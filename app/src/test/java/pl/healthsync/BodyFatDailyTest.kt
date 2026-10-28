package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BodyFatDailyTest {
    @Test fun `chooses latest reading per Warsaw day across daylight saving transition`() {
        val samples = listOf(
            BodyFatSample(Instant.parse("2026-03-28T22:30:00Z"), "a", 21.0),
            BodyFatSample(Instant.parse("2026-03-28T23:30:00Z"), "b", 20.0),
            BodyFatSample(Instant.parse("2026-03-29T21:30:00Z"), "c", 19.0),
            BodyFatSample(Instant.parse("2026-03-29T22:30:00Z"), "d", 18.0),
        )
        assertEquals(mapOf(
            LocalDate.parse("2026-03-28") to 21.0,
            LocalDate.parse("2026-03-29") to 19.0,
            LocalDate.parse("2026-03-30") to 18.0,
        ), latestBodyFatByDay(samples).mapValues { it.value.value })
    }

    @Test fun `empty input is not converted to zero`() {
        assertEquals(emptyMap<LocalDate, BodyFatSample>(), latestBodyFatByDay(emptyList()))
    }
}
