package pl.healthsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*

class TakeoutWriteCoordinatorTest {
    private val candidate = TakeoutWriteCandidate("source-id", "sleep", "content-hash")
    private val receipt = TakeoutWriteReceipt("health-id", "2026-10-30T00:00:00Z")
    private class Journal : TakeoutWriteJournal {
        val planned = mutableMapOf<String, TakeoutWriteCandidate>()
        val saved = mutableMapOf<String, TakeoutWriteReceipt>()
        override suspend fun prepare(sessionId: String, candidate: TakeoutWriteCandidate) {
            val before = planned.putIfAbsent(candidate.clientId, candidate)
            check(before == null || before == candidate)
        }
        override suspend fun confirmed(sessionId: String, candidate: TakeoutWriteCandidate) = saved[candidate.clientId]
        override suspend fun confirm(sessionId: String, candidate: TakeoutWriteCandidate, receipt: TakeoutWriteReceipt) { saved[candidate.clientId] = receipt }
    }
    private inner class Gateway : TakeoutWriteGateway {
        var written = false
        var writes = 0
        var failAfterWrite = false
        var blocked = false
        override suspend fun inspect(candidate: TakeoutWriteCandidate): TakeoutWriteCheck = when {
            blocked -> TakeoutWriteCheck.Blocked("conflict")
            written -> TakeoutWriteCheck.AlreadyWritten(receipt)
            else -> TakeoutWriteCheck.Missing
        }
        override suspend fun insert(candidate: TakeoutWriteCandidate) {
            writes++; written = true
            if (failAfterWrite) { failAfterWrite = false; error("connection lost after write") }
        }
    }

    @Test fun `retry verifies a write completed before the response was lost`() = runBlocking {
        val journal = Journal(); val gateway = Gateway().apply { failAfterWrite = true }
        val coordinator = TakeoutWriteCoordinator(journal, gateway)
        try { coordinator.run("run", listOf(candidate)); fail("expected error") } catch (_: IllegalStateException) { }
        assertTrue(journal.saved.isEmpty())
        assertTrue(journal.planned.containsKey(candidate.clientId))
        coordinator.run("run", listOf(candidate))
        assertEquals(1, gateway.writes)
        assertEquals(receipt, journal.saved[candidate.clientId])
    }

    @Test fun `conflict blocks a write and does not confirm it`() = runBlocking {
        val journal = Journal(); val gateway = Gateway().apply { blocked = true }
        try { TakeoutWriteCoordinator(journal, gateway).run("run", listOf(candidate)); fail("expected error") } catch (_: IllegalStateException) { }
        assertEquals(0, gateway.writes)
        assertTrue(journal.saved.isEmpty())
    }

    @Test fun `changing pending content cannot reuse the same identity`() = runBlocking {
        val journal = Journal(); val gateway = Gateway().apply { blocked = true }
        try { TakeoutWriteCoordinator(journal, gateway).run("run", listOf(candidate)) } catch (_: IllegalStateException) { }
        try { TakeoutWriteCoordinator(journal, gateway).run("run", listOf(candidate.copy(contentHash = "different"))); fail("expected error") } catch (_: IllegalStateException) { }
        assertEquals(0, gateway.writes)
    }

    @Test fun `resume detects if previously confirmed record is removed`() = runBlocking {
        val journal = Journal(); val gateway = Gateway()
        val coordinator = TakeoutWriteCoordinator(journal, gateway)
        coordinator.run("run", listOf(candidate))
        gateway.written = false
        try { coordinator.run("run", listOf(candidate)); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals(1, gateway.writes)
    }
}
