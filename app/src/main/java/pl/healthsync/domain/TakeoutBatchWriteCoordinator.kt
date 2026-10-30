package pl.healthsync.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A batch gateway must check each candidate against all relevant Health Connect sources. */
internal interface TakeoutBatchWriteGateway {
    suspend fun inspectBatch(candidates: List<TakeoutWriteCandidate>): Map<String, TakeoutWriteCheck>
    suspend fun insertBatch(candidates: List<TakeoutWriteCandidate>)
}

private val takeoutBatchLock = Mutex()

/** A partial or ambiguous batch result is recovered through reinspection, never blind replay. */
internal class TakeoutBatchWriteCoordinator(private val journal: TakeoutWriteJournal, private val gateway: TakeoutBatchWriteGateway) {
    suspend fun run(sessionId: String, candidates: List<TakeoutWriteCandidate>, batchSize: Int = 100, progress: (Int, Int) -> Unit = { _, _ -> }) = takeoutBatchLock.withLock {
        require(sessionId.isNotBlank() && batchSize in 1..1000 && candidates.map { it.clientId }.distinct().size == candidates.size)
        var done = 0
        for (batch in candidates.chunked(batchSize)) {
            batch.forEach { candidate ->
                require(candidate.clientId.isNotBlank() && candidate.kind.isNotBlank() && candidate.contentHash.isNotBlank())
            }
            journal.prepareBatch(sessionId, batch)
            val previous = journal.confirmedBatch(sessionId, batch)
            check(previous.keys == batch.map { it.clientId }.toSet()) { "Niepełna odpowiedź rejestru importu" }
            val before = inspectComplete(batch)
            val missing = mutableListOf<TakeoutWriteCandidate>()
            val recovered = mutableListOf<Pair<TakeoutWriteCandidate, TakeoutWriteReceipt>>()
            var problem: String? = null
            for (candidate in batch) {
                val stored = previous[candidate.clientId]
                when (val status = before.getValue(candidate.clientId)) {
                    is TakeoutWriteCheck.Blocked -> problem = problem ?: status.reason
                    TakeoutWriteCheck.Missing -> if (stored == null) missing.add(candidate) else problem = problem ?: "Potwierdzony rekord nie jest już dostępny"
                    is TakeoutWriteCheck.AlreadyWritten -> {
                        if (stored != null && stored != status.receipt) problem = problem ?: "Potwierdzony rekord uległ zmianie"
                        else if (stored == null) recovered.add(candidate to status.receipt)
                    }
                }
            }
            journal.confirmBatch(sessionId, recovered)
            check(problem == null) { problem!! }
            if (missing.isNotEmpty()) {
                gateway.insertBatch(missing)
                val after = inspectComplete(missing)
                var unconfirmed: String? = null
                val confirmed = mutableListOf<Pair<TakeoutWriteCandidate, TakeoutWriteReceipt>>()
                for (candidate in missing) when (val status = after.getValue(candidate.clientId)) {
                    is TakeoutWriteCheck.AlreadyWritten -> confirmed.add(candidate to status.receipt)
                    is TakeoutWriteCheck.Blocked -> unconfirmed = unconfirmed ?: status.reason
                    TakeoutWriteCheck.Missing -> unconfirmed = unconfirmed ?: "Nie można potwierdzić zapisu w Health Connect"
                }
                journal.confirmBatch(sessionId, confirmed)
                check(unconfirmed == null) { unconfirmed!! }
            }
            done += batch.size
            progress(done, candidates.size)
        }
    }

    private suspend fun inspectComplete(batch: List<TakeoutWriteCandidate>): Map<String, TakeoutWriteCheck> {
        val checks = gateway.inspectBatch(batch)
        check(checks.keys == batch.map { it.clientId }.toSet()) { "Niepełna odpowiedź sprawdzania rekordów" }
        return checks
    }
}
