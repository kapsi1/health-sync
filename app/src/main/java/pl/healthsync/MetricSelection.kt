package pl.healthsync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.util.UUID
import pl.healthsync.domain.updateBackfillRequests

private val Context.settings by preferencesDataStore(name = "settings")

class MetricSelection(private val context: Context) {
    private val key = stringSetPreferencesKey("selected_metrics")
    private val pendingKey = stringSetPreferencesKey("history_backfills")
    private fun current(ids: Set<String>): Set<String> = ids.map { id -> when (id) {
        "respiratory_rate" -> "respiratory_rate_sleep"
        "body_temperature" -> "skin_temperature_delta"
        else -> id
    } }.filter { id -> MetricRegistry.all.any { it.id == id } }.toSet()
    val selected = context.settings.data.map { current(it[key] ?: emptySet()) }
    val pending = context.settings.data.map { preferences ->
        (preferences[pendingKey] ?: emptySet()).mapNotNull { token ->
            token.substringBefore('|').takeIf { '|' in token }?.let { it to token }
        }.toMap()
    }
    private fun update(preferences: androidx.datastore.preferences.core.MutablePreferences, selected: Set<String>) {
        val previous = preferences[key] ?: emptySet()
        preferences[key] = selected
        preferences[pendingKey] = updateBackfillRequests(previous, selected, preferences[pendingKey] ?: emptySet()) { UUID.randomUUID().toString() }
    }
    suspend fun set(selected: Set<String>) { context.settings.edit { update(it, selected) } }
    suspend fun migrate() { context.settings.edit { preferences ->
        val previous = preferences[key] ?: emptySet()
        val updated = current(previous)
        if (previous != updated) update(preferences, updated)
    } }
    suspend fun toggle(id: String, enabled: Boolean) {
        context.settings.edit { preferences ->
            val current = preferences[key] ?: emptySet()
            update(preferences, if (enabled) current + id else current - id)
        }
    }
    suspend fun complete(token: String) {
        context.settings.edit { preferences -> preferences[pendingKey] = (preferences[pendingKey] ?: emptySet()) - token }
    }
}
