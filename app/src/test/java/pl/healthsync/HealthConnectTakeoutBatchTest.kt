package pl.healthsync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.data.*
import pl.healthsync.domain.*
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectTakeoutBatchTest {
    private val at = Instant.parse("2026-06-12T08:00:00Z")
    private fun record(minute: Long) = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.WEIGHT, at.plusSeconds(minute * 60), 70.0, "Google Health App"), "account") as WeightRecord
    private class FakeClient {
        var reads = 0
        var writes = 0
        var stored: List<WeightRecord> = emptyList()
        val permission = Proxy.newProxyInstance(PermissionController::class.java.classLoader, arrayOf(PermissionController::class.java)) { _, method, _ ->
            when (method.name) {
                "getGrantedPermissions" -> takeoutReadPermissions(setOf(TakeoutType.WEIGHT)) + HealthPermission.getWritePermission(WeightRecord::class)
                else -> error("Unexpected permission request: ${method.name}")
            }
        } as PermissionController
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader, arrayOf(HealthConnectClient::class.java)) { _, method, _ ->
            when (method.name) {
                "getPermissionController" -> permission
                "readRecords" -> { reads++; ReadRecordsResponse(stored, null) }
                "insertRecords" -> { writes++; InsertRecordsResponse(emptyList()) }
                else -> error("Unexpected client request: ${method.name}")
            }
        } as HealthConnectClient
    }

    @Test fun `same type batch uses one read and one insert`() = runBlocking {
        val fake = FakeClient()
        val planned = (1L..3L).map { TakeoutPlannedRecord(record(it)) }
        val gateway = HealthConnectTakeoutGateway(fake.client, "pl.healthsync", planned)
        val candidates = planned.map { takeoutPayloadCandidate(it.record) }
        assertTrue(gateway.inspectBatch(candidates).values.all { it == TakeoutWriteCheck.Missing })
        assertEquals(1, fake.reads)
        gateway.insertBatch(candidates)
        assertEquals(2, fake.reads)
        assertEquals(1, fake.writes)
    }

    @Test fun `foreign same time record prevents entire batch insertion`() = runBlocking {
        val fake = FakeClient()
        val planned = (1L..3L).map { TakeoutPlannedRecord(record(it)) }
        val first = planned.first().record as WeightRecord
        fake.stored = listOf(WeightRecord(first.time, null, Mass.kilograms(70.0), Metadata.unknownRecordingMethod()))
        val gateway = HealthConnectTakeoutGateway(fake.client, "pl.healthsync", planned)
        val candidates = planned.map { takeoutPayloadCandidate(it.record) }
        assertTrue(gateway.inspectBatch(candidates).getValue(candidates.first().clientId) is TakeoutWriteCheck.Blocked)
        try { gateway.insertBatch(candidates); fail("Expected conflict") } catch (_: IllegalStateException) { }
        assertEquals(0, fake.writes)
    }

    @Test fun `changed owned record is blocked with its type in single and batch inspection`() = runBlocking {
        val fake = FakeClient()
        val planned = record(1)
        val origin = DataOrigin::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance("pl.healthsync")
        val ctor = Metadata::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java, DataOrigin::class.java,
            Instant::class.java, String::class.java, Long::class.javaPrimitiveType, Device::class.java).apply { isAccessible = true }
        val metadata = ctor.newInstance(planned.metadata.recordingMethod, "record-id", origin, at, planned.metadata.clientRecordId, planned.metadata.clientRecordVersion, planned.metadata.device)
        fake.stored = listOf(WeightRecord(planned.time, planned.zoneOffset, Mass.kilograms(71.0), metadata))
        val gateway = HealthConnectTakeoutGateway(fake.client, "pl.healthsync", listOf(TakeoutPlannedRecord(planned)))
        val candidate = takeoutPayloadCandidate(planned)
        val expected = TakeoutWriteCheck.Blocked("Zapisany rekord nie odpowiada planowi importu: Waga")
        assertEquals(expected, gateway.inspect(candidate))
        assertEquals(expected, gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId))
    }

    @Test fun `body fat resume recovers Connect offset without inserting a duplicate`() = runBlocking {
        val planned = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.BODY_FAT, at, 20.0, "Google Health App"), "account") as BodyFatRecord
        fun stored(packageName: String, value: Double = 20.0, offset: ZoneOffset = ZoneOffset.ofHours(2)): BodyFatRecord {
            val origin = DataOrigin::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(packageName)
            val ctor = Metadata::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java, DataOrigin::class.java,
                Instant::class.java, String::class.java, Long::class.javaPrimitiveType, Device::class.java).apply { isAccessible = true }
            val metadata = ctor.newInstance(planned.metadata.recordingMethod, "record-id", origin, at, planned.metadata.clientRecordId, planned.metadata.clientRecordVersion, planned.metadata.device)
            return BodyFatRecord(at, offset, Percentage(value), metadata)
        }
        var existing = listOf(stored("pl.healthsync"))
        var writes = 0
        val permissions = Proxy.newProxyInstance(PermissionController::class.java.classLoader, arrayOf(PermissionController::class.java)) { _, method, _ ->
            if (method.name == "getGrantedPermissions") takeoutReadPermissions(setOf(TakeoutType.FAT)) + HealthPermission.getWritePermission(BodyFatRecord::class) else error(method.name)
        } as PermissionController
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader, arrayOf(HealthConnectClient::class.java)) { _, method, _ ->
            when (method.name) {
                "getPermissionController" -> permissions
                "readRecords" -> ReadRecordsResponse(existing, null)
                "insertRecords" -> { writes++; InsertRecordsResponse(emptyList()) }
                else -> error(method.name)
            }
        } as HealthConnectClient
        val gateway = HealthConnectTakeoutGateway(client, "pl.healthsync", listOf(TakeoutPlannedRecord(planned)))
        val candidate = takeoutPayloadCandidate(planned)
        val receipt = TakeoutWriteReceipt("record-id", at.toString())
        assertEquals(TakeoutWriteCheck.AlreadyWritten(receipt), gateway.inspect(candidate))
        assertEquals(TakeoutWriteCheck.AlreadyWritten(receipt), gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId))
        var confirmed: TakeoutWriteReceipt? = null
        val journal = object : TakeoutWriteJournal {
            override suspend fun prepare(sessionId: String, candidate: TakeoutWriteCandidate) = Unit
            override suspend fun confirmed(sessionId: String, candidate: TakeoutWriteCandidate) = confirmed
            override suspend fun confirm(sessionId: String, candidate: TakeoutWriteCandidate, receipt: TakeoutWriteReceipt) { confirmed = receipt }
        }
        val coordinator = TakeoutBatchWriteCoordinator(journal, gateway)
        coordinator.run("run", listOf(candidate))
        coordinator.run("run", listOf(candidate))
        assertEquals(receipt, confirmed)
        assertEquals(0, writes)
        existing = listOf(stored("pl.healthsync", value = 21.0))
        assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
        existing = listOf(stored("pl.healthsync", offset = ZoneOffset.UTC))
        assertTrue(gateway.inspect(candidate) is TakeoutWriteCheck.Blocked)
        existing = listOf(stored("another.app"))
        assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
        existing = listOf(stored("pl.healthsync"), stored("pl.healthsync"))
        assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
    }

    @Test fun `weight recovery recognizes millisecond truncation without inserting a duplicate`() = runBlocking {
        val fake = FakeClient()
        val time = at.plusNanos(123_456)
        val planned = mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.WEIGHT, time, 70.0, "Google Health App"), "account") as WeightRecord
        fun stored(packageName: String = "pl.healthsync", storedTime: Instant = at, value: Double = 70.0,
                   offset: ZoneOffset? = ZoneOffset.ofHours(2), version: Long = planned.metadata.clientRecordVersion): WeightRecord {
            val origin = DataOrigin::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(packageName)
            val ctor = Metadata::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java, DataOrigin::class.java,
                Instant::class.java, String::class.java, Long::class.javaPrimitiveType, Device::class.java).apply { isAccessible = true }
            val metadata = ctor.newInstance(planned.metadata.recordingMethod, "record-id", origin, at, planned.metadata.clientRecordId, version, planned.metadata.device)
            return WeightRecord(storedTime, offset, Mass.kilograms(value), metadata)
        }
        fake.stored = listOf(stored())
        val gateway = HealthConnectTakeoutGateway(fake.client, "pl.healthsync", listOf(TakeoutPlannedRecord(planned)))
        val candidate = takeoutPayloadCandidate(planned)
        val receipt = TakeoutWriteReceipt("record-id", at.toString())
        assertEquals(TakeoutWriteCheck.AlreadyWritten(receipt), gateway.inspect(candidate))
        assertEquals(TakeoutWriteCheck.AlreadyWritten(receipt), gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId))
        var confirmed: TakeoutWriteReceipt? = null
        val journal = object : TakeoutWriteJournal {
            override suspend fun prepare(sessionId: String, candidate: TakeoutWriteCandidate) = Unit
            override suspend fun confirmed(sessionId: String, candidate: TakeoutWriteCandidate) = confirmed
            override suspend fun confirm(sessionId: String, candidate: TakeoutWriteCandidate, receipt: TakeoutWriteReceipt) { confirmed = receipt }
        }
        val coordinator = TakeoutBatchWriteCoordinator(journal, gateway)
        coordinator.run("run", listOf(candidate))
        coordinator.run("run", listOf(candidate))
        assertEquals(receipt, confirmed)
        assertEquals(0, fake.writes)
        for (changed in listOf(stored(value = 71.0), stored(offset = ZoneOffset.UTC), stored(version = 1),
            stored(storedTime = at.plusNanos(1)), stored(storedTime = at.plusMillis(1)), stored(packageName = "another.app"))) {
            fake.stored = listOf(changed)
            assertTrue(gateway.inspect(candidate) is TakeoutWriteCheck.Blocked)
            assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
        }
        fake.stored = listOf(stored(), stored())
        assertTrue(gateway.inspect(candidate) is TakeoutWriteCheck.Blocked)
        assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
    }

    @Test fun `weight measurements in same millisecond block writes even across batches`() = runBlocking {
        val fake = FakeClient()
        val planned = listOf(at.plusNanos(100_000), at.plusNanos(200_000)).map { time ->
            TakeoutPlannedRecord(mapTakeoutMeasurement(TakeoutMeasurement(TakeoutKind.WEIGHT, time, 70.0, "Google Health App"), "account"))
        }
        val gateway = HealthConnectTakeoutGateway(fake.client, "pl.healthsync", planned)
        val candidates = planned.map { takeoutPayloadCandidate(it.record) }
        for (candidate in candidates) {
            assertTrue(gateway.inspect(candidate) is TakeoutWriteCheck.Blocked)
            assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
            try { gateway.insertBatch(listOf(candidate)); fail("Expected conflict") } catch (_: IllegalStateException) { }
        }
        assertEquals(0, fake.writes)
    }

    @Test fun `already written sibling does not block second split sleep but foreign session does`() = runBlocking {
        fun source(id: String, start: Long, end: Long): TakeoutSleep {
            val from = at.plusSeconds(start); val until = at.plusSeconds(end)
            return TakeoutSleep(id, from, until, ZoneOffset.UTC, ZoneOffset.UTC, "DERIVED", at, (end - start) / 60,
                listOf(TakeoutStage(id, from, until, "LIGHT")), "sleep_session_v2")
        }
        val early = mapTakeoutSleep(source("early", 0, 1800), "account") as SleepSessionRecord
        val late = mapTakeoutSleep(source("late", 3600, 7200), "account") as SleepSessionRecord
        val plans = listOf(early, late).map { TakeoutPlannedRecord(it, at, at.plusSeconds(10800)) }
        fun stored(record: SleepSessionRecord, packageName: String): SleepSessionRecord {
            val origin = DataOrigin::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(packageName)
            val ctor = Metadata::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java, DataOrigin::class.java,
                Instant::class.java, String::class.java, Long::class.javaPrimitiveType, Device::class.java).apply { isAccessible = true }
            val m = ctor.newInstance(record.metadata.recordingMethod, "record-id", origin, at, record.metadata.clientRecordId, record.metadata.clientRecordVersion, record.metadata.device)
            return SleepSessionRecord(startTime = record.startTime, startZoneOffset = record.startZoneOffset,
                endTime = record.endTime, endZoneOffset = record.endZoneOffset, metadata = m,
                title = record.title, notes = record.notes, stages = record.stages)
        }
        var existing = listOf(stored(early, "pl.healthsync"))
        val permission = Proxy.newProxyInstance(PermissionController::class.java.classLoader, arrayOf(PermissionController::class.java)) { _, method, _ ->
            if (method.name == "getGrantedPermissions") takeoutReadPermissions(setOf(TakeoutType.SLEEP)) + HealthPermission.getWritePermission(SleepSessionRecord::class) else error(method.name)
        } as PermissionController
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader, arrayOf(HealthConnectClient::class.java)) { _, method, _ ->
            when (method.name) { "getPermissionController" -> permission; "readRecords" -> ReadRecordsResponse(existing, null); else -> error(method.name) }
        } as HealthConnectClient
        val gateway = HealthConnectTakeoutGateway(client, "pl.healthsync", plans)
        val candidate = takeoutPayloadCandidate(late)
        assertEquals(TakeoutWriteCheck.Missing, gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId))
        existing = listOf(stored(early, "another.app"))
        assertTrue(gateway.inspectBatch(listOf(candidate)).getValue(candidate.clientId) is TakeoutWriteCheck.Blocked)
    }
}
