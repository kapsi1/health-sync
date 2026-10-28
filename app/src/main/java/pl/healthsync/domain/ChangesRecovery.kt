package pl.healthsync.domain

import java.time.LocalDate
import java.time.YearMonth

data class RecoveryCheckpoint(val token: String, val nextDate: LocalDate, val endExclusive: LocalDate)

interface RecoveryStore {
    suspend fun checkpoint(metric: String): RecoveryCheckpoint?
    suspend fun begin(metric: String, checkpoint: RecoveryCheckpoint)
    suspend fun advance(metric: String, nextDate: LocalDate)
    suspend fun finish(metric: String)
}

interface RecoverySource {
    suspend fun initialToken(metric: String): String
    suspend fun refresh(metric: String, range: DateChunk)
}

class ChangesRecovery(private val source: RecoverySource, private val store: RecoveryStore) {
    suspend fun run(metric: String, start: LocalDate, endExclusive: LocalDate, maxChunks: Int = 12): Boolean {
        require(start < endExclusive && maxChunks > 0)
        var checkpoint = store.checkpoint(metric)
        if (checkpoint == null) {
            checkpoint = RecoveryCheckpoint(source.initialToken(metric), start, endExclusive)
            store.begin(metric, checkpoint)
        }
        require(checkpoint.nextDate <= checkpoint.endExclusive)
        var cursor = checkpoint.nextDate
        repeat(maxChunks) {
            if (cursor >= checkpoint.endExclusive) return@repeat
            val next = minOf(YearMonth.from(cursor).plusMonths(1).atDay(1), checkpoint.endExclusive)
            source.refresh(metric, DateChunk(cursor, next))
            store.advance(metric, next)
            cursor = next
        }
        if (cursor < checkpoint.endExclusive) return false
        store.finish(metric)
        return true
    }
}
