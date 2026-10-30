package pl.healthsync.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import pl.healthsync.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

internal enum class ActivityPreviewStatus { PRESENT_IN_CONNECT, AMBIGUOUS, ESTIMATED }
internal data class ActivityPreviewDay(val date: LocalDate, val status: ActivityPreviewStatus, val estimate: TakeoutActivityEstimate)

internal fun activityReadPermissions(kinds: Set<TakeoutActivityKind>): Set<String> = buildSet {
    add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
    if (TakeoutActivityKind.STEPS in kinds) add(HealthPermission.getReadPermission(StepsRecord::class))
    if (TakeoutActivityKind.DISTANCE in kinds) add(HealthPermission.getReadPermission(DistanceRecord::class))
    if (TakeoutActivityKind.TOTAL_CALORIES in kinds) add(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
    if (TakeoutActivityKind.ACTIVE_CALORIES in kinds) add(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
}

internal class TakeoutActivityInspector(private val client: HealthConnectClient) {
    suspend fun inspect(estimates: Map<TakeoutActivityKind, Map<LocalDate, TakeoutActivityEstimate>>, start: LocalDate, end: LocalDate): Map<TakeoutActivityKind, List<ActivityPreviewDay>> {
        val kinds = estimates.keys
        if (kinds.isEmpty()) return emptyMap()
        check(client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) { "Nie można odczytać starszej historii Health Connect" }
        check(client.permissionController.getGrantedPermissions().containsAll(activityReadPermissions(kinds))) { "Brakuje zgody na odczyt aktywności lub starszej historii" }
        val zone = ZoneId.of("Europe/Warsaw")
        val result = mutableMapOf<TakeoutActivityKind, List<ActivityPreviewDay>>()
        for ((kind, values) in estimates) {
            val present = mutableSetOf<LocalDate>()
            suspend fun <T : Record> collect(type: KClass<T>, times: (T) -> Pair<Instant, Instant>) {
                // Read earlier history too, so an unusually long record crossing the first day is not missed.
                val filter = TimeRangeFilter.between(Instant.parse("1900-01-01T00:00:00Z"), end.plusDays(1).atStartOfDay(zone).toInstant())
                var token: String? = null
                var count = 0
                do {
                    val page = client.readRecords(ReadRecordsRequest(type, filter, pageToken = token))
                    for (record in page.records) {
                        check(++count <= 1_000_000) { "Zbyt wiele wpisów Connect do bezpiecznego porównania" }
                        val (recordStart, recordEnd) = times(record)
                        var first = maxOf(start, recordStart.atZone(zone).toLocalDate())
                        val last = minOf(end, recordEnd.minusNanos(1).atZone(zone).toLocalDate())
                        while (first <= last) { present.add(first); first = first.plusDays(1) }
                    }
                    token = page.pageToken
                } while (!token.isNullOrEmpty())
            }
            when (kind) {
                TakeoutActivityKind.STEPS -> collect(StepsRecord::class) { it.startTime to it.endTime }
                TakeoutActivityKind.DISTANCE -> collect(DistanceRecord::class) { it.startTime to it.endTime }
                TakeoutActivityKind.TOTAL_CALORIES -> collect(TotalCaloriesBurnedRecord::class) { it.startTime to it.endTime }
                TakeoutActivityKind.ACTIVE_CALORIES -> collect(ActiveCaloriesBurnedRecord::class) { it.startTime to it.endTime }
            }
            result[kind] = values.map { (date, estimate) -> ActivityPreviewDay(date,
                if (date in present) ActivityPreviewStatus.PRESENT_IN_CONNECT else if (estimate.value == null || estimate.value <= 0) ActivityPreviewStatus.AMBIGUOUS else ActivityPreviewStatus.ESTIMATED, estimate)
            }.sortedBy { it.date }
        }
        return result
    }
}
