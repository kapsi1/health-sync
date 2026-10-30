package pl.healthsync.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A source classification must be supplied explicitly; names alone can be ambiguous. */
internal enum class ActivitySourceCategory { WEARABLE, PHONE, GOOGLE_HEALTH, GOOGLE_FIT, CONNECT, UNKNOWN }
internal data class TakeoutActivitySample(val time: Instant, val value: Double, val source: ActivitySourceCategory)
internal data class TakeoutActivityEstimate(val value: Double?, val excludedConnectSamples: Int, val reason: String? = null)

/**
 * Approximation only: timestamps are grouped into minutes, not proven source intervals.
 * Concurrent samples of the same source are ambiguous and invalidate the day's estimate.
 */
internal fun estimateTakeoutActivityDay(
    day: LocalDate,
    zone: ZoneId,
    samples: Sequence<TakeoutActivitySample>,
): TakeoutActivityEstimate {
    val from = day.atStartOfDay(zone).toInstant()
    val until = day.plusDays(1).atStartOfDay(zone).toInstant()
    val accumulator = TakeoutActivityDayAccumulator()
    samples.filter { it.time >= from && it.time < until }.forEach(accumulator::add)
    return accumulator.result()
}

/** Stores minute buckets rather than all CSV rows, so files can be processed incrementally. */
internal class TakeoutActivityDayAccumulator(private val sumDistinctSameMinute: Boolean = false) {
    val priority = listOf(ActivitySourceCategory.WEARABLE, ActivitySourceCategory.PHONE,
        ActivitySourceCategory.GOOGLE_HEALTH, ActivitySourceCategory.GOOGLE_FIT)
    private val minutes = mutableMapOf<Instant, MutableMap<ActivitySourceCategory, Double>>()
    private val timestamps = mutableSetOf<Pair<Instant, ActivitySourceCategory>>()
    private var excluded = 0
    private var error: String? = null

    fun add(sample: TakeoutActivitySample) {
        if (sample.source == ActivitySourceCategory.CONNECT) { excluded++; return }
        if (error != null) return
        if (!sample.value.isFinite() || sample.value < 0) { error = "Nieprawidłowa wartość"; minutes.clear(); return }
        if (sample.source == ActivitySourceCategory.UNKNOWN) { error = "Nieznane źródło"; minutes.clear(); return }
        if (!timestamps.add(sample.time to sample.source)) { error = "Powtórzony czas wpisu źródła"; minutes.clear(); timestamps.clear(); return }
        val sources = minutes.getOrPut(sample.time.truncatedTo(ChronoUnit.MINUTES)) { mutableMapOf() }
        val previous = sources[sample.source]
        if (previous != null && !sumDistinctSameMinute) { error = "Kilka wpisów źródła w jednej minucie"; minutes.clear(); timestamps.clear(); return }
        sources[sample.source] = (previous ?: 0.0) + sample.value
    }

    fun result(): TakeoutActivityEstimate {
        error?.let { return TakeoutActivityEstimate(null, excluded, it) }
        if (minutes.isEmpty()) return TakeoutActivityEstimate(null, excluded, "Brak danych ze źródła innego niż Connect")
        val sum = minutes.values.sumOf { sources -> sources.getValue(priority.first { it in sources }) }
        if (!sum.isFinite()) return TakeoutActivityEstimate(null, excluded, "Nieprawidłowa suma")
        return TakeoutActivityEstimate(sum, excluded)
    }
}
