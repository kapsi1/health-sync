package pl.healthsync

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import pl.healthsync.domain.SheetHeaders

data class Metric(val id: String, val label: String, val unit: String, val category: String, val column: String, val permission: String)

object MetricRegistry {
    val all = listOf(
        Metric("steps", "Kroki", "kroki", "Aktywność", "steps", HealthPermission.getReadPermission(StepsRecord::class)),
        Metric("total_calories", "Spalone kalorie", "kcal", "Aktywność", "total_kcal", HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)),
        Metric("active_calories", "Aktywne kalorie", "kcal", "Aktywność", "active_kcal", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)),
        Metric("distance", "Dystans km", "km", "Aktywność", "distance_km", HealthPermission.getReadPermission(DistanceRecord::class)),
        Metric("exercise_duration", "Czas ćwiczeń", "godz.", "Aktywność", "exercise_hours", HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
        Metric("resting_heart_rate", "Tętno spoczynkowe", "ud./min", "Serce", "resting_hr_bpm", HealthPermission.getReadPermission(RestingHeartRateRecord::class)),
        Metric("hrv_rmssd", "HRV RMSSD", "ms", "Serce", "hrv_rmssd_avg_ms", HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)),
        Metric("oxygen_saturation", "Nasycenie krwi tlenem", "%", "Parametry życiowe", "oxygen_saturation_avg_percent", HealthPermission.getReadPermission(OxygenSaturationRecord::class)),
        Metric("respiratory_rate_sleep", "Oddechy/min podczas snu", "oddechów/min", "Parametry życiowe", "respiratory_sleep_avg", HealthPermission.getReadPermission(RespiratoryRateRecord::class)),
        Metric("skin_temperature_delta", "Wahania temperatury skóry", "°C", "Parametry życiowe", "skin_temperature_delta_c", HealthPermission.getReadPermission(SkinTemperatureRecord::class)),
        Metric("sleep_duration", "Czas snu", "godz.", "Sen", "sleep_hours", HealthPermission.getReadPermission(SleepSessionRecord::class)),
        Metric("sleep_awake", "Czas wybudzeń podczas snu", "godz.", "Sen", "sleep_awake_hours", HealthPermission.getReadPermission(SleepSessionRecord::class)),
        Metric("weight_avg", "Waga kg", "kg", "Ciało", "weight_avg_kg", HealthPermission.getReadPermission(WeightRecord::class)),
        Metric("body_fat", "% tłuszczu", "%", "Ciało", "body_fat_avg_percent", HealthPermission.getReadPermission(BodyFatRecord::class)),
    )
    val archivedColumns = mapOf(
        "floors" to "Piętra", "elevation_m" to "Przewyższenie",
        "heart_rate_avg_bpm" to "Tętno", "heart_rate_min_bpm" to "Minimalne tętno", "heart_rate_max_bpm" to "Maksymalne tętno",
        "respiratory_rate_avg" to "Oddechy/min", "body_temperature_avg_c" to "Temperatura ciała",
        "vo2_max_avg" to "VO₂ max", "lean_body_mass_avg_kg" to "Beztłuszczowa masa ciała",
        "height_avg_m" to "Wzrost", "hydration_l" to "Nawodnienie",
        "hrv_rmssd_ms" to "HRV RMSSD — ostatni pomiar",
        "oxygen_saturation_percent" to "Nasycenie krwi tlenem — ostatni pomiar",
        "respiratory_rate" to "Oddechy — ostatni pomiar",
        "body_temperature_c" to "Temperatura ciała — ostatni pomiar",
        "vo2_max" to "VO₂ max — ostatni pomiar",
        "body_fat_percent" to "Tkanka tłuszczowa — ostatni pomiar",
        "lean_body_mass_kg" to "Beztłuszczowa masa ciała — ostatni pomiar",
    )
    val previousLabels = mapOf(
        "Spalone kalorie łącznie" to "total_kcal", "Średnie tętno" to "heart_rate_avg_bpm",
        "Średnie HRV RMSSD" to "hrv_rmssd_avg_ms", "Średnie nasycenie krwi tlenem" to "oxygen_saturation_avg_percent",
        "Średnia liczba oddechów" to "respiratory_rate_avg", "Średnia temperatura ciała" to "body_temperature_avg_c",
        "Średnie VO₂ max" to "vo2_max_avg", "Średnia masa ciała" to "weight_avg_kg",
        "Średnia tkanka tłuszczowa" to "body_fat_avg_percent", "Średnia beztłuszczowa masa ciała" to "lean_body_mass_avg_kg",
        "Średni wzrost" to "height_avg_m", "Waga" to "weight_avg_kg", "Dystans" to "distance_km",
    )
    val sheetHeaders by lazy { SheetHeaders(all.associate { it.column to it.label } + archivedColumns, previousLabels) }
    val managedColumns get() = all.map { it.column }.toSet() + archivedColumns.keys
}

fun requiredMetricPermissions(metric: Metric): Set<String> = buildSet {
    add(metric.permission)
    if (metric.id in setOf("respiratory_rate_sleep", "hrv_rmssd")) add(HealthPermission.getReadPermission(SleepSessionRecord::class))
}
