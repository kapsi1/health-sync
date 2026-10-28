package pl.healthsync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore(name = "settings")

class MetricSelection(private val context: Context) {
    private val key = stringSetPreferencesKey("selected_metrics")
    val selected = context.settings.data.map { it[key] ?: emptySet() }
    suspend fun set(selected: Set<String>) { context.settings.edit { it[key] = selected } }
    suspend fun toggle(id: String, enabled: Boolean) {
        context.settings.edit { preferences ->
            val current = preferences[key] ?: emptySet()
            preferences[key] = if (enabled) current + id else current - id
        }
    }
}
