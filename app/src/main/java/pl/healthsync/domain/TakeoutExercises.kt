package pl.healthsync.domain

import java.io.Reader
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal data class TakeoutExercise(
    val id: String,
    val start: Instant,
    val end: Instant,
    val offset: ZoneOffset,
    val lastUpdated: Instant,
    val activity: String,
    val logType: String,
    val deletionReason: String,
    val unsupportedDetails: Boolean = false,
)

/** Preserve source identifiers, explicit offsets and revision; no route or inferred metrics. */
internal fun readTakeoutExercises(reader: Reader): List<TakeoutExercise> {
    val fields = setOf("exercise_id", "exercise_start", "exercise_end", "utc_offset", "exercise_last_updated", "activity_name", "log_type", "deletion_reason")
    val rows = readTakeoutCsv(reader, fields)
    fun instant(s: String) = try { Instant.parse(s) } catch (_: DateTimeParseException) { throw IllegalArgumentException("Nieprawidłowy czas treningu w Takeout") }
    val result = rows.map { row ->
        val id = row.getValue("exercise_id").trim()
        val start = instant(row.getValue("exercise_start")); val end = instant(row.getValue("exercise_end"))
        val offset = try { ZoneOffset.of(row.getValue("utc_offset")) } catch (_: Exception) { throw IllegalArgumentException("Nieprawidłowa strefa treningu w Takeout") }
        val activity = row.getValue("activity_name").trim(); val logType = row.getValue("log_type").trim()
        require(id.isNotEmpty() && start < end && activity.isNotEmpty() && logType.isNotEmpty()) { "Niekompletny trening w Takeout" }
        val unsupportedDetails = listOf("intervals", "events", "location_metadata").any { name -> row[name].orEmpty().trim().let { it.isNotEmpty() && it != "[]" && it != "{}" } }
        TakeoutExercise(id, start, end, offset, instant(row.getValue("exercise_last_updated")), activity, logType, row.getValue("deletion_reason").trim(), unsupportedDetails)
    }
    require(result.map { it.id }.distinct().size == result.size) { "Powtórzony identyfikator treningu w Takeout" }
    return result
}

/** Group all transitively overlapping sessions, including overlaps across calendar days. */
internal fun overlappingExerciseGroups(rows: List<TakeoutExercise>): List<List<TakeoutExercise>> {
    val groups = mutableListOf<List<TakeoutExercise>>()
    val current = mutableListOf<TakeoutExercise>()
    var end: Instant? = null
    for (row in rows.sortedWith(compareBy(TakeoutExercise::start, TakeoutExercise::end, TakeoutExercise::id))) {
        if (end != null && row.start >= end) {
            groups.add(current.toList())
            current.clear()
            end = null
        }
        current.add(row)
        if (end == null || row.end > end) end = row.end
    }
    if (current.isNotEmpty()) groups.add(current.toList())
    return groups
}

/** Only the explicitly approved tracker/automatic walking pair is resolved automatically. */
internal fun selectedTakeoutExercises(rows: List<TakeoutExercise>): Map<String, ImportDecision> = buildMap {
    overlappingExerciseGroups(rows).forEach { group ->
        when {
            group.size == 1 -> put(group.single().id, ImportDecision.INSERT)
            group.size == 2 && group.all { it.activity == "Outdoor Walk" } && group.map { it.logType }.toSet() == setOf("TRACKER", "AUTO_DETECTED") -> {
                group.forEach { put(it.id, if (it.logType == "TRACKER") ImportDecision.INSERT else ImportDecision.SUPERSEDED) }
            }
            else -> group.forEach { put(it.id, ImportDecision.CONFLICT) }
        }
    }
}
