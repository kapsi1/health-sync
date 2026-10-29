package pl.healthsync

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class SleepState { ASLEEP, AWAKE, UNKNOWN }
internal data class SleepStageInterval(val start: Instant, val end: Instant, val state: SleepState)
internal data class SleepInterval(val start: Instant, val end: Instant, val stages: List<SleepStageInterval> = emptyList())
internal data class SleepDay(val duration: Duration?, val awakeDuration: Duration?, val overlapping: Boolean)

private data class SessionDurations(val asleep: Duration?, val awake: Duration?)

/** Unknown stages/gaps do not imply sleep or wakefulness. Intervals are half-open. */
private fun sessionDurations(session: SleepInterval): SessionDurations {
    if (session.end <= session.start) return SessionDurations(null, null)
    val stages = session.stages.sortedBy { it.start }
    if (stages.isEmpty() || stages.any { it.end <= it.start || it.start < session.start || it.end > session.end } ||
        stages.zipWithNext().any { (first, next) -> next.start < first.end }) return SessionDurations(null, null)

    fun complete(parts: List<SleepStageInterval>, start: Instant, end: Instant): Boolean =
        parts.isNotEmpty() && parts.first().start == start && parts.last().end == end &&
            parts.none { it.state == SleepState.UNKNOWN } &&
            parts.zipWithNext().all { (first, next) -> first.end == next.start }

    fun total(parts: List<SleepStageInterval>, state: SleepState): Duration =
        parts.filter { it.state == state }.fold(Duration.ZERO) { sum, stage -> sum.plus(Duration.between(stage.start, stage.end)) }

    val asleep = if (complete(stages, session.start, session.end)) total(stages, SleepState.ASLEEP) else null
    val firstSleep = stages.indexOfFirst { it.state == SleepState.ASLEEP }
    val lastSleep = stages.indexOfLast { it.state == SleepState.ASLEEP }
    // Wake time before first sleep or after last sleep is not an awakening during sleep.
    val inside = if (firstSleep >= 0) stages.subList(firstSleep, lastSleep + 1) else emptyList()
    val awake = if (inside.isNotEmpty() && complete(inside, inside.first().start, inside.last().end))
        total(inside, SleepState.AWAKE) else null
    return SessionDurations(asleep, awake)
}

// Sum whole sessions by their end date, but never guess totals for an incomplete day.
internal fun sleepByEndDate(sessions: List<SleepInterval>): Map<LocalDate, SleepDay> =
    sessions.groupBy { it.end.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate() }
        .mapValues { (_, group) ->
            val ordered = group.sortedBy { it.start }
            val overlap = ordered.zipWithNext().any { (first, next) -> next.start < first.end }
            if (overlap) SleepDay(null, null, true)
            else {
                val values = ordered.map(::sessionDurations)
                fun totalIfKnown(select: (SessionDurations) -> Duration?): Duration? {
                    val durations = values.map(select)
                    return if (durations.any { it == null }) null else durations.fold(Duration.ZERO) { sum, value -> sum.plus(value!!) }
                }
                SleepDay(totalIfKnown { it.asleep }, totalIfKnown { it.awake }, false)
            }
        }.toSortedMap()
