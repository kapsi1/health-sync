package pl.healthsync

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class SleepInterval(val start: Instant, val end: Instant)
internal data class SleepDay(val duration: Duration?, val overlapping: Boolean)

// Group the entire elapsed session by its end date; overlapping sessions cannot be safely summed.
internal fun sleepByEndDate(sessions: List<SleepInterval>): Map<LocalDate, SleepDay> =
    sessions.groupBy { it.end.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate() }
        .mapValues { (_, group) ->
            val ordered = group.sortedBy { it.start }
            val overlap = ordered.zipWithNext().any { (first, next) -> next.start < first.end }
            if (overlap) SleepDay(null, true)
            else SleepDay(ordered.fold(Duration.ZERO) { sum, session -> sum.plus(Duration.between(session.start, session.end)) }, false)
        }.toSortedMap()
