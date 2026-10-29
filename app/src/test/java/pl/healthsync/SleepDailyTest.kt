package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SleepDailyTest {
    private fun interval(start: String, end: String): SleepInterval {
        val from = Instant.parse(start)
        val to = Instant.parse(end)
        return SleepInterval(from, to, listOf(SleepStageInterval(from, to, SleepState.ASLEEP)))
    }

    private val nightStart = Instant.parse("2026-10-28T22:00:00Z")
    private val nightDate = LocalDate.parse("2026-10-29")
    private fun night(vararg parts: Pair<SleepState, Long>): SleepInterval {
        var cursor = nightStart
        val stages = parts.map { (state, minutes) ->
            val end = cursor.plusSeconds(minutes * 60)
            SleepStageInterval(cursor, end, state).also { cursor = end }
        }
        return SleepInterval(nightStart, cursor, stages)
    }
    private fun day(session: SleepInterval) = sleepByEndDate(listOf(session)).getValue(nightDate)

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
        assertNull(day.awakeDuration)
    }

    @Test fun `asleep excludes every wake and awakenings exclude initial and final wake`() {
        val result = day(night(
            SleepState.AWAKE to 20, SleepState.ASLEEP to 120, SleepState.AWAKE to 5,
            SleepState.ASLEEP to 1, SleepState.AWAKE to 35, SleepState.ASLEEP to 180,
            SleepState.AWAKE to 30,
        ))
        assertEquals(301L, result.duration!!.toMinutes())
        assertEquals(40L, result.awakeDuration!!.toMinutes())
    }

    @Test fun `continuous sleep explicitly has zero awakenings`() {
        val result = day(night(SleepState.ASLEEP to 480))
        assertEquals(480L, result.duration!!.toMinutes())
        assertEquals(0L, result.awakeDuration!!.toMinutes())
    }

    @Test fun `sessions without stages do not become time in bed or zero wake`() {
        val result = day(SleepInterval(nightStart, nightStart.plusSeconds(8 * 3600)))
        assertNull(result.duration)
        assertNull(result.awakeDuration)
    }

    @Test fun `unknown stages and gaps inside sleep do not become guessed totals`() {
        val unknown = night(SleepState.ASLEEP to 120, SleepState.UNKNOWN to 10, SleepState.ASLEEP to 180)
        assertNull(day(unknown).duration)
        assertNull(day(unknown).awakeDuration)
        val gap = unknown.copy(stages = listOf(unknown.stages.first(), unknown.stages.last()))
        assertNull(day(gap).duration)
        assertNull(day(gap).awakeDuration)
    }

    @Test fun `known internal awakenings remain available when boundary stages are unknown`() {
        val result = day(night(SleepState.UNKNOWN to 10, SleepState.ASLEEP to 180,
            SleepState.AWAKE to 20, SleepState.ASLEEP to 180, SleepState.UNKNOWN to 10))
        assertNull(result.duration)
        assertEquals(20L, result.awakeDuration!!.toMinutes())
    }

    @Test fun `missing stages for one session do not produce a partial day total`() {
        val known = night(SleepState.ASLEEP to 120)
        val missing = SleepInterval(known.end.plusSeconds(3600), known.end.plusSeconds(7200))
        val result = sleepByEndDate(listOf(known, missing)).getValue(nightDate)
        assertNull(result.duration)
        assertNull(result.awakeDuration)
    }

    @Test fun `separate sessions sum sleep and awakenings without counting gap as awake`() {
        val first = night(SleepState.ASLEEP to 120, SleepState.AWAKE to 10, SleepState.ASLEEP to 120)
        val nextStart = first.end.plusSeconds(3600)
        val next = interval(nextStart.toString(), nextStart.plusSeconds(1800).toString())
        val result = sleepByEndDate(listOf(first, next)).getValue(nightDate)
        assertEquals(270L, result.duration!!.toMinutes())
        assertEquals(10L, result.awakeDuration!!.toMinutes())
    }

    @Test fun `overlapping or out of bounds stages do not double count`() {
        val source = night(SleepState.ASLEEP to 120, SleepState.AWAKE to 10, SleepState.ASLEEP to 120)
        val overlap = source.copy(stages = source.stages + source.stages.first())
        assertNull(day(overlap).duration)
        assertNull(day(overlap).awakeDuration)
        val outside = source.copy(stages = source.stages + SleepStageInterval(source.end, source.end.plusSeconds(60), SleepState.AWAKE))
        assertNull(day(outside).duration)
        assertNull(day(outside).awakeDuration)
    }
}
