package pl.healthsync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLogTest {
    @Test fun newestEntriesAreBoundedAndUseWarsawTime() {
        var history = ""
        repeat(51) { history = appendSyncLog(history, "Próba $it", Instant.parse("2026-10-28T12:00:00Z")) }
        val entries = history.lines()
        assertEquals(50, entries.size)
        assertEquals("28.10.2026 13:00:00 — Próba 50", entries.first())
        assertTrue(entries.last().endsWith("Próba 1"))
    }
}
