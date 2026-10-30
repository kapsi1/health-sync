package pl.healthsync

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.data.mapTakeoutMeasurement
import pl.healthsync.data.mapTakeoutSleep
import pl.healthsync.data.takeoutPayloadCandidate
import pl.healthsync.domain.*
import java.time.Instant
import java.time.ZoneOffset

class TakeoutRecordMappingTest {
    private val at = Instant.parse("2026-06-11T22:22:00Z")

    @Test fun `preserves sleep instant offsets stages and deterministic identity`() {
        val stage = TakeoutStage("stage", at, at.plusSeconds(3600), "REM")
        val row = TakeoutSleep("session", at, at.plusSeconds(3600), ZoneOffset.ofHours(2), ZoneOffset.ofHours(2), "DERIVED", at.plusSeconds(5000), 60, listOf(stage), "sleep_session_v2")
        val record = mapTakeoutSleep(row, "account")
        assertEquals(at, record.startTime)
        assertEquals(ZoneOffset.ofHours(2), record.endZoneOffset)
        assertEquals(SleepSessionRecord.STAGE_TYPE_REM, record.stages.single().stage)
        assertEquals(record.metadata.clientRecordId, mapTakeoutSleep(row, "account").metadata.clientRecordId)
        assertNotEquals(record.metadata.clientRecordId, mapTakeoutSleep(row, "other").metadata.clientRecordId)
        assertEquals(at.plusSeconds(5000).toEpochMilli(), record.metadata.clientRecordVersion)
        assertEquals(takeoutPayloadCandidate(record), takeoutPayloadCandidate(mapTakeoutSleep(row, "account")))
        assertNotEquals(takeoutPayloadCandidate(record).contentHash, takeoutPayloadCandidate(mapTakeoutSleep(row.copy(stages = listOf(stage.copy(type = "DEEP"))), "account")).contentHash)
    }

    @Test fun `measurements preserve values but do not invent zone offsets`() {
        val weight = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.WEIGHT, at, 70.5, "Google Health App"), "account") as WeightRecord
        val fat = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.BODY_FAT, at, 22.0, "Google Health App"), "account") as BodyFatRecord
        assertEquals(70.5, weight.weight.inKilograms, 0.0)
        assertEquals(22.0, fat.percentage.value, 0.0)
        assertNull(weight.zoneOffset)
        assertNotEquals(weight.metadata.clientRecordId, fat.metadata.clientRecordId)
        assertNotEquals(takeoutPayloadCandidate(weight).contentHash, takeoutPayloadCandidate(mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.WEIGHT, at, 70.6, "Google Health App"), "account")).contentHash)
    }

    @Test(expected = IllegalArgumentException::class) fun `refuses to map copied Connect measurements`() {
        mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.WEIGHT, at, 70.5, "Fit Health Connect"), "account")
    }

    @Test fun `height maps exact instant and meters without claiming a time zone`() {
        val height = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.HEIGHT, at, 1.75, "Google Health App"), "account") as HeightRecord
        assertEquals(at, height.time)
        assertEquals(1.75, height.height.inMeters, 0.0)
        assertNull(height.zoneOffset)
    }
}
