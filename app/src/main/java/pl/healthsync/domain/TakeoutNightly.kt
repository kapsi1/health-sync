package pl.healthsync.domain

import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal enum class NightlyKind { HRV, BREATHING, SKIN_TEMPERATURE }
internal data class TakeoutNightly(val kind: NightlyKind, val day: LocalDate, val value: Double?, val baseline: Double? = null)

/** Daily Health summaries are already calculated for the main sleep, not raw samples. */
internal fun readTakeoutNightly(reader: Reader, kind: NightlyKind): List<TakeoutNightly> {
    val field = when (kind) {
        NightlyKind.HRV -> "average heart rate variability milliseconds"
        NightlyKind.BREATHING -> "breaths per minute"
        NightlyKind.SKIN_TEMPERATURE -> "nightly temperature celsius"
    }
    val required = mutableSetOf("timestamp", "data source", field)
    if (kind == NightlyKind.SKIN_TEMPERATURE) required.add("baseline temperature celsius")
    fun number(s: String): Double? = s.toDoubleOrNull()?.takeIf { it.isFinite() }
    val rows = readTakeoutCsv(reader, required).filter { it.getValue("data source") == "Google Health App" }.map { row ->
        val time = try { Instant.parse(row.getValue("timestamp")) } catch (_: Exception) { throw IllegalArgumentException("Nieprawidłowa data podsumowania nocnego") }
        require(time.atOffset(ZoneOffset.UTC).toLocalTime() == java.time.LocalTime.MIDNIGHT) { "Nieoczekiwany czas podsumowania nocnego" }
        val value = number(row.getValue(field))
        val base = if (kind == NightlyKind.SKIN_TEMPERATURE) number(row.getValue("baseline temperature celsius")) else null
        TakeoutNightly(kind, time.atOffset(ZoneOffset.UTC).toLocalDate(), if (kind == NightlyKind.SKIN_TEMPERATURE) if (value != null && base != null) value - base else null else value, base)
    }
    return rows
}

/** Only match a uniquely identifiable primary v2 sleep of at least three hours. */
internal fun nightlySleepForDay(day: LocalDate, sleeps: List<TakeoutSleep>, thirdPartyIds: Set<String>?, zone: ZoneId): TakeoutSleep? {
    if (thirdPartyIds == null) return null
    val candidates = takeoutSleepGroups(sleeps).flatMap { group ->
        if (group.any { it.id in thirdPartyIds }) emptyList() else selectedSleepV2(group).orEmpty()
    }.filter { it.end.atZone(zone).toLocalDate() == day && (it.minutesAsleep ?: 0) > 180 }
    return candidates.singleOrNull()
}
