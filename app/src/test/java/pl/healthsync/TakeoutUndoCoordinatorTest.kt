package pl.healthsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*

class TakeoutUndoCoordinatorTest {
    private val entry = TakeoutUndoEntry(TakeoutWriteCandidate("client", "sleep", "hash"), TakeoutWriteReceipt("record", "2026-10-30T00:00:00Z"))
    private class Journal(private val entry: TakeoutUndoEntry) : TakeoutUndoJournal {
        var done = false
        override suspend fun begin(sessionId: String) = Unit
        override suspend fun pending(sessionId: String) = if (done) emptyList() else listOf(entry)
        override suspend fun markReverted(sessionId: String, entry: TakeoutUndoEntry) { done = true }
        override suspend fun finish(sessionId: String) = Unit
    }
    private class Gateway : TakeoutUndoGateway {
        var present = true
        var modified = false
        var failAfterDelete = false
        var deletions = 0
        override suspend fun inspect(entry: TakeoutUndoEntry): TakeoutUndoCheck = when {
            !present -> TakeoutUndoCheck.Missing
            modified -> TakeoutUndoCheck.Blocked("changed")
            else -> TakeoutUndoCheck.Unchanged
        }
        override suspend fun delete(entry: TakeoutUndoEntry) {
            deletions++; present = false
            if (failAfterDelete) { failAfterDelete = false; error("response lost") }
        }
    }

    @Test fun `modified record is never deleted`() = runBlocking {
        val journal = Journal(entry); val gateway = Gateway().apply { modified = true }
        try { TakeoutUndoCoordinator(journal, gateway).run("run"); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals(0, gateway.deletions)
        assertFalse(journal.done)
    }

    @Test fun `deletion can be resumed after losing response`() = runBlocking {
        val journal = Journal(entry); val gateway = Gateway().apply { failAfterDelete = true }
        val coordinator = TakeoutUndoCoordinator(journal, gateway)
        try { coordinator.run("run"); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertFalse(journal.done)
        coordinator.run("run")
        assertEquals(1, gateway.deletions)
        assertTrue(journal.done)
    }
}
