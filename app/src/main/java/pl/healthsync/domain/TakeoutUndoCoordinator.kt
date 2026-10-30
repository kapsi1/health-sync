package pl.healthsync.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TakeoutUndoEntry(val candidate: TakeoutWriteCandidate, val receipt: TakeoutWriteReceipt)
internal sealed interface TakeoutUndoCheck {
    data object Unchanged : TakeoutUndoCheck
    data object Missing : TakeoutUndoCheck
    data class Blocked(val reason: String) : TakeoutUndoCheck
}
internal interface TakeoutUndoJournal {
    suspend fun begin(sessionId: String)
    suspend fun pending(sessionId: String): List<TakeoutUndoEntry>
    suspend fun markReverted(sessionId: String, entry: TakeoutUndoEntry)
    suspend fun finish(sessionId: String)
}
internal interface TakeoutUndoGateway {
    /** Verify actual origin, exact identifier, version, full content and last-modified timestamp. */
    suspend fun inspect(entry: TakeoutUndoEntry): TakeoutUndoCheck
    /** Delete only the exact record after checking it again. */
    suspend fun delete(entry: TakeoutUndoEntry)
}
private val takeoutUndoLock = Mutex()

/** A deletion with an uncertain outcome is checked again before the checkpoint advances. */
internal class TakeoutUndoCoordinator(private val journal: TakeoutUndoJournal, private val gateway: TakeoutUndoGateway) {
    suspend fun run(sessionId: String) = takeoutUndoLock.withLock {
        journal.begin(sessionId)
        for (entry in journal.pending(sessionId)) {
            when (val before = gateway.inspect(entry)) {
                is TakeoutUndoCheck.Blocked -> error(before.reason)
                TakeoutUndoCheck.Missing -> Unit
                TakeoutUndoCheck.Unchanged -> {
                    gateway.delete(entry)
                    when (val after = gateway.inspect(entry)) {
                        is TakeoutUndoCheck.Blocked -> error(after.reason)
                        TakeoutUndoCheck.Unchanged -> error("Nie można potwierdzić usunięcia rekordu")
                        TakeoutUndoCheck.Missing -> Unit
                    }
                }
            }
            journal.markReverted(sessionId, entry)
        }
        // The caller must refresh affected days before marking the session fully reverted.
    }
}
