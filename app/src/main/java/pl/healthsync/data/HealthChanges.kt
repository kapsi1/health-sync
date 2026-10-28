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
import pl.healthsync.domain.ChangesRecovery
import pl.healthsync.domain.RecoveryCheckpoint
import pl.healthsync.domain.RecoverySource
import pl.healthsync.domain.RecoveryStore
import java.time.LocalDate
import java.time.ZoneId

private val Context.changesPreferences by preferencesDataStore(name = "health_changes")
private val changesLock = Mutex()
private val warsaw = ZoneId.of("Europe/Warsaw")

private class PersistentChangesState(private val context: Context) : ChangesState, RecoveryStore {
    private fun tokenKey(metric: String) = stringPreferencesKey("token_$metric")
    private fun deletionKey(metric: String) = booleanPreferencesKey("deletion_$metric")
    private fun recoveryTokenKey(metric: String) = stringPreferencesKey("recovery_token_$metric")
    private fun recoveryDateKey(metric: String) = stringPreferencesKey("recovery_date_$metric")
    private fun recoveryEndKey(metric: String) = stringPreferencesKey("recovery_end_$metric")
    override suspend fun token(metric: String): String? = context.changesPreferences.data.first()[tokenKey(metric)]
    override suspend fun save(metric: String, token: String, deletionSeen: Boolean) {
        context.changesPreferences.edit {
            it[tokenKey(metric)] = token
            if (deletionSeen) it[deletionKey(metric)] = true
        }
    }
    suspend fun deletionSeen(metric: String) = context.changesPreferences.data.first()[deletionKey(metric)] == true
    override suspend fun checkpoint(metric: String): RecoveryCheckpoint? {
        val preferences = context.changesPreferences.data.first()
        val token = preferences[recoveryTokenKey(metric)] ?: return null
        return RecoveryCheckpoint(token, LocalDate.parse(requireNotNull(preferences[recoveryDateKey(metric)])), LocalDate.parse(requireNotNull(preferences[recoveryEndKey(metric)])))
    }
    override suspend fun begin(metric: String, checkpoint: RecoveryCheckpoint) {
        context.changesPreferences.edit {
            it[recoveryTokenKey(metric)] = checkpoint.token
            it[recoveryDateKey(metric)] = checkpoint.nextDate.toString()
            it[recoveryEndKey(metric)] = checkpoint.endExclusive.toString()
        }
    }
    override suspend fun advance(metric: String, nextDate: LocalDate) {
        context.changesPreferences.edit { it[recoveryDateKey(metric)] = nextDate.toString() }
    }
    override suspend fun finish(metric: String) {
        context.changesPreferences.edit {
            it[tokenKey(metric)] = requireNotNull(it[recoveryTokenKey(metric)])
            it.remove(recoveryTokenKey(metric)); it.remove(recoveryDateKey(metric)); it.remove(recoveryEndKey(metric))
        }
    }
}

private class HealthChangesSource(private val client: HealthConnectClient, private val dao: ImportDao) : ChangesSource, RecoverySource {
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
        refresh(metric, DateChunk(date, date.plusDays(1)))
    }
    override suspend fun refresh(metric: String, range: DateChunk) {
        val rows = HealthHistorySource(client).read(metric, range)
        RoomHistoryStore(dao).commit(metric, range, rows)
    }
}

class HealthChanges(private val context: Context, private val client: HealthConnectClient, private val dao: ImportDao) {
    suspend fun scan(selected: Set<String>): String? = changesLock.withLock {
        val state = PersistentChangesState(context)
        val source = HealthChangesSource(client, dao)
        val scan = ChangesScan(source, state)
        val recovery = ChangesRecovery(source, state)
        val alerts = mutableListOf<String>()
        for (metric in listOf("weight_avg", "body_fat").filter { it in selected }) {
            val label = if (metric == "weight_avg") "masy ciała" else "tkanki tłuszczowej"
            val expired = if (state.checkpoint(metric) == null) scan.run(metric) == ChangesOutcome.EXPIRED else true
            if (expired) {
                val today = LocalDate.now(warsaw)
                val start = dao.earliestImportedStart()?.let(LocalDate::parse) ?: today.minusDays(2)
                val finished = recovery.run(metric, start, today.plusDays(1))
                if (!finished) alerts += "Odtwarzanie historii $label trwa; kolejną część przetworzy następna synchronizacja."
                else {
                    alerts += "Odtworzono historię $label od $start; wcześniejsze dane wymagają osobnego importu."
                    if (scan.run(metric) == ChangesOutcome.EXPIRED) alerts += "Nowy token zmian $label wygasł; odtwarzanie zostanie ponowione."
                }
            }
            if (state.deletionSeen(metric)) alerts += "Wykryto usunięcie pomiaru $label; nie usunięto danych z arkusza."
        }
        alerts.joinToString(" ").ifEmpty { null }
    }
}
