package pl.healthsync

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*

data class Metric(val id: String, val label: String, val unit: String, val category: String, val column: String, val permission: String)

object MetricRegistry {
    val all = listOf(
        Metric("steps", "Kroki", "kroki", "Aktywność", "steps", HealthPermission.getReadPermission(StepsRecord::class)),
        Metric("total_calories", "Spalone kalorie łącznie", "kcal", "Aktywność", "total_kcal", HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)),
        Metric("active_calories", "Aktywne kalorie", "kcal", "Aktywność", "active_kcal", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)),
        Metric("distance", "Dystans", "km", "Aktywność", "distance_km", HealthPermission.getReadPermission(DistanceRecord::class)),
        Metric("floors", "Piętra", "piętra", "Aktywność", "floors", HealthPermission.getReadPermission(FloorsClimbedRecord::class)),
        Metric("elevation", "Przewyższenie", "m", "Aktywność", "elevation_m", HealthPermission.getReadPermission(ElevationGainedRecord::class)),
        Metric("exercise_duration", "Czas ćwiczeń", "godz.", "Aktywność", "exercise_hours", HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
        Metric("heart_rate_avg", "Średnie tętno", "ud./min", "Serce", "heart_rate_avg_bpm", HealthPermission.getReadPermission(HeartRateRecord::class)),
        Metric("heart_rate_min", "Minimalne tętno", "ud./min", "Serce", "heart_rate_min_bpm", HealthPermission.getReadPermission(HeartRateRecord::class)),
        Metric("heart_rate_max", "Maksymalne tętno", "ud./min", "Serce", "heart_rate_max_bpm", HealthPermission.getReadPermission(HeartRateRecord::class)),
        Metric("resting_heart_rate", "Tętno spoczynkowe", "ud./min", "Serce", "resting_hr_bpm", HealthPermission.getReadPermission(RestingHeartRateRecord::class)),
        Metric("sleep_duration", "Czas snu", "godz.", "Sen", "sleep_hours", HealthPermission.getReadPermission(SleepSessionRecord::class)),
        Metric("weight_avg", "Średnia masa ciała", "kg", "Ciało", "weight_avg_kg", HealthPermission.getReadPermission(WeightRecord::class)),
        Metric("body_fat", "Tkanka tłuszczowa — ostatni pomiar", "%", "Ciało", "body_fat_percent", HealthPermission.getReadPermission(BodyFatRecord::class)),
        Metric("height_avg", "Średni wzrost", "m", "Ciało", "height_avg_m", HealthPermission.getReadPermission(HeightRecord::class)),
        Metric("hydration", "Nawodnienie", "l", "Odżywianie", "hydration_l", HealthPermission.getReadPermission(HydrationRecord::class)),
    )
}
