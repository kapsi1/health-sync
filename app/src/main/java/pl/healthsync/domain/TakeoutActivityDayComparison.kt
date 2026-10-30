package pl.healthsync.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class TakeoutActivityKind { STEPS, DISTANCE, TOTAL_CALORIES, ACTIVE_CALORIES }

internal data class ExistingActivityInterval(val kind: TakeoutActivityKind, val start: Instant, val end: Instant) {
    init { require(start < end) { "Nieprawidłowy przedział danych aktywności" } }
}

/** An interval touching a day without overlapping it does not make that day present. */
internal fun activityDayHasConnectData(
    day: LocalDate,
    kind: TakeoutActivityKind,
    zone: ZoneId,
    existing: Iterable<ExistingActivityInterval>,
): Boolean {
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    return existing.any { it.kind == kind && it.start < end && start < it.end }
}
