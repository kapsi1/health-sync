package pl.healthsync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.sheetStore by preferencesDataStore(name = "sheet_settings")
data class SheetState(val id: String?, val url: String?, val creationPending: Boolean, val name: String? = null)

class SheetSettings(private val context: Context) {
    private val idKey = stringPreferencesKey("spreadsheet_id")
    private val urlKey = stringPreferencesKey("spreadsheet_url")
    private val pendingKey = booleanPreferencesKey("creation_pending")
    private val nameKey = stringPreferencesKey("spreadsheet_name")
    val state = context.sheetStore.data.map { SheetState(it[idKey], it[urlKey], it[pendingKey] ?: false, it[nameKey]) }
    suspend fun markPending() { context.sheetStore.edit { it[pendingKey] = true } }
    suspend fun save(id: String, url: String) { context.sheetStore.edit {
        if (it[idKey] != id) it.remove(nameKey)
        it[idKey] = id; it[urlKey] = url; it[pendingKey] = false
    } }
    suspend fun saveNameIfCurrent(id: String, name: String) { context.sheetStore.edit {
        if (it[idKey] == id) it[nameKey] = name
    } }
    suspend fun clearPending() { context.sheetStore.edit { it[pendingKey] = false } }
}
