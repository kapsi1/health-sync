package pl.healthsync.domain

import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

fun durationAsText(hours: Double): String {
    require(hours.isFinite() && hours >= 0 && hours <= Long.MAX_VALUE / 3600.0)
    val seconds = (hours * 3600).roundToLong()
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
}

val durationColumns = setOf("exercise_hours", "sleep_hours", "sleep_awake_hours")
val integerColumns = setOf("total_kcal", "active_kcal")

/** Round only at the export boundary; local observations retain their source precision. */
fun sheetValue(columnName: String, value: Double): Any {
    require(value.isFinite())
    return if (columnName in durationColumns) durationAsText(value)
    else BigDecimal.valueOf(value).setScale(if (columnName in integerColumns) 0 else 2, RoundingMode.HALF_UP).toDouble()
}

fun legacyDurationText(value: Any?): String? {
    val hours = (value as? Number)?.toDouble() ?: return null
    if (!hours.isFinite() || hours < 0 || hours > Long.MAX_VALUE / 3600.0) return null
    return durationAsText(hours)
}

val numericColumns = setOf(
    "steps", "total_kcal", "active_kcal", "distance_km", "floors", "elevation_m",
    "heart_rate_avg_bpm", "heart_rate_min_bpm", "heart_rate_max_bpm", "resting_hr_bpm",
    "hrv_rmssd_ms", "oxygen_saturation_percent", "respiratory_rate", "body_temperature_c",
    "vo2_max", "weight_avg_kg", "body_fat_percent", "lean_body_mass_kg", "height_avg_m", "hydration_l",
    "hrv_rmssd_avg_ms", "oxygen_saturation_avg_percent", "respiratory_rate_avg", "body_temperature_avg_c",
    "vo2_max_avg", "body_fat_avg_percent", "lean_body_mass_avg_kg"
)
