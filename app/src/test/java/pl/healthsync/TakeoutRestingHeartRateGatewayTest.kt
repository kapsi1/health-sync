package pl.healthsync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.response.ReadRecordsResponse
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
