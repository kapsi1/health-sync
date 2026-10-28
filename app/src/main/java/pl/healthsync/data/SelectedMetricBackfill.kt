package pl.healthsync.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import pl.healthsync.MetricRegistry
import pl.healthsync.MetricSelection
import pl.healthsync.syncLock
import pl.healthsync.domain.HistoryImport
import java.time.LocalDate
import java.time.ZoneId

/** Replays only the already chosen history interval; completed chunks remain resumable. */
class SelectedMetricBackfill(private val context: Context, private val client: HealthConnectClient, private val dao: ImportDao) {
    suspend fun run(selectedSnapshot: Set<String>, progress: (String) -> Unit = {}) {
        val selections = MetricSelection(context)
        val pending = selections.pending.first().filterKeys { it in selectedSnapshot }
        if (pending.isEmpty()) return
        val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        val start = dao.earliestImportedStart()?.let(LocalDate::parse) ?: today
        val importer = HistoryImport(HealthHistorySource(client), RoomHistoryStore(dao))
        for ((id, token) in pending) {
            val metric = MetricRegistry.all.firstOrNull { it.id == id } ?: continue
            progress("Uzupełnianie historii: ${metric.label} od $start.")
            importer.run(listOf(id), start, today.plusDays(1), false) { }
            syncLock.withLock { if (id in selections.selected.first()) dao.queueMetric(id) }
            selections.complete(token)
        }
    }
}
