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
import pl.healthsync.sleepMetricIds
import pl.healthsync.toSleepInterval
import pl.healthsync.averageSamplesByDay
import pl.healthsync.sleepByEndDate
import pl.healthsync.domain.DateChunk
import pl.healthsync.domain.HistorySource
import pl.healthsync.domain.ImportedValue
import pl.healthsync.domain.SleepBreathingInterval
import pl.healthsync.domain.SleepBreathingSession
import pl.healthsync.domain.BreathingSample
import pl.healthsync.domain.breathingDuringSleep
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

private val zone = ZoneId.of("Europe/Warsaw")

class HealthHistorySource(private val client: HealthConnectClient) : HistorySource {
    override suspend fun read(metricId: String, range: DateChunk): List<ImportedValue> {
        val metric = MetricRegistry.all.single { it.id == metricId }
        val granted = client.permissionController.getGrantedPermissions()
        check(granted.containsAll(pl.healthsync.requiredMetricPermissions(metric))) { "Brak zgody na odczyt: ${metric.label}" }
        val earliestRead = if (metricId in sleepMetricIds || metricId == "respiratory_rate_sleep") range.start.minusDays(7) else range.start
        if (earliestRead < LocalDate.now(zone).minusDays(28)) {
            check(client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE &&
                HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted) { "Brak dostępu do starszej historii" }
        }
        return when (metricId) {
            "body_fat" -> {
                average(BodyFatRecord::class, range) { BodyFatSample(it.time, it.metadata.id, it.percentage.value) }
            }
            "hrv_rmssd" -> average(HeartRateVariabilityRmssdRecord::class, range) { BodyFatSample(it.time, it.metadata.id, it.heartRateVariabilityMillis) }
            "oxygen_saturation" -> average(OxygenSaturationRecord::class, range) { BodyFatSample(it.time, it.metadata.id, it.percentage.value) }
            "respiratory_rate_sleep" -> {
                val sessions = readAll(SleepSessionRecord::class, range.start.minusDays(7), range.endExclusive).filter { it.endTime.atZone(zone).toLocalDate() in range.start..<range.endExclusive }
                val sleeps = sessions.map { row -> SleepBreathingSession(row.endTime, row.stages.filter { it.stage in setOf(
                    SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM
                ) }.map { SleepBreathingInterval(it.startTime, it.endTime) }) }
                val samples = readAll(RespiratoryRateRecord::class, range.start.minusDays(7), range.endExclusive).map { BreathingSample(it.time, it.rate, it.metadata.dataOrigin.packageName) }
                breathingDuringSleep(sleeps, samples, zone).filterKeys { it >= range.start && it < range.endExclusive }.map { (date, value) -> ImportedValue(date, value) }
            }
            "skin_temperature_delta" -> {
                check(client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) { "Health Connect nie obsługuje temperatury skóry" }
                client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(SkinTemperatureRecord.TEMPERATURE_DELTA_AVG),
                    TimeRangeFilter.between(range.start.atStartOfDay(), range.endExclusive.atStartOfDay()), Period.ofDays(1)))
                    .mapNotNull { row -> row.result[SkinTemperatureRecord.TEMPERATURE_DELTA_AVG]?.let { ImportedValue(row.startTime.toLocalDate(), it.inCelsius) } }
            }
            "sleep_duration", "sleep_awake" -> {
                val rows = readAll(SleepSessionRecord::class, range.start.minusDays(7), range.endExclusive)
                val days = sleepByEndDate(rows.map { it.toSleepInterval() })
                    .filterKeys { it >= range.start && it < range.endExclusive }
                check(days.values.none { it.overlapping }) { "Nakładające się sesje snu wymagają rozstrzygnięcia" }
                days.mapNotNull { (date, day) ->
                    val duration = if (metricId == "sleep_awake") day.awakeDuration else day.duration
                    duration?.let { ImportedValue(date, it.toMillis() / 3_600_000.0) }
                }
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

    private suspend fun <T : Record> average(type: KClass<T>, range: DateChunk, sample: (T) -> BodyFatSample): List<ImportedValue> {
        val rows = readAll(type, range.start, range.endExclusive)
        return averageSamplesByDay(rows.map(sample))
            .map { (date, value) -> ImportedValue(date, value) }
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
    "exercise_duration" -> ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
    "resting_heart_rate" -> RestingHeartRateRecord.BPM_AVG
    "weight_avg" -> WeightRecord.WEIGHT_AVG
    else -> error("Nieobsługiwana metryka")
}

private fun numberFor(id: String, result: AggregationResult): Double? = when (id) {
    "steps" -> result[StepsRecord.COUNT_TOTAL]?.toDouble()
    "total_calories" -> result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
    "active_calories" -> result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
    "distance" -> result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
    "exercise_duration" -> result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMillis()?.div(3_600_000.0)
    "resting_heart_rate" -> result[RestingHeartRateRecord.BPM_AVG]?.toDouble()
    "weight_avg" -> result[WeightRecord.WEIGHT_AVG]?.inKilograms
    else -> null
}
