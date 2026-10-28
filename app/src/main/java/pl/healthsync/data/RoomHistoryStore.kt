package pl.healthsync.data

import pl.healthsync.MetricRegistry
import pl.healthsync.domain.DateChunk
import pl.healthsync.domain.HistoryStore
import pl.healthsync.domain.ImportedValue
import java.time.Instant

class RoomHistoryStore(private val dao: ImportDao) : HistoryStore {
    override suspend fun isCompleted(metricId: String, range: DateChunk) =
        dao.isCompleted(metricId, range.start.toString(), range.endExclusive.toString())

    override suspend fun invalidateAll(metricIds: List<String>, ranges: List<DateChunk>) =
        dao.invalidateRanges(metricIds, ranges.map { it.start.toString() to it.endExclusive.toString() })

    override suspend fun commit(metricId: String, range: DateChunk, rows: List<ImportedValue>) {
        val metric = MetricRegistry.all.single { it.id == metricId }
        val now = Instant.now().toString()
        dao.commitChunk(
            ImportCheckpoint(metricId, range.start.toString(), range.endExclusive.toString(), now),
            rows.map { DailyObservation(it.date.toString(), metricId, it.value, metric.unit, now) },
        )
    }
}
