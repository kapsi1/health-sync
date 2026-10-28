package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {
    private val now = Instant.parse("2026-10-28T21:00:00Z")

    @Test fun formatsPolishTimeUnits() {
        assertEquals("przed chwilą", relativeTime(now.minusSeconds(30), now))
        assertEquals("1 minutę temu", relativeTime(now.minusSeconds(60), now))
        assertEquals("2 minuty temu", relativeTime(now.minusSeconds(120), now))
        assertEquals("12 minut temu", relativeTime(now.minusSeconds(720), now))
        assertEquals("22 minuty temu", relativeTime(now.minusSeconds(1320), now))
        assertEquals("1 godzinę temu", relativeTime(now.minusSeconds(3600), now))
        assertEquals("2 godziny temu", relativeTime(now.minusSeconds(7200), now))
        assertEquals("1 dzień temu", relativeTime(now.minusSeconds(86400), now))
        assertEquals("2 dni temu", relativeTime(now.minusSeconds(172800), now))
    }
}
