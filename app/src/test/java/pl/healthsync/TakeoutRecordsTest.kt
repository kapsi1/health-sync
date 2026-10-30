package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.io.StringReader
import java.time.Instant

class TakeoutRecordsTest {
    @Test fun `weight converts grams and keeps source for safe import filtering`() {
        val csv = "timestamp,weight grams,data source\n2026-10-14T06:00:00Z,70500,Google Health App\n2026-10-15T06:00:00Z,71000,Fit Health Connect\n"
        val rows = readTakeoutMeasurements(StringReader(csv), TakeoutKind.WEIGHT)
        assertEquals(70.5, rows[0].value, 0.0)
        assertEquals("Fit Health Connect", rows[1].source)
        assertEquals(Instant.parse("2026-10-14T06:00:00Z"), rows[0].time)
    }

    @Test fun `body fat retains percentage`() {
        val csv = "timestamp,body fat percentage,data source\n2026-10-14T06:00:00.123Z,17.8,Google Health App\n"
        assertEquals(17.8, readTakeoutMeasurements(StringReader(csv), TakeoutKind.BODY_FAT).single().value, 0.0)
    }

    @Test fun `height converts millimeters into meters`() {
        val csv = "timestamp,height millimeters,data source\n2026-10-14T06:00:00Z,1750,Google Health App\n"
        assertEquals(1.75, readTakeoutMeasurements(StringReader(csv), TakeoutKind.HEIGHT).single().value, 0.0)
    }

    @Test(expected = IllegalArgumentException::class) fun `rejects invalid values`() {
        readTakeoutMeasurements(StringReader("timestamp,body fat percentage,data source\n2026-10-14T06:00:00Z,101,Google Health App\n"), TakeoutKind.BODY_FAT)
    }

    @Test fun `stages joined by session id and sorted`() {
        val sleeps = "sleep_id,sleep_start,sleep_end,start_utc_offset,end_utc_offset,data_source,sleep_last_updated,minutes_asleep\n" +
            "s1,2026-10-28T22:00:00Z,2026-10-29T06:00:00Z,+01:00,+01:00,DERIVED,2026-10-29T06:10:00Z,450\n"
        val stages = "sleep_id,sleep_stage_id,sleep_stage_start,sleep_stage_end,sleep_stage_type\n" +
            "s1,b,2026-10-29T02:00:00Z,2026-10-29T06:00:00Z,REM\n" +
            "s1,a,2026-10-28T22:00:00Z,2026-10-29T02:00:00Z,LIGHT\n"
        val sleep = readTakeoutSleeps(StringReader(sleeps), StringReader(stages)).single()
        assertEquals(listOf("a", "b"), sleep.stages.map { it.id })
        assertEquals(450L, sleep.minutesAsleep)
    }

    @Test fun `sleep algorithm version is retained`() {
        val sleeps = "sleep_id,sleep_start,sleep_end,start_utc_offset,end_utc_offset,data_source,sleep_last_updated,minutes_asleep,algorithm_version\n" +
            "s1,2026-10-28T22:00:00Z,2026-10-29T06:00:00Z,+01:00,+01:00,DERIVED,2026-10-29T06:10:00Z,450,sleep_session_v2\n"
        val stages = "sleep_id,sleep_stage_id,sleep_stage_start,sleep_stage_end,sleep_stage_type\n"
        assertEquals("sleep_session_v2", readTakeoutSleeps(StringReader(sleeps), StringReader(stages)).single().algorithmVersion)
    }

    @Test(expected = IllegalArgumentException::class) fun `overlapping sleep stages are rejected`() {
        val sleeps = "sleep_id,sleep_start,sleep_end,start_utc_offset,end_utc_offset,data_source,sleep_last_updated,minutes_asleep\n" +
            "s1,2026-10-28T22:00:00Z,2026-10-29T06:00:00Z,+01:00,+01:00,DERIVED,2026-10-29T06:10:00Z,450\n"
        val stages = "sleep_id,sleep_stage_id,sleep_stage_start,sleep_stage_end,sleep_stage_type\n" +
            "s1,a,2026-10-28T22:00:00Z,2026-10-29T03:00:00Z,LIGHT\n" +
            "s1,b,2026-10-29T02:00:00Z,2026-10-29T06:00:00Z,REM\n"
        readTakeoutSleeps(StringReader(sleeps), StringReader(stages))
    }

    @Test(expected = IllegalArgumentException::class) fun `orphan stages are rejected`() {
        val sleeps = "sleep_id,sleep_start,sleep_end,start_utc_offset,end_utc_offset,data_source,sleep_last_updated,minutes_asleep\n"
        val stages = "sleep_id,sleep_stage_id,sleep_stage_start,sleep_stage_end,sleep_stage_type\n" +
            "unknown,a,2026-10-28T22:00:00Z,2026-10-29T03:00:00Z,LIGHT\n"
        readTakeoutSleeps(StringReader(sleeps), StringReader(stages))
    }
}
