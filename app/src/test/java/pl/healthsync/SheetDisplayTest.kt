package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.healthsync.domain.durationAsText
import pl.healthsync.domain.sheetValue
import pl.healthsync.domain.legacyDurationText

class SheetDisplayTest {
    @Test fun durationRoundsToSecondsWithoutWrappingHours() {
        assertEquals("00:00:00", durationAsText(0.0))
        assertEquals("01:30:00", durationAsText(1.5))
        assertEquals("01:00:00", durationAsText(3599.6 / 3600))
        assertEquals("27:01:02", durationAsText(27.0 + 62.0 / 3600))
    }
    @Test fun otherMetricsRemainNumeric() {
        assertEquals(12.34567, sheetValue("distance_km", 12.34567))
        assertEquals("00:30:00", sheetValue("exercise_hours", 0.5))
    }
    @Test fun migrationDoesNotOverwriteTextOrFormulas() {
        assertEquals("01:30:00", legacyDurationText(1.5))
        assertNull(legacyDurationText("01:30:00"))
        assertNull(legacyDurationText("=A2"))
        assertNull(legacyDurationText(-1.0))
    }
}
