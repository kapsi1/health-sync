package pl.healthsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.LocalDate

class ChangesRecoveryTest {
    private class Store : RecoveryStore {
        var checkpoint: RecoveryCheckpoint? = null
        var acceptedToken: String? = "expired"
        override suspend fun checkpoint(metric: String) = checkpoint
        override suspend fun begin(metric: String, checkpoint: RecoveryCheckpoint) { this.checkpoint = checkpoint }
        override suspend fun advance(metric: String, nextDate: LocalDate) { checkpoint = checkpoint!!.copy(nextDate = nextDate) }
        override suspend fun finish(metric: String) { acceptedToken = checkpoint!!.token; checkpoint = null }
    }
    private class Source : RecoverySource {
        var fail = false
        var tokens = 0
        val refreshed = mutableListOf<DateChunk>()
        override suspend fun initialToken(metric: String): String { tokens++; return "new" }
        override suspend fun refresh(metric: String, range: DateChunk) { if (fail) error("failure"); refreshed.add(range) }
    }

    @Test fun resumeAndFailureNeverAdvanceBeyondSavedChunks() = runBlocking {
        val store = Store(); val source = Source()
        val start = LocalDate.parse("2026-01-15"); val end = LocalDate.parse("2026-04-01")
        val recovery = ChangesRecovery(source, store)
        assertFalse(recovery.run("weight_avg", start, end, 1))
        assertEquals(LocalDate.parse("2026-02-01"), store.checkpoint!!.nextDate)
        assertEquals("expired", store.acceptedToken)
        source.fail = true
        try { recovery.run("weight_avg", start, end, 1); fail("expected failure") } catch (_: IllegalStateException) { }
        assertEquals(LocalDate.parse("2026-02-01"), store.checkpoint!!.nextDate)
        source.fail = false
        assertTrue(recovery.run("weight_avg", start, end, 3))
        assertEquals("new", store.acceptedToken)
        assertEquals(1, source.tokens)
        assertEquals(3, source.refreshed.size)
        assertNull(store.checkpoint)
    }
}
