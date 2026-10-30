package pl.healthsync.domain

import java.time.Instant
import java.security.MessageDigest
import java.time.Duration
import java.time.ZoneId
import kotlin.math.abs

internal enum class ImportDecision { INSERT, ALREADY_PRESENT, CONFLICT, ORIGINATES_IN_CONNECT, UNKNOWN_SOURCE, SUPERSEDED }
internal data class ExistingMeasurement(val kind: TakeoutKind, val time: Instant, val value: Double)
internal data class ExistingSleepStage(val start: Instant, val end: Instant, val type: String)
internal data class ExistingSleep(val start: Instant, val end: Instant, val stages: List<ExistingSleepStage> = emptyList())
internal enum class SleepConflictReason(val label: String) {
    SOURCE_OVERLAP("Nakładające się sesje w Takeout"),
    INCOMPLETE("Brak faz lub podsumowania snu w Takeout"),
    SUMMARY_MISMATCH("Czas faz różni się od podsumowania w Takeout"),
    MULTIPLE_EXISTING("Kilka nakładających się sesji w Health Connect"),
    BOUNDS_DIFFER("Inny początek lub koniec sesji w Health Connect"),
    EXISTING_NO_STAGES("Sesja w Health Connect nie zawiera faz"),
    STAGES_DIFFER("Fazy w Health Connect różnią się od Takeout"),
    NO_UNIQUE_V2("Nie można jednoznacznie wybrać sesji v2"),
    ALTERNATIVE_IN_CONNECT("Inna wersja sesji nakłada się na dane w Health Connect")
}
internal data class SleepComparison(val decision: ImportDecision, val reason: SleepConflictReason? = null)

/** Mark every session in a connected group of overlapping intervals, in O(n log n). */
internal fun overlappingTakeoutSleepIds(sleeps: List<TakeoutSleep>): Set<String> {
    return overlappingTakeoutSleepGroups(sleeps).flatMap { group -> group.map { it.id } }.toSet()
}

internal fun overlappingTakeoutSleepGroups(sleeps: List<TakeoutSleep>): List<List<TakeoutSleep>> {
    return takeoutSleepGroups(sleeps).filter { it.size > 1 }
}

internal fun takeoutSleepGroups(sleeps: List<TakeoutSleep>): List<List<TakeoutSleep>> {
    val result = mutableListOf<List<TakeoutSleep>>()
    val group = mutableListOf<TakeoutSleep>()
    var end: Instant? = null
    fun finish() { if (group.isNotEmpty()) result.add(group.toList()); group.clear() }
    sleeps.sortedBy { it.start }.forEach { sleep ->
        if (end != null && sleep.start >= end) finish()
        group.add(sleep)
        end = if (group.size == 1) sleep.end else maxOf(end!!, sleep.end)
    }
    finish()
    return result
}

/** A single v1 can be replaced by one or more mutually non-overlapping v2 sessions. */
internal fun selectedSleepV2(group: List<TakeoutSleep>): List<TakeoutSleep>? {
    val v2 = group.filter { it.algorithmVersion == "sleep_session_v2" }.sortedBy { it.start }
    if (v2.isEmpty() || group.any { it.algorithmVersion !in setOf("sleep_session_v1", "sleep_session_v2") } ||
        group.count { it.algorithmVersion == "sleep_session_v1" } > 1 ||
        v2.zipWithNext().any { (a, b) -> a.end > b.start }) return null
    return v2
}

internal fun resolveSleepV2(sleeps: List<TakeoutSleep>, existing: List<ExistingSleep>, thirdPartyIds: Set<String>? = emptySet()): Map<String, SleepComparison> {
    val result = mutableMapOf<String, SleepComparison>()
    for (group in takeoutSleepGroups(sleeps)) {
        val v2 = selectedSleepV2(group)
        if (v2 == null) {
            group.forEach { result[it.id] = SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.NO_UNIQUE_V2) }
            continue
        }
        for (selected in v2) {
            var comparison = if (thirdPartyIds == null || group.any { it.id in thirdPartyIds }) SleepComparison(ImportDecision.UNKNOWN_SOURCE)
                else compareSleep(selected, existing)
            val unrelated = existing.filterNot { row -> v2.any { compareSleep(it, listOf(row)).decision == ImportDecision.ALREADY_PRESENT } }
            if (comparison.decision == ImportDecision.INSERT && group.filter { it.algorithmVersion == "sleep_session_v1" }.any { alternate ->
                    unrelated.any { it.start < alternate.end && alternate.start < it.end }
                }) comparison = SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.ALTERNATIVE_IN_CONNECT)
            result[selected.id] = comparison
        }
        group.filter { it.algorithmVersion == "sleep_session_v1" }.forEach { result[it.id] = SleepComparison(ImportDecision.SUPERSEDED) }
    }
    return result
}

/** Data copied out of Health Connect must not be written back into it. */
internal fun classifyMeasurement(candidate: TakeoutMeasurement, existing: List<ExistingMeasurement>): ImportDecision {
    when (candidate.source) {
        "Fit Health Connect" -> return ImportDecision.ORIGINATES_IN_CONNECT
        "Google Health App" -> Unit
        else -> return ImportDecision.UNKNOWN_SOURCE
    }
    val sameTime = existing.filter { it.kind == candidate.kind && it.time == candidate.time }
    if (sameTime.isEmpty()) return ImportDecision.INSERT
    val tolerance = if (candidate.kind == TakeoutKind.BODY_FAT) 0.0001 else 0.0005
    return if (sameTime.all { abs(it.value - candidate.value) <= tolerance }) ImportDecision.ALREADY_PRESENT else ImportDecision.CONFLICT
}

/** Resting heart rate in this export is a daily summary, so don't add a second daily summary. */
internal fun compareDailyRestingHeartRates(source: List<TakeoutMeasurement>, existing: List<ExistingMeasurement>, zone: ZoneId): List<Pair<TakeoutMeasurement, ImportDecision>> {
    val sourceCounts = source.filter { it.source == "Google Health App" }.groupingBy { it.time.atZone(zone).toLocalDate() }.eachCount()
    val existingDays = existing.filter { it.kind == TakeoutKind.RESTING_HEART_RATE }.map { it.time.atZone(zone).toLocalDate() }.toSet()
    return source.map { row ->
        require(row.kind == TakeoutKind.RESTING_HEART_RATE)
        val date = row.time.atZone(zone).toLocalDate()
        val decision = when {
            row.source == "Fit Health Connect" -> ImportDecision.ORIGINATES_IN_CONNECT
            row.source != "Google Health App" -> ImportDecision.UNKNOWN_SOURCE
            date in existingDays -> ImportDecision.ALREADY_PRESENT
            sourceCounts.getValue(date) != 1 -> ImportDecision.CONFLICT
            else -> ImportDecision.INSERT
        }
        row to decision
    }
}

/** An overlap alone is not enough to conclude that two sleep sessions are identical. */
internal fun classifySleep(candidate: TakeoutSleep, existing: List<ExistingSleep>): ImportDecision {
    return compareSleep(candidate, existing).decision
}

internal fun compareSleep(candidate: TakeoutSleep, existing: List<ExistingSleep>, sourceOverlap: Boolean = false): SleepComparison {
    if (sourceOverlap) return SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.SOURCE_OVERLAP)
    if (candidate.source != "DERIVED") return SleepComparison(ImportDecision.UNKNOWN_SOURCE)
    if (candidate.stages.isEmpty() || candidate.minutesAsleep == null) return SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.INCOMPLETE)
    val ordered = candidate.stages.sortedBy { it.start }
    if (ordered.first().start != candidate.start || ordered.last().end != candidate.end ||
        ordered.zipWithNext().any { (a, b) -> a.end != b.start }) return SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.INCOMPLETE)
    val asleepSeconds = candidate.stages.filter { it.type != "AWAKE" }.sumOf { Duration.between(it.start, it.end).seconds }
    if (abs(asleepSeconds / 60.0 - candidate.minutesAsleep.toDouble()) > 1.0) return SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.SUMMARY_MISMATCH)
    val overlaps = existing.filter { it.start < candidate.end && candidate.start < it.end }
    if (overlaps.isEmpty()) return SleepComparison(ImportDecision.INSERT)
    if (overlaps.size != 1) return SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.MULTIPLE_EXISTING)
    val other = overlaps.single()
    if (other.start != candidate.start || other.end != candidate.end) return SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.BOUNDS_DIFFER)
    if (other.stages.isEmpty()) return SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.EXISTING_NO_STAGES)
    val candidateStages = candidate.stages.map { ExistingSleepStage(it.start, it.end, it.type) }
    return if (candidateStages == other.stages.sortedBy { it.start }) SleepComparison(ImportDecision.ALREADY_PRESENT)
    else SleepComparison(ImportDecision.CONFLICT, SleepConflictReason.STAGES_DIFFER)
}

/** A deterministic ID does not contain a raw measurement or a source identifier. */
internal fun takeoutClientId(kind: String, accountKey: String, sourceKey: String): String {
    require(kind in setOf("sleep", "weight", "body_fat", "height", "resting_heart_rate", "steps", "distance", "total_calories", "active_calories") && accountKey.isNotBlank() && sourceKey.isNotBlank())
    val digest = MessageDigest.getInstance("SHA-256")
    listOf("takeout-v1", kind, accountKey, sourceKey).forEach { field ->
        val bytes = field.toByteArray(Charsets.UTF_8)
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
    }
    return "takeout-v1-" + digest.digest().joinToString("") { "%02x".format(it) }
}
