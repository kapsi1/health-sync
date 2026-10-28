package pl.healthsync.data

import pl.healthsync.MetricRegistry
import pl.healthsync.domain.PendingBatch
import pl.healthsync.domain.PendingQueue
import pl.healthsync.domain.PendingSheetDay
import java.time.LocalDate
import java.security.MessageDigest

class RoomPendingQueue(private val dao: ImportDao, private val active: Set<String>) : PendingQueue {
    override suspend fun snapshot(limit: Int): PendingBatch {
        if (active.isEmpty()) return PendingBatch(emptyList())
        val mapping = MetricRegistry.all.filter { it.id in active }.associate { it.id to it.column }
        val dates = dao.queuedDates(mapping.keys.toList(), limit)
        val rows = dates.map { date ->
            val observations = dao.observationsForDate(date)
            val cells = observations.filter { it.metricId in mapping }.associate { mapping.getValue(it.metricId) to it.value }
            PendingSheetDay(LocalDate.parse(date), cells, revision(observations))
        }
        return PendingBatch(rows)
    }

    override suspend fun acknowledge(batch: PendingBatch) {
        for (row in batch.rows) {
            val current = dao.observationsForDate(row.date.toString())
            if (revision(current) == row.revision) dao.acknowledgeIfUnchanged(row.date.toString(), current)
        }
    }
}

private fun revision(rows: List<DailyObservation>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    rows.sortedBy { it.metricId }.forEach { row ->
        listOf(row.date, row.metricId, row.value.toString(), row.unit, row.observedAtUtc).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
