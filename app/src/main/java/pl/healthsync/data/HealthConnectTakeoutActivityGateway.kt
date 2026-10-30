package pl.healthsync.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import pl.healthsync.domain.*
import java.time.Instant
import kotlin.reflect.KClass

internal fun activityWritePermissions(kinds: Set<TakeoutActivityKind>): Set<String> = buildSet {
    addAll(activityReadPermissions(kinds))
    if (TakeoutActivityKind.STEPS in kinds) add(HealthPermission.getWritePermission(StepsRecord::class))
    if (TakeoutActivityKind.DISTANCE in kinds) add(HealthPermission.getWritePermission(DistanceRecord::class))
    if (TakeoutActivityKind.TOTAL_CALORIES in kinds) add(HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class))
    if (TakeoutActivityKind.ACTIVE_CALORIES in kinds) add(HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class))
}

private fun activityKind(record: Record): TakeoutActivityKind = when (record) {
    is StepsRecord -> TakeoutActivityKind.STEPS
    is DistanceRecord -> TakeoutActivityKind.DISTANCE
    is TotalCaloriesBurnedRecord -> TakeoutActivityKind.TOTAL_CALORIES
    is ActiveCaloriesBurnedRecord -> TakeoutActivityKind.ACTIVE_CALORIES
    else -> error("Nieobsługiwany typ aktywności")
}
private fun activityBounds(record: Record): Pair<Instant, Instant> = when (record) {
    is StepsRecord -> record.startTime to record.endTime
    is DistanceRecord -> record.startTime to record.endTime
    is TotalCaloriesBurnedRecord -> record.startTime to record.endTime
    is ActiveCaloriesBurnedRecord -> record.startTime to record.endTime
    else -> error("Nieobsługiwany typ aktywności")
}

/** Every day is rechecked against all sources before and after insertion. */
internal class HealthConnectTakeoutActivityGateway(
    private val client: HealthConnectClient,
    private val ownPackage: String,
    plan: List<TakeoutPlannedRecord>,
) : TakeoutBatchWriteGateway, TakeoutUndoGateway {
    private val records = plan.associateBy { requireNotNull(it.record.metadata.clientRecordId) }
    init { require(records.size == plan.size) }

    private suspend fun read(kind: TakeoutActivityKind, keep: (Record) -> Boolean): List<Record> {
        suspend fun <T : Record> all(type: KClass<T>): List<Record> {
            val result = mutableListOf<Record>()
            var token: String? = null
            var readCount = 0
            do {
                val page = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.after(Instant.parse("1900-01-01T00:00:00Z")), pageToken = token))
                readCount += page.records.size
                check(readCount <= 1_000_000) { "Zbyt wiele wpisów Connect do bezpiecznego porównania" }
                result.addAll(page.records.filter(keep))
                token = page.pageToken
            } while (!token.isNullOrEmpty())
            return result
        }
        return when (kind) {
            TakeoutActivityKind.STEPS -> all(StepsRecord::class)
            TakeoutActivityKind.DISTANCE -> all(DistanceRecord::class)
            TakeoutActivityKind.TOTAL_CALORIES -> all(TotalCaloriesBurnedRecord::class)
            TakeoutActivityKind.ACTIVE_CALORIES -> all(ActiveCaloriesBurnedRecord::class)
        }
    }

    override suspend fun inspectBatch(candidates: List<TakeoutWriteCandidate>): Map<String, TakeoutWriteCheck> {
        require(candidates.size <= 1000 && candidates.map { it.clientId }.distinct().size == candidates.size)
        val items = candidates.map { candidate ->
            val item = records[candidate.clientId] ?: error("Brak rekordu aktywności w planie")
            check(takeoutPayloadCandidate(item.record) == candidate) { "Zawartość planu uległa zmianie" }
            candidate to item
        }
        val result = mutableMapOf<String, TakeoutWriteCheck>()
        for ((kind, group) in items.groupBy { activityKind(it.second.record) }) {
            check(client.permissionController.getGrantedPermissions().containsAll(activityWritePermissions(setOf(kind)))) { "Brak wymaganej zgody Health Connect" }
            val windows = group.map { activityBounds(it.second.record) }
            val existing = read(kind) { record ->
                val (a, b) = activityBounds(record)
                windows.any { (start, end) -> a < end && start < b }
            }
            for ((candidate, item) in group) {
                val (start, end) = activityBounds(item.record)
                val overlaps = existing.filter { val (a, b) = activityBounds(it); a < end && start < b }
                val own = overlaps.filter { it.metadata.dataOrigin.packageName == ownPackage && it.metadata.clientRecordId == candidate.clientId }
                result[candidate.clientId] = when {
                    overlaps.size != own.size || own.size > 1 -> TakeoutWriteCheck.Blocked("W Connect są już dane tego typu w tym dniu")
                    own.isEmpty() -> TakeoutWriteCheck.Missing
                    takeoutPayloadCandidate(own.single()) != candidate -> TakeoutWriteCheck.Blocked("Własny rekord aktywności został zmieniony")
                    else -> own.single().let { TakeoutWriteCheck.AlreadyWritten(TakeoutWriteReceipt(it.metadata.id, it.metadata.lastModifiedTime.toString())) }
                }
            }
        }
        return result
    }

    override suspend fun insertBatch(candidates: List<TakeoutWriteCandidate>) {
        require(candidates.isNotEmpty() && candidates.size <= 1000)
        check(inspectBatch(candidates).values.all { it == TakeoutWriteCheck.Missing }) { "Dane w Connect zmieniły się podczas importu" }
        client.insertRecords(candidates.map { records.getValue(it.clientId).record })
    }

    override suspend fun inspect(entry: TakeoutUndoEntry): TakeoutUndoCheck {
        val item = records[entry.candidate.clientId] ?: return TakeoutUndoCheck.Blocked("Brak pierwotnego rekordu")
        if (takeoutPayloadCandidate(item.record) != entry.candidate) return TakeoutUndoCheck.Blocked("Plan importu uległ zmianie")
        val kind = activityKind(item.record)
        check(client.permissionController.getGrantedPermissions().containsAll(activityWritePermissions(setOf(kind)))) { "Brak wymaganej zgody Health Connect" }
        val matches = read(kind) { it.metadata.id == entry.receipt.recordId }
        if (matches.isEmpty()) return TakeoutUndoCheck.Missing
        if (matches.size != 1) return TakeoutUndoCheck.Blocked("Niejednoznaczny identyfikator")
        val row = matches.single()
        if (row.metadata.dataOrigin.packageName != ownPackage || row.metadata.clientRecordId != entry.candidate.clientId ||
            row.metadata.lastModifiedTime.toString() != entry.receipt.lastModified || takeoutPayloadCandidate(row) != entry.candidate) return TakeoutUndoCheck.Blocked("Rekord został zmieniony lub nie należy do Health Sync")
        return TakeoutUndoCheck.Unchanged
    }

    override suspend fun delete(entry: TakeoutUndoEntry) {
        check(inspect(entry) == TakeoutUndoCheck.Unchanged) { "Rekord zmienił się przed wycofaniem" }
        val type: KClass<out Record> = when (activityKind(records.getValue(entry.candidate.clientId).record)) {
            TakeoutActivityKind.STEPS -> StepsRecord::class
            TakeoutActivityKind.DISTANCE -> DistanceRecord::class
            TakeoutActivityKind.TOTAL_CALORIES -> TotalCaloriesBurnedRecord::class
            TakeoutActivityKind.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord::class
        }
        client.deleteRecords(type, listOf(entry.receipt.recordId), emptyList())
    }
}
