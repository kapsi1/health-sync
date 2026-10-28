package pl.healthsync.domain

import java.util.Locale
import kotlin.math.roundToLong

fun durationAsText(hours: Double): String {
    require(hours.isFinite() && hours >= 0 && hours <= Long.MAX_VALUE / 3600.0)
    val seconds = (hours * 3600).roundToLong()
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
}

fun sheetValue(columnName: String, value: Double): Any =
    if (columnName == "exercise_hours") durationAsText(value) else value

fun legacyDurationText(value: Any?): String? {
    val hours = (value as? Number)?.toDouble() ?: return null
    if (!hours.isFinite() || hours < 0 || hours > Long.MAX_VALUE / 3600.0) return null
    return durationAsText(hours)
}

val displayNumberPatterns = mapOf("total_kcal" to "0", "active_kcal" to "0", "distance_km" to "0.000")
