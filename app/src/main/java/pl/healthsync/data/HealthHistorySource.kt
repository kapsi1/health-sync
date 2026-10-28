package pl.healthsync.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import pl.healthsync.BodyFatSample
import pl.healthsync.MetricRegistry
import pl.healthsync.SleepInterval
import pl.healthsync.latestBodyFatByDay
import pl.healthsync.sleepByEndDate
import pl.healthsync.domain.DateChunk
import pl.healthsync.domain.HistorySource
import pl.healthsync.domain.ImportedValue
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

private val zone = ZoneId.of("Europe/Warsaw")

class HealthHistorySource(private val client: HealthConnectClient) : HistorySource {
    override suspend fun read(metricId: String, range: DateChunk): List<ImportedValue> {
        val metric = MetricRegistry.all.single { it.id == metricId }
        val granted = client.permissionController.getGrantedPermissions()
        check(metric.permission in granted) { "Brak zgody na odczyt: ${metric.label}" }
        val earliestRead = if (metricId == "sleep_duration") range.start.minusDays(7) else range.start
        if (earliestRead < LocalDate.now(zone).minusDays(28)) {
            check(client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE &&
                HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted) { "Brak dostępu do starszej historii" }
        }
        return when (metricId) {
            "body_fat" -> {
                val rows = readAll(BodyFatRecord::class, range.start, range.endExclusive)
                latestBodyFatByDay(rows.map { BodyFatSample(it.time, it.metadata.id, it.percentage.value) })
                    .map { (date, sample) -> ImportedValue(date, sample.value) }
            }
            "sleep_duration" -> {
                val rows = readAll(SleepSessionRecord::class, range.start.minusDays(7), range.endExclusive)
                val days = sleepByEndDate(rows.map { SleepInterval(it.startTime, it.endTime) })
                    .filterKeys { it >= range.start && it < range.endExclusive }
                check(days.values.none { it.overlapping }) { "Nakładające się sesje snu wymagają rozstrzygnięcia" }
                days.map { (date, day) -> ImportedValue(date, day.duration!!.toMillis() / 3_600_000.0) }
            }
            else -> {
                val aggregate = aggregateFor(metricId)
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(setOf(aggregate), TimeRangeFilter.between(range.start.atStartOfDay(), range.endExclusive.atStartOfDay()), Period.ofDays(1))
                ).mapNotNull { row ->
                    numberFor(metricId, row.result)?.let { ImportedValue(row.startTime.toLocalDate(), it) }
                }
            }
        }
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, start: LocalDate, end: LocalDate): List<T> {
        val rows = mutableListOf<T>()
        var token: String? = null
        val filter = TimeRangeFilter.between(start.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant())
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageToken = token))
            rows.addAll(response.records)
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return rows
    }
}

private fun aggregateFor(id: String): AggregateMetric<*> = when (id) {
    "steps" -> StepsRecord.COUNT_TOTAL
    "total_calories" -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
    "active_calories" -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
    "distance" -> DistanceRecord.DISTANCE_TOTAL
    "heart_rate_avg" -> HeartRateRecord.BPM_AVG
    "resting_heart_rate" -> RestingHeartRateRecord.BPM_AVG
    "weight_avg" -> WeightRecord.WEIGHT_AVG
    else -> error("Nieobsługiwana metryka")
}

private fun numberFor(id: String, result: AggregationResult): Double? = when (id) {
    "steps" -> result[StepsRecord.COUNT_TOTAL]?.toDouble()
    "total_calories" -> result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
    "active_calories" -> result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    "distance" -> result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
    "heart_rate_avg" -> result[HeartRateRecord.BPM_AVG]?.toDouble()
    "resting_heart_rate" -> result[RestingHeartRateRecord.BPM_AVG]?.toDouble()
    "weight_avg" -> result[WeightRecord.WEIGHT_AVG]?.inKilograms
    else -> null
}
