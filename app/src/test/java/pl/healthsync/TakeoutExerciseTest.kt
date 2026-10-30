package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.io.StringReader
import java.time.Instant
import java.time.ZoneOffset

class TakeoutExerciseTest {
    private val header = "exercise_id,exercise_start,exercise_end,utc_offset,exercise_last_updated,activity_name,log_type,deletion_reason\n"
    private val row = "a,2026-06-12T08:00:00Z,2026-06-12T09:00:00Z,+02:00,2026-06-12T10:00:00Z,Outdoor Walk,TRACKER,UNSPECIFIED\n"

    @Test fun `exercise parser preserves identity interval source and timezone`() {
        val record = readTakeoutExercises(StringReader(header + row)).single()
        assertEquals("a", record.id)
        assertEquals(Instant.parse("2026-06-12T08:00:00Z"), record.start)
        assertEquals(ZoneOffset.ofHours(2), record.offset)
        assertEquals("TRACKER", record.logType)
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate exercise IDs cannot be silently merged`() {
        readTakeoutExercises(StringReader(header + row + row))
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid duration cannot be imported`() {
        readTakeoutExercises(StringReader(header + row.replace("2026-06-12T09:00:00Z", "2026-06-12T08:00:00Z")))
    }

    @Test fun `adjacent sessions stay separate and transitive overlaps form one group`() {
        val first = readTakeoutExercises(StringReader(header + row)).single()
        val second = first.copy(id = "b", start = first.start.plusSeconds(1800), end = first.end.plusSeconds(1800))
        val third = second.copy(id = "c", start = first.end.plusSeconds(1200), end = first.end.plusSeconds(3600))
        val fourth = third.copy(id = "d", start = third.end, end = third.end.plusSeconds(600))
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d")),
            overlappingExerciseGroups(listOf(fourth, second, first, third)).map { group -> group.map { it.id } })
    }

    @Test fun `only tracker wins an automatic walk pair`() {
        val tracker = readTakeoutExercises(StringReader(header + row)).single()
        val automatic = tracker.copy(id = "auto", logType = "AUTO_DETECTED")
        assertEquals(ImportDecision.INSERT, selectedTakeoutExercises(listOf(tracker, automatic))["a"])
        assertEquals(ImportDecision.SUPERSEDED, selectedTakeoutExercises(listOf(tracker, automatic))["auto"])
        assertTrue(selectedTakeoutExercises(listOf(tracker, automatic.copy(activity = "Bike"))).values.all { it == ImportDecision.CONFLICT })
    }
}
