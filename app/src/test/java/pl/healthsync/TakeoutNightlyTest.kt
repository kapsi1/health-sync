package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import pl.healthsync.data.*
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TakeoutNightlyTest {
    private val day = LocalDate.parse("2026-06-12")
    private val sleep = TakeoutSleep("s", Instant.parse("2026-06-11T22:00:00Z"), Instant.parse("2026-06-12T06:00:00Z"), ZoneOffset.ofHours(2), ZoneOffset.ofHours(2), "Google Health App", Instant.parse("2026-06-12T06:00:00Z"), 440, emptyList(), "sleep_session_v2")

    @Test fun `temperature uses nightly deviation and skips unavailable baseline`() {
        val csv = "timestamp,nightly temperature celsius,baseline temperature celsius,data source\n2026-06-12T00:00:00Z,33.1,33.0,Google Health App\n2026-06-13T00:00:00Z,33,NaN,Google Health App\n"
        val rows = readTakeoutNightly(StringReader(csv), NightlyKind.SKIN_TEMPERATURE)
        assertEquals(0.1, rows[0].value!!, 1e-9)
        assertNull(rows[1].value)
        assertEquals(day, rows[0].day)
    }

    @Test fun `nightly summary requires an unambiguous eligible v2 sleep`() {
        assertEquals(sleep, nightlySleepForDay(day, listOf(sleep), emptySet(), ZoneId.of("Europe/Warsaw")))
        assertNull(nightlySleepForDay(day, listOf(sleep.copy(minutesAsleep = 180)), emptySet(), ZoneId.of("Europe/Warsaw")))
        assertNull(nightlySleepForDay(day, listOf(sleep), null, ZoneId.of("Europe/Warsaw")))
        assertNull(nightlySleepForDay(day, listOf(sleep), setOf("s"), ZoneId.of("Europe/Warsaw")))
    }

    @Test fun `nightly records are deterministic and attach to sleep`() {
        for (row in listOf(TakeoutNightly(NightlyKind.HRV, day, 48.5), TakeoutNightly(NightlyKind.BREATHING, day, 14.0), TakeoutNightly(NightlyKind.SKIN_TEMPERATURE, day, 0.1, 33.0))) {
            val a = mapTakeoutNightly(row, sleep, "account").record
            val b = mapTakeoutNightly(row, sleep, "account").record
            assertEquals(takeoutPayloadCandidate(a), takeoutPayloadCandidate(b))
            assertTrue(takeoutPayloadMatches(a, b))
        }
    }
}
