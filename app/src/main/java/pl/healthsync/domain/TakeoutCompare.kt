package pl.healthsync.domain

import java.time.Instant
import java.security.MessageDigest
import java.time.Duration
import kotlin.math.abs

internal enum class ImportDecision { INSERT, ALREADY_PRESENT, CONFLICT, ORIGINATES_IN_CONNECT, UNKNOWN_SOURCE }
internal data class ExistingMeasurement(val kind: TakeoutKind, val time: Instant, val value: Double)
internal data class ExistingSleepStage(val start: Instant, val end: Instant, val type: String)
internal data class ExistingSleep(val start: Instant, val end: Instant, val stages: List<ExistingSleepStage> = emptyList())

/** Data copied out of Health Connect must not be written back into it. */
internal fun classifyMeasurement(candidate: TakeoutMeasurement, existing: List<ExistingMeasurement>): ImportDecision {
    when (candidate.source) {
        "Fit Health Connect" -> return ImportDecision.ORIGINATES_IN_CONNECT
        "Google Health App" -> Unit
        else -> return ImportDecision.UNKNOWN_SOURCE
    }
    val sameTime = existing.filter { it.kind == candidate.kind && it.time == candidate.time }
    if (sameTime.isEmpty()) return ImportDecision.INSERT
    val tolerance = if (candidate.kind == TakeoutKind.WEIGHT) 0.0005 else 0.0001
    return if (sameTime.all { abs(it.value - candidate.value) <= tolerance }) ImportDecision.ALREADY_PRESENT else ImportDecision.CONFLICT
}

/** An overlap alone is not enough to conclude that two sleep sessions are identical. */
internal fun classifySleep(candidate: TakeoutSleep, existing: List<ExistingSleep>): ImportDecision {
    if (candidate.source != "DERIVED") return ImportDecision.UNKNOWN_SOURCE
    if (candidate.stages.isEmpty() || candidate.minutesAsleep == null) return ImportDecision.CONFLICT
    val asleepSeconds = candidate.stages.filter { it.type != "AWAKE" }.sumOf { Duration.between(it.start, it.end).seconds }
    if (abs(asleepSeconds / 60.0 - candidate.minutesAsleep.toDouble()) > 1.0) return ImportDecision.CONFLICT
    val overlaps = existing.filter { it.start < candidate.end && candidate.start < it.end }
    if (overlaps.isEmpty()) return ImportDecision.INSERT
    if (overlaps.size != 1) return ImportDecision.CONFLICT
    val other = overlaps.single()
    val candidateStages = candidate.stages.map { ExistingSleepStage(it.start, it.end, it.type) }
    return if (other.start == candidate.start && other.end == candidate.end && candidateStages.isNotEmpty() &&
        candidateStages == other.stages.sortedBy { it.start }) ImportDecision.ALREADY_PRESENT else ImportDecision.CONFLICT
}

/** A deterministic ID does not contain a raw measurement or a source identifier. */
internal fun takeoutClientId(kind: String, accountKey: String, sourceKey: String): String {
    require(kind in setOf("sleep", "weight", "body_fat") && accountKey.isNotBlank() && sourceKey.isNotBlank())
    val digest = MessageDigest.getInstance("SHA-256")
    listOf("takeout-v1", kind, accountKey, sourceKey).forEach { field ->
        val bytes = field.toByteArray(Charsets.UTF_8)
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
    }
    return "takeout-v1-" + digest.digest().joinToString("") { "%02x".format(it) }
}
