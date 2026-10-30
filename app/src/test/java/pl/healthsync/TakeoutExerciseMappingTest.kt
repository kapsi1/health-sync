package pl.healthsync

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.data.*
import pl.healthsync.domain.*
import java.time.Instant
import java.time.ZoneOffset

class TakeoutExerciseMappingTest {
    private val start = Instant.parse("2026-06-12T08:00:00Z")
    private val row = TakeoutExercise("id", start, start.plusSeconds(3600), ZoneOffset.ofHours(2), start, "Weightlifting", "TRACKER", "UNSPECIFIED")
    @Test fun `maps source interval and activity without creating detailed sensor values`() {
        val record = mapTakeoutExercise(row, "account").record as ExerciseSessionRecord
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING, record.exerciseType)
        assertEquals(start, record.startTime)
        assertEquals(ZoneOffset.ofHours(2), record.startZoneOffset)
        assertEquals("Weightlifting", record.title)
        assertTrue(record.segments.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun `unknown activities cannot be guessed`() { mapTakeoutExercise(row.copy(activity = "unknown"), "account") }
    @Test(expected = IllegalArgumentException::class) fun `sessions with unsupported events cannot be silently reduced`() { mapTakeoutExercise(row.copy(unsupportedDetails = true), "account") }
}
