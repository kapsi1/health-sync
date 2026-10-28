package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SleepDailyTest {
    private fun interval(start: String, end: String) = SleepInterval(Instant.parse(start), Instant.parse(end))

    @Test fun `midnight sessions and DST are assigned to end date and combined`() {
        val sessions = listOf(
            interval("2026-03-28T22:00:00Z", "2026-03-29T05:00:00Z"),
            interval("2026-03-29T12:00:00Z", "2026-03-29T12:30:00Z"),
        )
        assertEquals(450L, sleepByEndDate(sessions)[LocalDate.parse("2026-03-29")]!!.duration!!.toMinutes())
    }

    @Test fun `overlapping sessions are not silently double counted`() {
        val sessions = listOf(
            interval("2026-10-25T00:00:00Z", "2026-10-25T05:00:00Z"),
            interval("2026-10-25T03:00:00Z", "2026-10-25T06:00:00Z"),
        )
        val day = sleepByEndDate(sessions)[LocalDate.parse("2026-10-25")]!!
        assertTrue(day.overlapping)
        assertEquals(null, day.duration)
    }
}
