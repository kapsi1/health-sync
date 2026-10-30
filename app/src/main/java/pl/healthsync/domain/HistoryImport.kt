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

interface ManualHistoryStore : HistoryStore {
    /** Atomically starts a fresh refresh or resumes the matching unfinished session. */
    suspend fun prepareManual(metricIds: List<String>, start: LocalDate, endExclusive: LocalDate): LocalDate
    suspend fun finishManual(metricIds: List<String>, start: LocalDate, endExclusive: LocalDate)
}

interface UndoHistoryStore : HistoryStore {
    /** Replace or remove the local value only after a successful complete read of that day. */
    suspend fun reconcileAfterUndo(metricId: String, date: LocalDate, value: ImportedValue?)
}

private val historyImportLock = Mutex()

class HistoryImport(private val source: HistorySource, private val store: HistoryStore, private val today: () -> LocalDate = { LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")) }) {

    suspend fun reconcileAfterUndo(affected: Map<String, Set<LocalDate>>) = historyImportLock.withLock {
        val undoStore = store as? UndoHistoryStore ?: error("Brak obsługi przeliczania po wycofaniu")
        for ((metric, dates) in affected) for (date in dates.sorted()) {
            val values = source.read(metric, DateChunk(date, date.plusDays(1)))
            check(values.size <= 1 && values.all { it.date == date }) { "Nieprawidłowy wynik odczytu po wycofaniu" }
            undoStore.reconcileAfterUndo(metric, date, values.singleOrNull())
        }
    }

    suspend fun run(metricIds: List<String>, start: LocalDate, endExclusive: LocalDate, refresh: Boolean, progress: (ImportProgress) -> Unit) = historyImportLock.withLock {
        require(metricIds.isNotEmpty() && metricIds.distinct().size == metricIds.size)
        val ranges = chunks(start, endExclusive)
        if (refresh) store.invalidateAll(metricIds, ranges)
        readChunks(metricIds, ranges, refresh, 0, metricIds.size * ranges.size, progress)
    }

    suspend fun runManual(metricIds: List<String>, start: LocalDate, endExclusive: LocalDate, progress: (ImportProgress) -> Unit) = historyImportLock.withLock {
        require(metricIds.isNotEmpty() && metricIds.distinct().size == metricIds.size)
        require(start < endExclusive)
        val manual = store as? ManualHistoryStore ?: error("Brak obsługi wznawiania importu")
        val ordered = metricIds.sorted()
        val sessionEnd = manual.prepareManual(ordered, start, endExclusive)
        require(sessionEnd > start && sessionEnd <= endExclusive)
        val original = chunks(start, sessionEnd)
        val additional = if (sessionEnd < endExclusive) chunks(sessionEnd, endExclusive) else emptyList()
        val total = ordered.size * (original.size + additional.size)
        val done = readChunks(ordered, original, false, 0, total, progress, forceLast = sessionEnd < endExclusive)
        if (additional.isNotEmpty()) readChunks(ordered, additional, false, done, total, progress)
        manual.finishManual(ordered, start, sessionEnd)
    }

    private suspend fun readChunks(metricIds: List<String>, ranges: List<DateChunk>, refresh: Boolean, initial: Int, total: Int, progress: (ImportProgress) -> Unit, forceLast: Boolean = false): Int {
        var processed = initial
        for (metric in metricIds) for (range in ranges) {
            val skip = !refresh && !(forceLast && range == ranges.last()) && range.endExclusive <= today() && store.isCompleted(metric, range)
            if (!skip) {
                val rows = source.read(metric, range)
                store.commit(metric, range, rows)
            }
            progress(ImportProgress(++processed, total, metric, range, skip))
        }
        return processed
    }
}
