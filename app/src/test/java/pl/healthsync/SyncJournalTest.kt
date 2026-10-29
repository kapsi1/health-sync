package pl.healthsync

import org.junit.Assert.*
import org.junit.Test

class SyncJournalTest {
    @Test fun retryKeepsEventsAndErrorHistory() {
        val started = appendEvent(null, "run", "Automatyczna", SyncEvent("1", "Start"), false, false)
        val failed = appendEvent(started, "run", "Automatyczna", SyncEvent("2", "Błąd"), true, true)
        val retry = appendEvent(failed, "run", "Automatyczna", SyncEvent("3", "Ponowienie"), false, false)
        val finished = appendEvent(retry, "run", "Automatyczna", SyncEvent("4", "Gotowe"), false, true)
        assertTrue(finished.error)
        assertTrue(finished.complete)
        assertEquals(false, finished.lastError)
        assertEquals(true, failed.lastError)
        assertNull(retry.lastError)
        assertEquals(listOf("Start", "Błąd", "Ponowienie", "Gotowe"), finished.events.map { it.message })
    }

    @Test fun retainsLatestThirtyEvents() {
        var run: SyncRun? = null
        repeat(35) { run = appendEvent(run, "run", "Ręczna", SyncEvent("$it", "$it"), false, false) }
        assertEquals(30, run!!.events.size)
        assertEquals("5", run!!.events.first().message)
    }
}
