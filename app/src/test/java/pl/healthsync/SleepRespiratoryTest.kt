package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SleepRespiratoryTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun time(value: String) = Instant.parse(value)
    @Test fun `only asleep samples count and are assigned to session end date`() {
        val session = SleepBreathingSession(time("2026-06-12T06:00:00Z"), listOf(
            SleepBreathingInterval(time("2026-06-11T21:00:00Z"), time("2026-06-11T23:00:00Z")),
            SleepBreathingInterval(time("2026-06-11T23:30:00Z"), time("2026-06-12T06:00:00Z"))))
        val samples = listOf(BreathingSample(time("2026-06-11T22:30:00Z"), 12.0), BreathingSample(time("2026-06-11T23:15:00Z"), 90.0),
            BreathingSample(time("2026-06-12T05:00:00Z"), 16.0), BreathingSample(time("2026-06-12T06:00:00Z"), 80.0))
        assertEquals(14.0, breathingDuringSleep(listOf(session), samples, zone).getValue(LocalDate.parse("2026-06-12")), 0.0)
    }
    @Test fun `missing sleep stages do not classify daytime samples as sleep`() {
        assertTrue(breathingDuringSleep(listOf(SleepBreathingSession(time("2026-06-12T06:00:00Z"), emptyList())),
            listOf(BreathingSample(time("2026-06-12T05:00:00Z"), 12.0)), zone).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun `overlapping sleep stages are not double counted`() {
        val overlap = listOf(SleepBreathingInterval(time("2026-06-12T00:00:00Z"), time("2026-06-12T02:00:00Z")),
            SleepBreathingInterval(time("2026-06-12T01:00:00Z"), time("2026-06-12T03:00:00Z")))
        breathingDuringSleep(listOf(SleepBreathingSession(time("2026-06-12T04:00:00Z"), overlap)), emptyList(), zone)
    }
    @Test fun `morning Google Health summary is kept and takes precedence over other samples`() {
        val sessions = listOf(SleepBreathingSession(time("2026-06-12T06:00:00Z"), listOf(SleepBreathingInterval(time("2026-06-12T00:00:00Z"), time("2026-06-12T06:00:00Z")))))
        val samples = listOf(BreathingSample(time("2026-06-12T05:00:00Z"), 18.0),
            BreathingSample(time("2026-06-12T07:00:00Z"), 14.0, "com.fitbit.FitbitMobile"))
        assertEquals(14.0, breathingDuringSleep(sessions, samples, zone).getValue(LocalDate.parse("2026-06-12")), 0.0)
        val ambiguous = samples + BreathingSample(time("2026-06-12T08:00:00Z"), 15.0, "com.fitbit.FitbitMobile")
        assertTrue(breathingDuringSleep(sessions, ambiguous, zone).isEmpty())
    }
    @Test fun `dense Fitbit HRV across midnight needs sleep and uses asleep phases`() {
        val samples = listOf(BreathingSample(time("2026-06-11T21:55:00Z"), 10.0, "com.fitbit.FitbitMobile"),
            BreathingSample(time("2026-06-11T22:05:00Z"), 20.0, "com.fitbit.FitbitMobile"),
            BreathingSample(time("2026-06-12T05:00:00Z"), 40.0, "com.fitbit.FitbitMobile"))
        val sessions = listOf(SleepBreathingSession(time("2026-06-12T06:00:00Z"), listOf(
            SleepBreathingInterval(time("2026-06-11T21:50:00Z"), time("2026-06-12T06:00:00Z")))))
        val result = hrvDuringSleep(sessions, samples, zone)
        assertEquals(70.0 / 3.0, result.getValue(LocalDate.parse("2026-06-12")), 1e-9)
        assertFalse(result.containsKey(LocalDate.parse("2026-06-11")))
        assertTrue(hrvDuringSleep(emptyList(), samples, zone).isEmpty())
    }
    @Test fun `single Fitbit HRV and imported summary remain daily`() {
        val samples = listOf(BreathingSample(time("2026-06-12T07:00:00Z"), 51.0, "com.fitbit.FitbitMobile"),
            BreathingSample(time("2026-06-13T07:00:00Z"), 52.0, "pl.healthsync", true))
        val result = hrvDuringSleep(emptyList(), samples, zone)
        assertEquals(51.0, result.getValue(LocalDate.parse("2026-06-12")), 0.0)
        assertEquals(52.0, result.getValue(LocalDate.parse("2026-06-13")), 0.0)
    }
}
