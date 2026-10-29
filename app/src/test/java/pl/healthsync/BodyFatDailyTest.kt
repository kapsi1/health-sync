package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BodyFatDailyTest {
    @Test fun `mean uses every measurement for each Warsaw day including daylight transition`() {
        val samples = listOf(
            BodyFatSample(Instant.parse("2026-03-28T22:30:00Z"), "a", 21.0),
            BodyFatSample(Instant.parse("2026-03-28T23:30:00Z"), "b", 20.0),
            BodyFatSample(Instant.parse("2026-03-29T21:30:00Z"), "c", 18.0),
            BodyFatSample(Instant.parse("2026-03-29T22:30:00Z"), "d", 25.0),
        )
        assertEquals(mapOf(LocalDate.parse("2026-03-28") to 21.0,
            LocalDate.parse("2026-03-29") to 19.0, LocalDate.parse("2026-03-30") to 25.0), averageSamplesByDay(samples))
        assertTrue(averageSamplesByDay(emptyList()).isEmpty())
    }
}
