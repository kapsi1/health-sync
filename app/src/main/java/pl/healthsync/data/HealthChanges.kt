package pl.healthsync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.healthsync.domain.ChangesOutcome
import pl.healthsync.domain.ChangesPage
import pl.healthsync.domain.ChangesScan
import pl.healthsync.domain.ChangesSource
import pl.healthsync.domain.ChangesState
import pl.healthsync.domain.DateChunk
import java.time.LocalDate
import java.time.ZoneId

private val Context.changesPreferences by preferencesDataStore(name = "health_changes")
private val changesLock = Mutex()
private val warsaw = ZoneId.of("Europe/Warsaw")

private class PersistentChangesState(private val context: Context) : ChangesState {
    private fun tokenKey(metric: String) = stringPreferencesKey("token_$metric")
    private fun deletionKey(metric: String) = booleanPreferencesKey("deletion_$metric")
    override suspend fun token(metric: String): String? = context.changesPreferences.data.first()[tokenKey(metric)]
    override suspend fun save(metric: String, token: String, deletionSeen: Boolean) {
        context.changesPreferences.edit {
            it[tokenKey(metric)] = token
            if (deletionSeen) it[deletionKey(metric)] = true
        }
    }
    suspend fun deletionSeen(metric: String) = context.changesPreferences.data.first()[deletionKey(metric)] == true
}

private class HealthChangesSource(private val client: HealthConnectClient, private val dao: ImportDao) : ChangesSource {
    override suspend fun initialToken(metric: String): String = client.getChangesToken(ChangesTokenRequest(
        recordTypes = when (metric) {
            "weight_avg" -> setOf(WeightRecord::class)
            "body_fat" -> setOf(BodyFatRecord::class)
            else -> error("Nieobsługiwany typ zmian")
        }
    ))
    override suspend fun page(metric: String, token: String): ChangesPage {
        val result = client.getChanges(token)
        val dates = result.changes.mapNotNull { change ->
            if (change !is UpsertionChange) null else when (val record = change.record) {
                is WeightRecord -> if (metric == "weight_avg") record.time.atZone(warsaw).toLocalDate() else null
                is BodyFatRecord -> if (metric == "body_fat") record.time.atZone(warsaw).toLocalDate() else null
                else -> null
            }
        }.toSet()
        return ChangesPage(dates, result.changes.any { it is DeletionChange }, result.nextChangesToken, result.hasMore, result.changesTokenExpired)
    }
    override suspend fun refreshDay(metric: String, date: LocalDate) {
        val range = DateChunk(date, date.plusDays(1))
        val rows = HealthHistorySource(client).read(metric, range)
        RoomHistoryStore(dao).commit(metric, range, rows)
    }
}

class HealthChanges(private val context: Context, private val client: HealthConnectClient, private val dao: ImportDao) {
    suspend fun scan(selected: Set<String>): String? = changesLock.withLock {
        val state = PersistentChangesState(context)
        val scan = ChangesScan(HealthChangesSource(client, dao), state)
        val alerts = mutableListOf<String>()
        for (metric in listOf("weight_avg", "body_fat").filter { it in selected }) {
            val label = if (metric == "weight_avg") "masy ciała" else "tkanki tłuszczowej"
            if (scan.run(metric) == ChangesOutcome.EXPIRED) alerts += "Token zmian $label wygasł; potrzebna pełna rekonsyliacja."
            if (state.deletionSeen(metric)) alerts += "Wykryto usunięcie pomiaru $label; nie usunięto danych z arkusza."
        }
        alerts.joinToString(" ").ifEmpty { null }
    }
}
