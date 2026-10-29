package pl.healthsync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import pl.healthsync.data.HealthHistorySource
import pl.healthsync.domain.DateChunk
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** A temporary read-only preview, not the durable history importer. */
class DailyPreview(private val client: HealthConnectClient) {
    suspend fun read(selected: Set<String>, granted: Set<String>): String {
        val end = LocalDate.now(ZoneId.of("Europe/Warsaw")).plusDays(1)
        val start = end.minusDays(7)
        val lines = mutableListOf("Ostatnie 7 dni: $start – ${end.minusDays(1)}. Wyniki są podglądem i nie są zapisywane.")
        for (metric in MetricRegistry.all.filter { it.id in selected }) {
            if (metric.permission !in granted) {
                lines += "${metric.label}: wymaga zgody"
                continue
            }
            try {
                if (metric.id in setOf("hrv_rmssd", "oxygen_saturation", "respiratory_rate", "body_temperature", "vo2_max", "lean_body_mass")) {
                    val values = HealthHistorySource(client).read(metric.id, DateChunk(start, end))
                    lines += "${metric.label}: ${if (values.isEmpty()) "brak danych" else values.joinToString("; ") { "${it.date}: ${it.value} ${metric.unit}" }}"
                    continue
                }
                if (metric.id in sleepMetricIds) {
                    val sessions = readAll(SleepSessionRecord::class, start.minusDays(7), end)
                    val days = sleepByEndDate(sessions.map { it.toSleepInterval() })
                        .filterKeys { it >= start && it < end }
                    val values = days.map { (date, day) ->
                        val duration = if (metric.id == "sleep_awake") day.awakeDuration else day.duration
                        if (day.overlapping) "$date: nakładające się sesje — brak wyniku"
                        else if (duration == null) "$date: brak kompletnych faz snu — brak wyniku"
                        else "$date: ${duration.toMinutes() / 60.0} godz."
                    }
                    lines += "${metric.label}: ${if (values.isEmpty()) "brak danych" else values.joinToString("; ")}. Data zakończenia sesji; sen bez czuwania, wybudzenia tylko pomiędzy fazami snu."
                    continue
                }
                if (metric.id == "body_fat") {
                    val records = readAll(BodyFatRecord::class, start, end)
                    val days = latestBodyFatByDay(records.map { BodyFatSample(it.time, it.metadata.id, it.percentage.value) })
                    val values = days.map { (date, latest) -> "$date: ${latest.value} %" }
                    val sources = records.map { it.metadata.dataOrigin.packageName }.distinct()
                    val warning = if (sources.size > 1) " Pomiar wybierany ze wszystkich źródeł; możliwe duplikaty." else ""
                    lines += "${metric.label}: ${if (values.isEmpty()) "brak danych" else values.joinToString("; ")}$warning"
                    continue
                }
                val aggregate = metricFor(metric.id) ?: continue
                val rows = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(aggregate),
                        timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), end.atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1)
                    )
                )
                val values = rows.mapNotNull { row ->
                    val number = valueFor(metric.id, row.result)
                    number?.let { "${row.startTime.toLocalDate()}: $it ${metric.unit}" }
                }
                val detail = if (metric.id == "weight_avg" && values.isEmpty()) {
                    val records = readAll(WeightRecord::class, start, end)
                    if (records.isEmpty()) "Brak rekordów masy w Health Connect w tym okresie. Sprawdź uprawnienie zapisu Google Health i synchronizację."
                    else "Rekordy masy istnieją, lecz agregacja nie zwróciła wyniku. Źródła: ${records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString()}."
                } else if (values.isEmpty()) "brak danych" else values.joinToString("; ")
                lines += "${metric.label}: $detail"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lines += "${metric.label}: błąd odczytu (${e.javaClass.simpleName})"
            }
        }
        if (selected.isEmpty()) lines += "Wybierz co najmniej jedną statystykę."
        return lines.joinToString("\n\n")
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, start: LocalDate, end: LocalDate): List<T> {
        val records = mutableListOf<T>()
        var token: String? = null
        val range = TimeRangeFilter.between(start.atStartOfDay(WARSAW).toInstant(), end.atStartOfDay(WARSAW).toInstant())
        do {
            val page = client.readRecords(ReadRecordsRequest(type, range, pageToken = token))
            records.addAll(page.records)
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return records
    }
}

private val WARSAW = ZoneId.of("Europe/Warsaw")

private fun metricFor(id: String): AggregateMetric<*>? = when (id) {
    "steps" -> StepsRecord.COUNT_TOTAL
    "total_calories" -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
    "active_calories" -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
    "distance" -> DistanceRecord.DISTANCE_TOTAL
    "floors" -> FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
    "elevation" -> ElevationGainedRecord.ELEVATION_GAINED_TOTAL
    "exercise_duration" -> ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
    "heart_rate_avg" -> HeartRateRecord.BPM_AVG
    "heart_rate_min" -> HeartRateRecord.BPM_MIN
    "heart_rate_max" -> HeartRateRecord.BPM_MAX
    "resting_heart_rate" -> RestingHeartRateRecord.BPM_AVG
    "weight_avg" -> WeightRecord.WEIGHT_AVG
    "height_avg" -> HeightRecord.HEIGHT_AVG
    "hydration" -> HydrationRecord.VOLUME_TOTAL
    else -> null
}

private fun valueFor(id: String, result: androidx.health.connect.client.aggregate.AggregationResult): String? = when (id) {
    "steps" -> result[StepsRecord.COUNT_TOTAL]?.toString()
    "total_calories" -> result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.toString()
    "active_calories" -> result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toString()
    "distance" -> result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.toString()
    "floors" -> result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]?.toString()
    "elevation" -> result[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters?.toString()
    "exercise_duration" -> result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMillis()?.div(3_600_000.0)?.toString()
    "heart_rate_avg" -> result[HeartRateRecord.BPM_AVG]?.toString()
    "heart_rate_min" -> result[HeartRateRecord.BPM_MIN]?.toString()
    "heart_rate_max" -> result[HeartRateRecord.BPM_MAX]?.toString()
    "resting_heart_rate" -> result[RestingHeartRateRecord.BPM_AVG]?.toString()
    "weight_avg" -> result[WeightRecord.WEIGHT_AVG]?.inKilograms?.toString()
    "height_avg" -> result[HeightRecord.HEIGHT_AVG]?.inMeters?.toString()
    "hydration" -> result[HydrationRecord.VOLUME_TOTAL]?.inLiters?.toString()
    else -> null
}
