package pl.healthsync.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class SleepBreathingInterval(val start: Instant, val end: Instant)
internal data class SleepBreathingSession(val end: Instant, val asleep: List<SleepBreathingInterval>)
internal data class BreathingSample(val time: Instant, val value: Double, val originPackage: String = "")

/** Health writes a daily sleep summary; other sources require explicit asleep stages. */
internal fun breathingDuringSleep(sessions: List<SleepBreathingSession>, samples: List<BreathingSample>, zone: ZoneId): Map<LocalDate, Double> {
    val health = samples.filter { it.originPackage == "com.fitbit.FitbitMobile" && it.value.isFinite() }.groupBy { it.time.atZone(zone).toLocalDate() }
    val intervals = sessions.filter { it.end.atZone(zone).toLocalDate() !in health }.flatMap { session ->
        session.asleep.map { Triple(it.start, it.end, session.end.atZone(zone).toLocalDate()) }
    }.sortedBy { it.first }
    require(intervals.all { it.first < it.second } && intervals.zipWithNext().none { (a, b) -> a.second > b.first }) { "Nakładające się fazy snu uniemożliwiają obliczenie oddechów" }
    val byDay = mutableMapOf<LocalDate, MutableList<Double>>()
    val sortedSamples = samples.filter { it.originPackage != "com.fitbit.FitbitMobile" }.sortedBy { it.time }
    var index = 0
    for (sample in sortedSamples) {
        if (!sample.value.isFinite()) continue
        while (index < intervals.size && intervals[index].second <= sample.time) index++
        val interval = intervals.getOrNull(index) ?: break
        if (sample.time >= interval.first && sample.time < interval.second) byDay.getOrPut(interval.third) { mutableListOf() }.add(sample.value)
    }
    val result = byDay.mapValues { (_, values) -> values.average() }.toMutableMap()
    for ((day, summaries) in health) {
        // Ambiguous Health summaries cannot safely be combined or replaced by unrelated samples.
        if (summaries.size == 1) result[day] = summaries.single().value else result.remove(day)
    }
    return result
}
