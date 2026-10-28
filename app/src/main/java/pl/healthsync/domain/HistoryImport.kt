package pl.healthsync.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

data class DateChunk(val start: LocalDate, val endExclusive: LocalDate)
data class ImportedValue(val date: LocalDate, val value: Double)
data class ImportProgress(val processed: Int, val total: Int, val metricId: String, val range: DateChunk, val skipped: Boolean)

fun chunks(start: LocalDate, endExclusive: LocalDate): List<DateChunk> {
    require(start < endExclusive)
    val result = mutableListOf<DateChunk>()
    var cursor = start
    while (cursor < endExclusive) {
        val end = minOf(YearMonth.from(cursor).plusMonths(1).atDay(1), endExclusive)
        result += DateChunk(cursor, end)
        cursor = end
    }
    return result
}

interface HistorySource {
    suspend fun read(metricId: String, range: DateChunk): List<ImportedValue>
}

interface HistoryStore {
    suspend fun isCompleted(metricId: String, range: DateChunk): Boolean
    suspend fun invalidateAll(metricIds: List<String>, ranges: List<DateChunk>)
    suspend fun commit(metricId: String, range: DateChunk, rows: List<ImportedValue>)
}

private val historyImportLock = Mutex()

class HistoryImport(private val source: HistorySource, private val store: HistoryStore, private val today: () -> LocalDate = { LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")) }) {

    suspend fun run(metricIds: List<String>, start: LocalDate, endExclusive: LocalDate, refresh: Boolean, progress: (ImportProgress) -> Unit) = historyImportLock.withLock {
        require(metricIds.isNotEmpty() && metricIds.distinct().size == metricIds.size)
        val ranges = chunks(start, endExclusive)
        val total = metricIds.size * ranges.size
        var processed = 0
        if (refresh) store.invalidateAll(metricIds, ranges)
        for (metric in metricIds) for (range in ranges) {
            val skip = !refresh && range.endExclusive <= today() && store.isCompleted(metric, range)
            if (!skip) {
                val rows = source.read(metric, range)
                store.commit(metric, range, rows)
            }
            progress(ImportProgress(++processed, total, metric, range, skip))
        }
    }
}
