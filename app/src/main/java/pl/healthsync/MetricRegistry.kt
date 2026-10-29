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
        Metric("floors", "Piętra", "piętra", "Aktywność", "floors", HealthPermission.getReadPermission(FloorsClimbedRecord::class)),
        Metric("elevation", "Przewyższenie", "m", "Aktywność", "elevation_m", HealthPermission.getReadPermission(ElevationGainedRecord::class)),
        Metric("exercise_duration", "Czas ćwiczeń", "godz.", "Aktywność", "exercise_hours", HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
        Metric("heart_rate_avg", "Tętno", "ud./min", "Serce", "heart_rate_avg_bpm", HealthPermission.getReadPermission(HeartRateRecord::class)),
        Metric("heart_rate_min", "Minimalne tętno", "ud./min", "Serce", "heart_rate_min_bpm", HealthPermission.getReadPermission(HeartRateRecord::class)),
        Metric("heart_rate_max", "Maksymalne tętno", "ud./min", "Serce", "heart_rate_max_bpm", HealthPermission.getReadPermission(HeartRateRecord::class)),
        Metric("resting_heart_rate", "Tętno spoczynkowe", "ud./min", "Serce", "resting_hr_bpm", HealthPermission.getReadPermission(RestingHeartRateRecord::class)),
        Metric("hrv_rmssd", "HRV RMSSD", "ms", "Serce", "hrv_rmssd_avg_ms", HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)),
        Metric("oxygen_saturation", "Nasycenie krwi tlenem", "%", "Parametry życiowe", "oxygen_saturation_avg_percent", HealthPermission.getReadPermission(OxygenSaturationRecord::class)),
        Metric("respiratory_rate", "Oddechy/min", "oddechów/min", "Parametry życiowe", "respiratory_rate_avg", HealthPermission.getReadPermission(RespiratoryRateRecord::class)),
        Metric("body_temperature", "Temperatura ciała", "°C", "Parametry życiowe", "body_temperature_avg_c", HealthPermission.getReadPermission(BodyTemperatureRecord::class)),
        Metric("vo2_max", "VO₂ max", "ml/kg/min", "Parametry życiowe", "vo2_max_avg", HealthPermission.getReadPermission(Vo2MaxRecord::class)),
        Metric("sleep_duration", "Czas snu", "godz.", "Sen", "sleep_hours", HealthPermission.getReadPermission(SleepSessionRecord::class)),
        Metric("sleep_awake", "Czas wybudzeń podczas snu", "godz.", "Sen", "sleep_awake_hours", HealthPermission.getReadPermission(SleepSessionRecord::class)),
        Metric("weight_avg", "Waga kg", "kg", "Ciało", "weight_avg_kg", HealthPermission.getReadPermission(WeightRecord::class)),
        Metric("body_fat", "% tłuszczu", "%", "Ciało", "body_fat_avg_percent", HealthPermission.getReadPermission(BodyFatRecord::class)),
        Metric("lean_body_mass", "Beztłuszczowa masa ciała", "kg", "Ciało", "lean_body_mass_avg_kg", HealthPermission.getReadPermission(LeanBodyMassRecord::class)),
        Metric("height_avg", "Wzrost", "m", "Ciało", "height_avg_m", HealthPermission.getReadPermission(HeightRecord::class)),
        Metric("hydration", "Nawodnienie", "l", "Odżywianie", "hydration_l", HealthPermission.getReadPermission(HydrationRecord::class)),
    )
    val archivedColumns = mapOf(
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
