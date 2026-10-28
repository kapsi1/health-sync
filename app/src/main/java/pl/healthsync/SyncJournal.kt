package pl.healthsync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private val Context.journalStore by preferencesDataStore(name = "sync_journal")
private val journalKey = stringPreferencesKey("runs")
private val activeAutoKey = stringPreferencesKey("active_auto")

data class SyncEvent(val at: String, val message: String)
data class SyncRun(val id: String, val kind: String, val error: Boolean, val complete: Boolean, val events: List<SyncEvent>)

class SyncJournal(private val context: Context) {
    val runs = context.journalStore.data.map { decodeRuns(it[journalKey]) }

    suspend fun automaticRunId(workId: String, attempt: Int): String {
        var result = ""
        context.journalStore.edit { prefs ->
            val saved = prefs[activeAutoKey].orEmpty().split("|", limit = 2)
            result = if (attempt > 0 && saved.size == 2 && saved[0] == workId) saved[1] else "auto-${java.util.UUID.randomUUID()}"
            prefs[activeAutoKey] = "$workId|$result"
        }
        return result
    }

    suspend fun event(id: String, kind: String, message: String, error: Boolean = false, complete: Boolean = false) {
        context.journalStore.edit { prefs ->
            val runs = decodeRuns(prefs[journalKey]).toMutableList()
            val previous = runs.firstOrNull { it.id == id }
            runs.removeAll { it.id == id }
            runs.add(0, appendEvent(previous, id, kind, SyncEvent(Instant.now().toString(), message), error, complete))
            prefs[journalKey] = encodeRuns(runs.take(100))
        }
    }
}

internal fun appendEvent(previous: SyncRun?, id: String, kind: String, event: SyncEvent, error: Boolean, complete: Boolean): SyncRun {
    require(previous == null || previous.id == id)
    return SyncRun(id, kind, error || previous?.error == true, complete, (previous?.events.orEmpty() + event).takeLast(30))
}

internal fun encodeRuns(runs: List<SyncRun>): String = JSONArray().apply {
    runs.forEach { run -> put(JSONObject().apply {
        put("id", run.id); put("kind", run.kind); put("error", run.error); put("complete", run.complete)
        put("events", JSONArray().apply { run.events.forEach { event -> put(JSONObject().put("at", event.at).put("message", event.message)) } })
    }) }
}.toString()

internal fun decodeRuns(raw: String?): List<SyncRun> = try {
    val runs = JSONArray(raw ?: "[]")
    (0 until runs.length()).map { index ->
        val run = runs.getJSONObject(index)
        val events = run.getJSONArray("events")
        SyncRun(run.getString("id"), run.getString("kind"), run.getBoolean("error"), run.getBoolean("complete"),
            (0 until events.length()).map { i -> events.getJSONObject(i).let { SyncEvent(it.getString("at"), it.getString("message")) } })
    }
} catch (_: Exception) { emptyList() }
