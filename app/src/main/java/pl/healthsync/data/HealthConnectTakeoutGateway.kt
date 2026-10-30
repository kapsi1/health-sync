package pl.healthsync.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import pl.healthsync.domain.TakeoutWriteCandidate
import pl.healthsync.domain.TakeoutWriteCheck
import pl.healthsync.domain.TakeoutWriteGateway
import pl.healthsync.domain.TakeoutBatchWriteGateway
import pl.healthsync.domain.TakeoutWriteReceipt
import pl.healthsync.domain.TakeoutUndoEntry
import pl.healthsync.domain.TakeoutUndoGateway
import pl.healthsync.domain.TakeoutUndoCheck
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import kotlin.reflect.KClass

internal data class TakeoutPlannedRecord(
    val record: Record,
    /** Union of source intervals to guard against other versions of the same night. */
    val sleepSourceStart: Instant? = null,
    val sleepSourceEnd: Instant? = null,
)

internal fun takeoutRecordTime(record: Record): Instant = when (record) {
    is WeightRecord -> record.time
    is BodyFatRecord -> record.time
    is HeightRecord -> record.time
    is RestingHeartRateRecord -> record.time
    is SleepSessionRecord -> record.startTime
    is StepsRecord -> record.startTime
    is DistanceRecord -> record.startTime
    is TotalCaloriesBurnedRecord -> record.startTime
    is ActiveCaloriesBurnedRecord -> record.startTime
    else -> error("Nieobsługiwany rekord Takeout")
}

/** Hashes the full payload; does not include Health Connect-generated IDs or timestamps. */
internal fun takeoutPayloadCandidate(record: Record): TakeoutWriteCandidate {
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
    }
    val id = requireNotNull(record.metadata.clientRecordId)
    add(id)
    add(record.metadata.clientRecordVersion.toString())
    val kind = when (record) {
        is WeightRecord -> {
            add(record.time.toString()); add(record.zoneOffset?.id ?: "")
            add(java.lang.Double.toHexString(record.weight.inKilograms)); "weight"
        }
        is BodyFatRecord -> {
            add(record.time.toString()); add(record.zoneOffset?.id ?: "")
            add(java.lang.Double.toHexString(record.percentage.value)); "body_fat"
        }
        is HeightRecord -> {
            add(record.time.toString()); add(record.zoneOffset?.id ?: "")
            add(java.lang.Double.toHexString(record.height.inMeters)); "height"
        }
        is RestingHeartRateRecord -> { add(record.time.toString()); add(record.zoneOffset?.id ?: ""); add(record.beatsPerMinute.toString()); "resting_heart_rate" }
        is SleepSessionRecord -> {
            add(record.startTime.toString()); add(record.endTime.toString())
            add(record.startZoneOffset?.id ?: ""); add(record.endZoneOffset?.id ?: "")
            add(record.title ?: ""); add(record.notes ?: "")
            val stages = record.stages.sortedBy { it.startTime }
            add(stages.size.toString())
            stages.forEach { add(it.startTime.toString()); add(it.endTime.toString()); add(it.stage.toString()) }
            "sleep"
        }
        is StepsRecord -> { add(record.startTime.toString()); add(record.endTime.toString()); add(record.startZoneOffset?.id ?: ""); add(record.endZoneOffset?.id ?: ""); add(record.count.toString()); "steps" }
        is DistanceRecord -> { add(record.startTime.toString()); add(record.endTime.toString()); add(record.startZoneOffset?.id ?: ""); add(record.endZoneOffset?.id ?: ""); add(java.lang.Double.toHexString(record.distance.inMeters)); "distance" }
        is TotalCaloriesBurnedRecord -> { add(record.startTime.toString()); add(record.endTime.toString()); add(record.startZoneOffset?.id ?: ""); add(record.endZoneOffset?.id ?: ""); add(java.lang.Double.toHexString(record.energy.inKilocalories)); "total_calories" }
        is ActiveCaloriesBurnedRecord -> { add(record.startTime.toString()); add(record.endTime.toString()); add(record.startZoneOffset?.id ?: ""); add(record.endZoneOffset?.id ?: ""); add(java.lang.Double.toHexString(record.energy.inKilocalories)); "active_calories" }
        else -> error("Nieobsługiwany rekord Takeout")
    }
    add(kind)
    return TakeoutWriteCandidate(id, kind, digest.digest().joinToString("") { "%02x".format(it) })
}

/** Connect may fill a missing offset with the local offset at the measurement instant. */
internal fun takeoutPayloadMatches(expected: Record, actual: Record): Boolean {
    if (takeoutPayloadCandidate(expected) == takeoutPayloadCandidate(actual)) return true
    if (expected is RestingHeartRateRecord && actual is RestingHeartRateRecord && expected.zoneOffset == null &&
        actual.zoneOffset == java.time.ZoneId.of("Europe/Warsaw").rules.getOffset(expected.time)) {
        val normalized = RestingHeartRateRecord(time = actual.time, zoneOffset = null,
            beatsPerMinute = actual.beatsPerMinute, metadata = actual.metadata)
        return takeoutPayloadCandidate(expected) == takeoutPayloadCandidate(normalized)
    }
    return false
}

/** Only created after archive origin and candidate selection have already been checked. */
internal class HealthConnectTakeoutGateway(
    private val client: HealthConnectClient,
    private val ownPackage: String,
    planned: List<TakeoutPlannedRecord>,
) : TakeoutWriteGateway, TakeoutBatchWriteGateway, TakeoutUndoGateway {
    private val records = planned.associateBy { requireNotNull(it.record.metadata.clientRecordId) }
    init {
        require(records.size == planned.size)
        planned.forEach { item ->
            if (item.record is SleepSessionRecord) {
                require(item.sleepSourceStart != null && item.sleepSourceEnd != null && item.sleepSourceStart < item.sleepSourceEnd)
                require(item.sleepSourceStart <= item.record.startTime && item.sleepSourceEnd >= item.record.endTime)
            }
        }
    }

    /** A verified sibling v2 from the same source group is not a conflicting v1. */
    private fun isKnownSleepSibling(item: TakeoutPlannedRecord, existing: SleepSessionRecord): Boolean {
        val row = item.record as? SleepSessionRecord ?: return false
        val id = existing.metadata.clientRecordId ?: return false
        if (existing.metadata.dataOrigin.packageName != ownPackage || id == row.metadata.clientRecordId) return false
        val sibling = records[id] ?: return false
        val planned = sibling.record as? SleepSessionRecord ?: return false
        if (sibling.sleepSourceStart != item.sleepSourceStart || sibling.sleepSourceEnd != item.sleepSourceEnd) return false
        if (existing.startTime < row.endTime && row.startTime < existing.endTime) return false
        return takeoutPayloadCandidate(existing) == takeoutPayloadCandidate(planned)
    }

    override suspend fun inspect(candidate: TakeoutWriteCandidate): TakeoutWriteCheck {
        val planned = records[candidate.clientId] ?: return TakeoutWriteCheck.Blocked("Brak danych zaplanowanego rekordu")
        check(takeoutPayloadCandidate(planned.record) == candidate) { "Zawartość rekordu uległa zmianie" }
        val type = when (planned.record) {
            is WeightRecord -> TakeoutType.WEIGHT
            is BodyFatRecord -> TakeoutType.FAT
            is HeightRecord -> TakeoutType.HEIGHT
            is RestingHeartRateRecord -> TakeoutType.RESTING_HEART_RATE
            is SleepSessionRecord -> TakeoutType.SLEEP
            else -> error("Nieobsługiwany rekord")
        }
        val write = when (type) {
            TakeoutType.WEIGHT -> HealthPermission.getWritePermission(WeightRecord::class)
            TakeoutType.FAT -> HealthPermission.getWritePermission(BodyFatRecord::class)
            TakeoutType.HEIGHT -> HealthPermission.getWritePermission(HeightRecord::class)
            TakeoutType.RESTING_HEART_RATE -> HealthPermission.getWritePermission(RestingHeartRateRecord::class)
            TakeoutType.SLEEP -> HealthPermission.getWritePermission(SleepSessionRecord::class)
        }
        check(client.permissionController.getGrantedPermissions().containsAll(takeoutReadPermissions(setOf(type)) + write)) { "Brak wymaganej zgody Health Connect" }
        val existing: List<Record> = when (val row = planned.record) {
            is WeightRecord -> readAll(WeightRecord::class, TimeRangeFilter.between(row.time.minusSeconds(1), row.time.plusSeconds(1))).filter { it.time == row.time }
            is BodyFatRecord -> readAll(BodyFatRecord::class, TimeRangeFilter.between(row.time.minusSeconds(1), row.time.plusSeconds(1))).filter { it.time == row.time }
            is HeightRecord -> readAll(HeightRecord::class, TimeRangeFilter.between(row.time.minusSeconds(1), row.time.plusSeconds(1))).filter { it.time == row.time }
            is RestingHeartRateRecord -> {
                val zone = java.time.ZoneId.of("Europe/Warsaw")
                val day = row.time.atZone(zone).toLocalDate()
                readAll(RestingHeartRateRecord::class, TimeRangeFilter.between(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()))
                    .filter { it.time.atZone(zone).toLocalDate() == day }
            }
            is SleepSessionRecord -> readAll(SleepSessionRecord::class, TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z"))).filter {
                it.startTime < planned.sleepSourceEnd!! && planned.sleepSourceStart!! < it.endTime && !isKnownSleepSibling(planned, it)
            }
            else -> error("Nieobsługiwany rekord")
        }
        val owned = existing.filter { it.metadata.dataOrigin.packageName == ownPackage && it.metadata.clientRecordId == candidate.clientId }
        if (existing.size > owned.size || owned.size > 1) return TakeoutWriteCheck.Blocked("W Health Connect pojawiły się inne nakładające się dane")
        val match = owned.singleOrNull() ?: return TakeoutWriteCheck.Missing
        if (!takeoutPayloadMatches(planned.record, match)) return TakeoutWriteCheck.Blocked("Poprzednio zapisany rekord został zmieniony")
        return TakeoutWriteCheck.AlreadyWritten(TakeoutWriteReceipt(match.metadata.id, match.metadata.lastModifiedTime.toString()))
    }

    override suspend fun insert(candidate: TakeoutWriteCandidate) {
        val item = records[candidate.clientId] ?: error("Brak danych zaplanowanego rekordu")
        check(takeoutPayloadCandidate(item.record) == candidate) { "Zawartość rekordu uległa zmianie" }
        // Recheck immediately before insertion; Health Connect has no cross-app transactional lock.
        check(inspect(candidate) == TakeoutWriteCheck.Missing) { "Dane w Health Connect zmieniły się podczas importu" }
        client.insertRecords(listOf(item.record))
    }

    override suspend fun inspectBatch(candidates: List<TakeoutWriteCandidate>): Map<String, TakeoutWriteCheck> {
        require(candidates.size <= 1000 && candidates.map { it.clientId }.distinct().size == candidates.size)
        val mapped = candidates.map { candidate ->
            val plan = records[candidate.clientId] ?: error("Brak danych zaplanowanego rekordu")
            check(takeoutPayloadCandidate(plan.record) == candidate) { "Zawartość rekordu uległa zmianie" }
            candidate to plan
        }
        val result = mutableMapOf<String, TakeoutWriteCheck>()
        for ((kind, group) in mapped.groupBy { it.first.kind }) {
            val type = when (kind) {
                "weight" -> TakeoutType.WEIGHT
                "body_fat" -> TakeoutType.FAT
                "height" -> TakeoutType.HEIGHT
                "resting_heart_rate" -> TakeoutType.RESTING_HEART_RATE
                "sleep" -> TakeoutType.SLEEP
                else -> error("Nieobsługiwany typ rekordu")
            }
            val write = when (type) {
                TakeoutType.WEIGHT -> HealthPermission.getWritePermission(WeightRecord::class)
                TakeoutType.FAT -> HealthPermission.getWritePermission(BodyFatRecord::class)
                TakeoutType.HEIGHT -> HealthPermission.getWritePermission(HeightRecord::class)
                TakeoutType.RESTING_HEART_RATE -> HealthPermission.getWritePermission(RestingHeartRateRecord::class)
                TakeoutType.SLEEP -> HealthPermission.getWritePermission(SleepSessionRecord::class)
            }
            check(client.permissionController.getGrantedPermissions().containsAll(takeoutReadPermissions(setOf(type)) + write)) { "Brak wymaganej zgody Health Connect" }
            val filter = if (type == TakeoutType.SLEEP) TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z")) else if (type == TakeoutType.RESTING_HEART_RATE) {
                val zone = java.time.ZoneId.of("Europe/Warsaw")
                val dates = group.map { (_, item) -> takeoutRecordTime(item.record).atZone(zone).toLocalDate() }
                TimeRangeFilter.between(dates.min().atStartOfDay(zone).toInstant(), dates.max().plusDays(1).atStartOfDay(zone).toInstant())
            } else {
                val times = group.map { (_, item) -> takeoutRecordTime(item.record) }
                TimeRangeFilter.between(times.min().minusSeconds(1), times.max().plusSeconds(1))
            }
            val existing: List<Record> = when (type) {
                TakeoutType.WEIGHT -> readAll(WeightRecord::class, filter)
                TakeoutType.FAT -> readAll(BodyFatRecord::class, filter)
                TakeoutType.HEIGHT -> readAll(HeightRecord::class, filter)
                TakeoutType.RESTING_HEART_RATE -> readAll(RestingHeartRateRecord::class, filter)
                TakeoutType.SLEEP -> readAll(SleepSessionRecord::class, filter)
            }
            val byTime = if (type == TakeoutType.SLEEP) emptyMap() else existing.groupBy(::takeoutRecordTime)
            val byDay = if (type == TakeoutType.RESTING_HEART_RATE) existing.groupBy { takeoutRecordTime(it).atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate() } else emptyMap()
            for ((candidate, item) in group) {
                val relevant: List<Record> = when (val row = item.record) {
                    is WeightRecord, is BodyFatRecord, is HeightRecord -> byTime[takeoutRecordTime(row)].orEmpty()
                    is RestingHeartRateRecord -> byDay[row.time.atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate()].orEmpty()
                    is SleepSessionRecord -> existing.filterIsInstance<SleepSessionRecord>().filter {
                        it.startTime < item.sleepSourceEnd!! && item.sleepSourceStart!! < it.endTime && !isKnownSleepSibling(item, it)
                    }
                    else -> error("Nieobsługiwany rekord")
                }
                val owned = relevant.filter { it.metadata.dataOrigin.packageName == ownPackage && it.metadata.clientRecordId == candidate.clientId }
                result[candidate.clientId] = when {
                    relevant.size > owned.size || owned.size > 1 -> TakeoutWriteCheck.Blocked("W Health Connect pojawiły się inne nakładające się dane")
                    owned.isEmpty() -> TakeoutWriteCheck.Missing
                    !takeoutPayloadMatches(item.record, owned.single()) -> TakeoutWriteCheck.Blocked("Poprzednio zapisany rekord został zmieniony")
                    else -> owned.single().let { TakeoutWriteCheck.AlreadyWritten(TakeoutWriteReceipt(it.metadata.id, it.metadata.lastModifiedTime.toString())) }
                }
            }
        }
        return result
    }

    override suspend fun insertBatch(candidates: List<TakeoutWriteCandidate>) {
        require(candidates.isNotEmpty() && candidates.size <= 1000)
        val checked = inspectBatch(candidates)
        check(checked.values.all { it == TakeoutWriteCheck.Missing }) { "Dane w Health Connect zmieniły się podczas importu" }
        client.insertRecords(candidates.map { records.getValue(it.clientId).record })
    }

    override suspend fun inspect(entry: TakeoutUndoEntry): TakeoutUndoCheck {
        val planned = records[entry.candidate.clientId] ?: return TakeoutUndoCheck.Blocked("Brak pierwotnego rekordu do porównania")
        if (takeoutPayloadCandidate(planned.record) != entry.candidate) return TakeoutUndoCheck.Blocked("Zawartość planu uległa zmianie")
        val permissionPair = when (planned.record) {
            is WeightRecord -> TakeoutType.WEIGHT to HealthPermission.getWritePermission(WeightRecord::class)
            is BodyFatRecord -> TakeoutType.FAT to HealthPermission.getWritePermission(BodyFatRecord::class)
            is HeightRecord -> TakeoutType.HEIGHT to HealthPermission.getWritePermission(HeightRecord::class)
            is RestingHeartRateRecord -> TakeoutType.RESTING_HEART_RATE to HealthPermission.getWritePermission(RestingHeartRateRecord::class)
            is SleepSessionRecord -> TakeoutType.SLEEP to HealthPermission.getWritePermission(SleepSessionRecord::class)
            else -> return TakeoutUndoCheck.Blocked("Nieobsługiwany typ rekordu")
        }
        check(client.permissionController.getGrantedPermissions().containsAll(takeoutReadPermissions(setOf(permissionPair.first)) + permissionPair.second)) { "Brak wymaganej zgody Health Connect" }
        // Search the full history: a changed record may have moved outside its original time window.
        val all: List<Record> = when (planned.record) {
            is WeightRecord -> readAll(WeightRecord::class, TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z")))
            is BodyFatRecord -> readAll(BodyFatRecord::class, TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z")))
            is HeightRecord -> readAll(HeightRecord::class, TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z")))
            is RestingHeartRateRecord -> readAll(RestingHeartRateRecord::class, TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z")))
            is SleepSessionRecord -> readAll(SleepSessionRecord::class, TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z")))
            else -> error("Nieobsługiwany rekord")
        }
        val matches = all.filter { it.metadata.id == entry.receipt.recordId }
        if (matches.isEmpty()) return TakeoutUndoCheck.Missing
        if (matches.size != 1) return TakeoutUndoCheck.Blocked("Niejednoznaczny identyfikator rekordu")
        val record = matches.single()
        if (record.metadata.dataOrigin.packageName != ownPackage || record.metadata.clientRecordId != entry.candidate.clientId ||
            record.metadata.lastModifiedTime.toString() != entry.receipt.lastModified || !takeoutPayloadMatches(planned.record, record)) {
            return TakeoutUndoCheck.Blocked("Rekord został zmieniony lub nie należy do Health Sync")
        }
        return TakeoutUndoCheck.Unchanged
    }

    override suspend fun delete(entry: TakeoutUndoEntry) {
        check(inspect(entry) == TakeoutUndoCheck.Unchanged) { "Rekord zmienił się przed wycofaniem" }
        val type: KClass<out Record> = when (records.getValue(entry.candidate.clientId).record) {
            is WeightRecord -> WeightRecord::class
            is BodyFatRecord -> BodyFatRecord::class
            is HeightRecord -> HeightRecord::class
            is RestingHeartRateRecord -> RestingHeartRateRecord::class
            is SleepSessionRecord -> SleepSessionRecord::class
            else -> error("Nieobsługiwany typ rekordu")
        }
        client.deleteRecords(type, listOf(entry.receipt.recordId), emptyList())
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val result = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, filter, pageToken = token))
            result.addAll(page.records)
            check(result.size <= 100_000) { "Zbyt dużo rekordów do bezpiecznego porównania" }
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return result
    }
}
