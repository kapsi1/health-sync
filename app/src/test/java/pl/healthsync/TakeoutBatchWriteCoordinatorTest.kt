package pl.healthsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*

class TakeoutBatchWriteCoordinatorTest {
    private val items = (1..4).map { TakeoutWriteCandidate("id-$it", "weight", "hash-$it") }
    private class Journal : TakeoutWriteJournal {
        val planned = mutableMapOf<String, TakeoutWriteCandidate>()
        val confirmed = mutableMapOf<String, TakeoutWriteReceipt>()
        override suspend fun prepare(sessionId: String, candidate: TakeoutWriteCandidate) {
            val previous = planned.putIfAbsent(candidate.clientId, candidate)
            check(previous == null || previous == candidate)
        }
        override suspend fun confirmed(sessionId: String, candidate: TakeoutWriteCandidate) = confirmed[candidate.clientId]
        override suspend fun confirm(sessionId: String, candidate: TakeoutWriteCandidate, receipt: TakeoutWriteReceipt) { confirmed[candidate.clientId] = receipt }
    }
    private class Gateway : TakeoutBatchWriteGateway {
        val written = mutableSetOf<String>()
        var calls = 0
        var partialFailure = false
        var block: String? = null
        override suspend fun inspectBatch(candidates: List<TakeoutWriteCandidate>) = candidates.associate { c ->
            c.clientId to when {
                c.clientId == block -> TakeoutWriteCheck.Blocked("conflict")
                c.clientId in written -> TakeoutWriteCheck.AlreadyWritten(TakeoutWriteReceipt("hc-${c.clientId}", "2026-10-30T00:00:00Z"))
                else -> TakeoutWriteCheck.Missing
            }
        }
        override suspend fun insertBatch(candidates: List<TakeoutWriteCandidate>) {
            calls++
            if (partialFailure) {
                written.add(candidates.first().clientId); partialFailure = false; error("response lost")
            }
            check(candidates.none { it.clientId in written })
            written.addAll(candidates.map { it.clientId })
        }
    }

    @Test fun `resume checks individual records after partial batch failure`() = runBlocking {
        val journal = Journal(); val gateway = Gateway().apply { partialFailure = true }
        val coordinator = TakeoutBatchWriteCoordinator(journal, gateway)
        try { coordinator.run("run", items, 4); fail("expected error") } catch (_: IllegalStateException) { }
        assertEquals(1, gateway.written.size)
        assertTrue(journal.confirmed.isEmpty())
        coordinator.run("run", items, 4)
        assertEquals(2, gateway.calls)
        assertEquals(items.map { it.clientId }.toSet(), gateway.written)
        assertEquals(4, journal.confirmed.size)
    }

    @Test fun `conflict prevents writing other records in the same batch`() = runBlocking {
        val journal = Journal(); val gateway = Gateway().apply { block = "id-2" }
        try { TakeoutBatchWriteCoordinator(journal, gateway).run("run", items, 4); fail("expected error") } catch (_: IllegalStateException) { }
        assertEquals(0, gateway.calls)
        assertTrue(gateway.written.isEmpty())
    }

    @Test fun `confirmed item deleted later prevents new batch writes`() = runBlocking {
        val journal = Journal(); val gateway = Gateway()
        val coordinator = TakeoutBatchWriteCoordinator(journal, gateway)
        coordinator.run("run", items.take(2), 2)
        gateway.written.remove("id-1")
        try { coordinator.run("run", items, 4); fail("expected error") } catch (_: IllegalStateException) { }
        assertEquals(1, gateway.calls)
        assertFalse("id-3" in gateway.written)
    }
}
