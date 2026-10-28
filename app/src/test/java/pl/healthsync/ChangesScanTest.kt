package pl.healthsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.LocalDate

class ChangesScanTest {
    private class State(var current: String? = "old") : ChangesState {
        var deleted = false
        override suspend fun token(metric: String) = current
        override suspend fun save(metric: String, token: String, deletionSeen: Boolean) { current = token; deleted = deleted || deletionSeen }
    }
    private class Source : ChangesSource {
        var fail = false
        val days = mutableListOf<LocalDate>()
        override suspend fun initialToken(metric: String) = "initial"
        override suspend fun page(metric: String, token: String) = ChangesPage(setOf(LocalDate.parse("2020-01-02")), true, "next", false, false)
        override suspend fun refreshDay(metric: String, date: LocalDate) { if (fail) error("read failed"); days.add(date) }
    }
    @Test fun failureDoesNotAdvanceTokenAndRetryProcessesOlderDate() = runBlocking {
        val state = State(); val source = Source().apply { fail = true }
        try { ChangesScan(source, state).run("weight_avg"); fail("expected failure") } catch (_: IllegalStateException) { }
        assertEquals("old", state.current)
        source.fail = false
        assertEquals(ChangesOutcome.CHECKED, ChangesScan(source, state).run("weight_avg"))
        assertEquals("next", state.current)
        assertEquals(listOf(LocalDate.parse("2020-01-02")), source.days)
        assertTrue(state.deleted)
    }
    @Test fun firstRunCreatesTokenWithoutReplayingExistingData() = runBlocking {
        val state = State(null); val source = Source()
        assertEquals(ChangesOutcome.INITIALIZED, ChangesScan(source, state).run("weight_avg"))
        assertEquals("initial", state.current)
        assertTrue(source.days.isEmpty())
    }
    @Test fun expiredTokenIsNotAdvanced() = runBlocking {
        val state = State()
        val source = object : ChangesSource {
            override suspend fun initialToken(metric: String) = "unused"
            override suspend fun page(metric: String, token: String) = ChangesPage(emptySet(), false, "next", false, true)
            override suspend fun refreshDay(metric: String, date: LocalDate) { fail("Unexpected refresh") }
        }
        assertEquals(ChangesOutcome.EXPIRED, ChangesScan(source, state).run("weight_avg"))
        assertEquals("old", state.current)
    }
}
