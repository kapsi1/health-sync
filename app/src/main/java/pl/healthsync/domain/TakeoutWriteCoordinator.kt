package pl.healthsync.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Opaque record identity and a hash of the exact planned content, not the health values themselves. */
internal data class TakeoutWriteCandidate(val clientId: String, val kind: String, val contentHash: String)
internal data class TakeoutWriteReceipt(val recordId: String, val lastModified: String)
internal sealed interface TakeoutWriteCheck {
    data object Missing : TakeoutWriteCheck
    data class AlreadyWritten(val receipt: TakeoutWriteReceipt) : TakeoutWriteCheck
    data class Blocked(val reason: String) : TakeoutWriteCheck
}

internal interface TakeoutWriteJournal {
    /** Stores intent before any write. Replays may not replace earlier intent. */
    suspend fun prepare(sessionId: String, candidate: TakeoutWriteCandidate)
    suspend fun confirmed(sessionId: String, candidate: TakeoutWriteCandidate): TakeoutWriteReceipt?
    suspend fun confirm(sessionId: String, candidate: TakeoutWriteCandidate, receipt: TakeoutWriteReceipt)
    suspend fun prepareBatch(sessionId: String, candidates: List<TakeoutWriteCandidate>) { candidates.forEach { prepare(sessionId, it) } }
    suspend fun confirmedBatch(sessionId: String, candidates: List<TakeoutWriteCandidate>): Map<String, TakeoutWriteReceipt?> =
        candidates.associate { it.clientId to confirmed(sessionId, it) }
    suspend fun confirmBatch(sessionId: String, entries: List<Pair<TakeoutWriteCandidate, TakeoutWriteReceipt>>) {
        entries.forEach { (candidate, receipt) -> confirm(sessionId, candidate, receipt) }
    }
}

internal interface TakeoutWriteGateway {
    /** Must verify permissions, source, existing records, identity and full content. */
    suspend fun inspect(candidate: TakeoutWriteCandidate): TakeoutWriteCheck
    /** Must not update an existing record; an ambiguous outcome must be recoverable by inspect. */
    suspend fun insert(candidate: TakeoutWriteCandidate)
}

private val takeoutWriteLock = Mutex()

/** A failed or cancelled write leaves the journal pending and is rechecked before any retry. */
internal class TakeoutWriteCoordinator(private val journal: TakeoutWriteJournal, private val gateway: TakeoutWriteGateway) {
    suspend fun run(sessionId: String, candidates: List<TakeoutWriteCandidate>, progress: (Int, Int) -> Unit = { _, _ -> }) = takeoutWriteLock.withLock {
        require(sessionId.isNotBlank() && candidates.map { it.clientId }.distinct().size == candidates.size)
        candidates.forEachIndexed { index, candidate ->
            require(candidate.clientId.isNotBlank() && candidate.kind.isNotBlank() && candidate.contentHash.isNotBlank())
            journal.prepare(sessionId, candidate)
            val confirmed = journal.confirmed(sessionId, candidate)
            if (confirmed != null) {
                val current = gateway.inspect(candidate)
                check(current is TakeoutWriteCheck.AlreadyWritten && current.receipt == confirmed) {
                    "Potwierdzony rekord zmienił się lub nie jest już dostępny"
                }
            } else {
                val check = gateway.inspect(candidate)
                val receipt = when (check) {
                    is TakeoutWriteCheck.AlreadyWritten -> check.receipt
                    is TakeoutWriteCheck.Blocked -> error(check.reason)
                    TakeoutWriteCheck.Missing -> {
                        gateway.insert(candidate)
                        when (val after = gateway.inspect(candidate)) {
                            is TakeoutWriteCheck.AlreadyWritten -> after.receipt
                            is TakeoutWriteCheck.Blocked -> error(after.reason)
                            TakeoutWriteCheck.Missing -> error("Nie można potwierdzić zapisu w Health Connect")
                        }
                    }
                }
                journal.confirm(sessionId, candidate, receipt)
            }
            progress(index + 1, candidates.size)
        }
    }
}
