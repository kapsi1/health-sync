package pl.healthsync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class BodyFatSample(val time: Instant, val id: String, val value: Double)

/** Arithmetic mean of available records, assigned to their local calendar day. */
internal fun averageSamplesByDay(samples: List<BodyFatSample>): Map<LocalDate, Double> =
    samples.groupBy { it.time.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate() }
        .mapValues { (_, records) -> records.map { it.value }.average() }
        .toSortedMap()
