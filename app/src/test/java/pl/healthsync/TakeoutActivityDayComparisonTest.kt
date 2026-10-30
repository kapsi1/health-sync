package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TakeoutActivityDayComparisonTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val day = LocalDate.parse("2026-06-12")
    private val steps = TakeoutActivityKind.STEPS
    private fun interval(kind: TakeoutActivityKind, start: String, end: String) =
        ExistingActivityInterval(kind, Instant.parse(start), Instant.parse(end))

    @Test fun `partial interval blocks only its own metric`() {
        val records = listOf(interval(steps, "2026-06-12T09:00:00Z", "2026-06-12T09:01:00Z"))
        assertTrue(activityDayHasConnectData(day, steps, zone, records))
        assertFalse(activityDayHasConnectData(day, TakeoutActivityKind.DISTANCE, zone, records))
    }

    @Test fun `an interval crossing midnight blocks both days but adjacent intervals do not`() {
        val crossing = interval(steps, "2026-06-11T21:59:00Z", "2026-06-11T22:01:00Z")
        assertTrue(activityDayHasConnectData(day.minusDays(1), steps, zone, listOf(crossing)))
        assertTrue(activityDayHasConnectData(day, steps, zone, listOf(crossing)))
        val before = interval(steps, "2026-06-11T21:00:00Z", "2026-06-11T22:00:00Z")
        val after = interval(steps, "2026-06-12T22:00:00Z", "2026-06-12T23:00:00Z")
        assertFalse(activityDayHasConnectData(day, steps, zone, listOf(before, after)))
    }

    @Test fun `day boundaries follow daylight saving time`() {
        val springDay = LocalDate.parse("2026-03-29")
        val autumnDay = LocalDate.parse("2026-10-25")
        val springLast = interval(steps, "2026-03-29T21:59:00Z", "2026-03-29T22:00:00Z")
        val autumnLast = interval(steps, "2026-10-25T22:59:00Z", "2026-10-25T23:00:00Z")
        assertTrue(activityDayHasConnectData(springDay, steps, zone, listOf(springLast)))
        assertTrue(activityDayHasConnectData(autumnDay, steps, zone, listOf(autumnLast)))
        assertFalse(activityDayHasConnectData(springDay.plusDays(1), steps, zone, listOf(springLast)))
        assertFalse(activityDayHasConnectData(autumnDay.plusDays(1), steps, zone, listOf(autumnLast)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty intervals are rejected`() {
        interval(steps, "2026-06-12T09:00:00Z", "2026-06-12T09:00:00Z")
    }
}
