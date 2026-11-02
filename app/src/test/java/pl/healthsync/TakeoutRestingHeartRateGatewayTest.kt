package pl.healthsync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.data.*
import pl.healthsync.domain.*
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TakeoutRestingHeartRateGatewayTest {
    @Test fun `matches only the Connect supplied offset for the original day`() {
        val at = Instant.parse("2026-09-17T00:00:00Z")
        val planned = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.RESTING_HEART_RATE, at, 61.6, "Google Health App"), "account") as RestingHeartRateRecord
        fun actual(offset: ZoneOffset, bpm: Long = planned.beatsPerMinute) = RestingHeartRateRecord(time = at, zoneOffset = offset, beatsPerMinute = bpm, metadata = planned.metadata)
        assertTrue(takeoutPayloadMatches(planned, actual(ZoneOffset.ofHours(2))))
        assertFalse(takeoutPayloadMatches(planned, actual(ZoneOffset.ofHours(1))))
        assertFalse(takeoutPayloadMatches(planned, actual(ZoneOffset.ofHours(2), bpm = planned.beatsPerMinute + 1)))
    }
    @Test fun `body fat matches only Warsaw offset for measurement time with all other fields unchanged`() {
        for ((time, offset) in listOf(
            "2026-01-15T12:00:00Z" to ZoneOffset.ofHours(1),
            "2026-07-15T12:00:00Z" to ZoneOffset.ofHours(2),
            "2026-03-29T00:59:59Z" to ZoneOffset.ofHours(1),
            "2026-03-29T01:00:00Z" to ZoneOffset.ofHours(2),
        )) {
            val at = Instant.parse(time)
            val planned = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.BODY_FAT, at, 20.0, "Google Health App"), "account") as BodyFatRecord
            fun actual(time: Instant = at, zone: ZoneOffset? = offset, value: Double = 20.0, metadata: Metadata = planned.metadata) =
                BodyFatRecord(time, zone, Percentage(value), metadata)
            assertTrue(takeoutPayloadMatches(planned, actual(zone = null)))
            assertTrue(takeoutPayloadMatches(planned, actual()))
            assertFalse(takeoutPayloadMatches(planned, actual(zone = ZoneOffset.UTC)))
            assertFalse(takeoutPayloadMatches(planned, actual(value = 20.1)))
            assertFalse(takeoutPayloadMatches(planned, actual(time = at.plusSeconds(1))))
            assertFalse(takeoutPayloadMatches(planned, actual(metadata = Metadata.unknownRecordingMethod(clientRecordId = "different", clientRecordVersion = 0))))
            assertFalse(takeoutPayloadMatches(planned, actual(metadata = Metadata.unknownRecordingMethod(clientRecordId = requireNotNull(planned.metadata.clientRecordId), clientRecordVersion = 1))))
            val explicitlyZoned = actual()
            assertFalse(takeoutPayloadMatches(explicitlyZoned, actual(zone = null)))
            assertFalse(takeoutPayloadMatches(explicitlyZoned, actual(zone = ZoneOffset.UTC)))
        }
    }
    @Test fun `weight allows only observed offset and millisecond truncation`() {
        for ((time, offset) in listOf(
            "2026-01-15T12:00:00.123456Z" to ZoneOffset.ofHours(1),
            "2026-07-15T12:00:00.123456Z" to ZoneOffset.ofHours(2),
            "2026-03-29T00:59:59.123456Z" to ZoneOffset.ofHours(1),
            "2026-03-29T01:00:00.123456Z" to ZoneOffset.ofHours(2),
        )) {
            val at = Instant.parse(time)
            val planned = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.WEIGHT, at, 70.0, "Google Health App"), "account") as WeightRecord
            fun actual(time: Instant = Instant.ofEpochMilli(at.toEpochMilli()), zone: ZoneOffset? = offset, value: Double = 70.0, metadata: Metadata = planned.metadata) =
                WeightRecord(time, zone, Mass.kilograms(value), metadata)
            assertTrue(takeoutPayloadMatches(planned, actual()))
            assertTrue(takeoutPayloadMatches(planned, actual(time = at)))
            assertTrue(takeoutPayloadMatches(planned, actual(zone = null)))
            assertFalse(takeoutPayloadMatches(planned, actual(zone = ZoneOffset.UTC)))
            assertFalse(takeoutPayloadMatches(planned, actual(value = 71.0)))
            assertFalse(takeoutPayloadMatches(planned, actual(time = at.plusNanos(1))))
            assertFalse(takeoutPayloadMatches(planned, actual(time = Instant.ofEpochMilli(at.toEpochMilli() + 1))))
            assertFalse(takeoutPayloadMatches(planned, actual(metadata = Metadata.unknownRecordingMethod(clientRecordId = "different", clientRecordVersion = 0))))
            assertFalse(takeoutPayloadMatches(planned, actual(metadata = Metadata.unknownRecordingMethod(clientRecordId = requireNotNull(planned.metadata.clientRecordId), clientRecordVersion = 1))))
        }
    }
    @Test fun `Connect resting heart rate rounds source decimals to nearest whole beat`() {
        val at = Instant.parse("2026-06-12T00:00:00Z")
        val row = TakeoutMeasurement(TakeoutKind.RESTING_HEART_RATE, at, 61.678, "Google Health App")
        val record = mapTakeoutMeasurement(row, "account") as RestingHeartRateRecord
        assertEquals(62L, record.beatsPerMinute)
        assertEquals(at, record.time)
    }
    @Test fun `daily resting heart rate is blocked if a later record already exists in Connect`() = runBlocking {
        val at = Instant.parse("2026-06-12T00:00:00Z")
        val source = TakeoutMeasurement(TakeoutKind.RESTING_HEART_RATE, at, 61.0, "Google Health App")
        val planned = TakeoutPlannedRecord(mapTakeoutMeasurement(source, "account"))
        val record = RestingHeartRateRecord(time = at.plusSeconds(36000), zoneOffset = null, beatsPerMinute = 65, metadata = Metadata.manualEntry())
        val permissions = Proxy.newProxyInstance(PermissionController::class.java.classLoader, arrayOf(PermissionController::class.java)) { _, method, _ ->
            if (method.name == "getGrantedPermissions") takeoutReadPermissions(setOf(TakeoutType.RESTING_HEART_RATE)) + HealthPermission.getWritePermission(RestingHeartRateRecord::class) else error(method.name)
        } as PermissionController
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader, arrayOf(HealthConnectClient::class.java)) { _, method, _ ->
            when (method.name) { "getPermissionController" -> permissions; "readRecords" -> ReadRecordsResponse(listOf(record), null); else -> error(method.name) }
        } as HealthConnectClient
        val gateway = HealthConnectTakeoutGateway(client, "pl.healthsync", listOf(planned))
        val candidate = takeoutPayloadCandidate(planned.record)
        assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
        assertTrue(gateway.inspect(candidate) is TakeoutWriteCheck.Blocked)
        assertEquals(setOf(LocalDate.parse("2026-06-12")), affectedTakeoutDates(listOf(planned)).getValue("resting_heart_rate"))
    }
}
