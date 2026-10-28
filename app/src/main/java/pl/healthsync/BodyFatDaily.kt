package pl.healthsync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class BodyFatSample(val time: Instant, val id: String, val value: Double)

internal fun latestBodyFatByDay(samples: List<BodyFatSample>): Map<LocalDate, BodyFatSample> =
    samples.groupBy { it.time.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate() }
        .mapValues { (_, records) -> records.maxWithOrNull(compareBy<BodyFatSample> { it.time }.thenBy { it.id })!! }
        .toSortedMap()
