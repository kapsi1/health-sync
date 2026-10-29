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
    @Test fun exportsRoundedNumbersAndBothDurations() {
        assertEquals(12.35, sheetValue("distance_km", 12.34567))
        assertEquals(1234.57, sheetValue("total_kcal", 1234.5678))
        assertEquals(123.45, sheetValue("active_kcal", 123.454))
        assertEquals(72.13, sheetValue("weight_avg_kg", 72.125))
        assertEquals(72.0, sheetValue("weight_avg_kg", 72.0000000000001))
        assertEquals(-1.24, sheetValue("body_temperature_c", -1.235))
        assertEquals(12345.0, sheetValue("steps", 12345.0))
        assertEquals("00:30:00", sheetValue("exercise_hours", 0.5))
        assertEquals("07:30:00", sheetValue("sleep_hours", 7.5))
        assertEquals("25:00:00", sheetValue("sleep_hours", 25.0))
        assertEquals("00:40:00", sheetValue("sleep_awake_hours", 40.0 / 60))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonFiniteExport() {
        sheetValue("weight_avg_kg", Double.NaN)
    }
    @Test fun migrationDoesNotOverwriteTextOrFormulas() {
        assertEquals("01:30:00", legacyDurationText(1.5))
        assertNull(legacyDurationText("01:30:00"))
        assertNull(legacyDurationText("=A2"))
        assertNull(legacyDurationText(-1.0))
    }
}
