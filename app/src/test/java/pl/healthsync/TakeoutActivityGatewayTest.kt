package pl.healthsync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.response.ReadRecordsResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.data.*
import pl.healthsync.domain.*
import java.lang.reflect.Proxy
import java.time.LocalDate

class TakeoutActivityGatewayTest {
    @Test fun `existing partial steps interval blocks whole day while adjacent day stays clear`() = runBlocking {
        val kind = TakeoutActivityKind.STEPS
        val first = mapTakeoutActivity(kind, LocalDate.parse("2026-06-12"), 1200.0, "account")
        val second = mapTakeoutActivity(kind, LocalDate.parse("2026-06-13"), 500.0, "account")
        val day = first.record as StepsRecord
        val existing = StepsRecord(startTime = day.startTime.plusSeconds(3600), startZoneOffset = day.startZoneOffset,
            endTime = day.startTime.plusSeconds(3660), endZoneOffset = day.startZoneOffset, count = 20, metadata = Metadata.manualEntry())
        val permission = Proxy.newProxyInstance(PermissionController::class.java.classLoader, arrayOf(PermissionController::class.java)) { _, method, _ ->
            if (method.name == "getGrantedPermissions") activityWritePermissions(setOf(kind)) else error(method.name)
        } as PermissionController
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader, arrayOf(HealthConnectClient::class.java)) { _, method, _ ->
            when (method.name) { "getPermissionController" -> permission; "readRecords" -> ReadRecordsResponse(listOf(existing), null); else -> error(method.name) }
        } as HealthConnectClient
        val gateway = HealthConnectTakeoutActivityGateway(client, "pl.healthsync", listOf(first, second))
        val a = takeoutPayloadCandidate(first.record); val b = takeoutPayloadCandidate(second.record)
        val result = gateway.inspectBatch(listOf(a, b))
        assertTrue(result.getValue(a.clientId) is TakeoutWriteCheck.Blocked)
        assertEquals(TakeoutWriteCheck.Missing, result.getValue(b.clientId))
        try { gateway.insertBatch(listOf(a, b)); fail("Expected overlap rejection") } catch (_: IllegalStateException) { }
    }

    @Test fun `day interval follows daylight saving transition and record identity is stable`() {
        val first = mapTakeoutActivity(TakeoutActivityKind.STEPS, LocalDate.parse("2026-03-29"), 2000.0, "account")
        val second = mapTakeoutActivity(TakeoutActivityKind.STEPS, LocalDate.parse("2026-03-29"), 2001.0, "account")
        val record = first.record as StepsRecord
        assertEquals(23 * 3600L, java.time.Duration.between(record.startTime, record.endTime).seconds)
        val a = takeoutPayloadCandidate(first.record); val b = takeoutPayloadCandidate(second.record)
        assertEquals(a.clientId, b.clientId)
        assertNotEquals(a.contentHash, b.contentHash)
    }

    @Test fun `recovery recognizes own record and undo blocks changed value`() = runBlocking {
        val kind = TakeoutActivityKind.STEPS
        val item = mapTakeoutActivity(kind, LocalDate.parse("2026-06-12"), 1200.0, "account")
        val row = item.record as StepsRecord
        val origin = DataOrigin::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance("pl.healthsync")
        val ctor = Metadata::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java, DataOrigin::class.java,
            java.time.Instant::class.java, String::class.java, Long::class.javaPrimitiveType, Device::class.java).apply { isAccessible = true }
        val modified = java.time.Instant.parse("2026-06-20T00:00:00Z")
        val metadata = ctor.newInstance(row.metadata.recordingMethod, "record-id", origin, modified, row.metadata.clientRecordId, row.metadata.clientRecordVersion, row.metadata.device)
        fun stored(count: Long) = StepsRecord(startTime = row.startTime, startZoneOffset = row.startZoneOffset, endTime = row.endTime,
            endZoneOffset = row.endZoneOffset, count = count, metadata = metadata)
        var entries = listOf(stored(1200))
        val permission = Proxy.newProxyInstance(PermissionController::class.java.classLoader, arrayOf(PermissionController::class.java)) { _, method, _ ->
            if (method.name == "getGrantedPermissions") activityWritePermissions(setOf(kind)) else error(method.name)
        } as PermissionController
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader, arrayOf(HealthConnectClient::class.java)) { _, method, _ ->
            when (method.name) { "getPermissionController" -> permission; "readRecords" -> ReadRecordsResponse(entries, null); else -> error(method.name) }
        } as HealthConnectClient
        val gateway = HealthConnectTakeoutActivityGateway(client, "pl.healthsync", listOf(item))
        val candidate = takeoutPayloadCandidate(row)
        val receipt = TakeoutWriteReceipt("record-id", modified.toString())
        assertEquals(TakeoutWriteCheck.AlreadyWritten(receipt), gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId))
        val undo = TakeoutUndoEntry(candidate, receipt)
        assertEquals(TakeoutUndoCheck.Unchanged, gateway.inspect(undo))
        entries = listOf(stored(1201))
        assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
        assertTrue(gateway.inspect(undo) is TakeoutUndoCheck.Blocked)
    }
}
