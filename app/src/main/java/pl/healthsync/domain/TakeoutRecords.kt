package pl.healthsync.domain

import java.io.Reader
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal enum class TakeoutKind { WEIGHT, BODY_FAT, HEIGHT, RESTING_HEART_RATE }
internal data class TakeoutMeasurement(val kind: TakeoutKind, val time: Instant, val value: Double, val source: String)
internal data class TakeoutStage(val id: String, val start: Instant, val end: Instant, val type: String)
internal data class TakeoutSleep(val id: String, val start: Instant, val end: Instant, val startOffset: ZoneOffset,
    val endOffset: ZoneOffset, val source: String, val lastUpdated: Instant, val minutesAsleep: Long?, val stages: List<TakeoutStage>,
    val algorithmVersion: String? = null)

private fun timestamp(value: String): Instant = try { Instant.parse(value) } catch (_: DateTimeParseException) {
    throw IllegalArgumentException("Nieprawidłowy czas UTC w eksporcie")
}
private fun offset(value: String): ZoneOffset = try { ZoneOffset.of(value) } catch (_: Exception) {
    throw IllegalArgumentException("Nieprawidłowa strefa czasu w eksporcie")
}
private fun Map<String, String>.required(name: String): String = getValue(name).also { require(it.isNotBlank()) { "Brak wymaganego pola eksportu" } }

internal fun readTakeoutMeasurements(reader: Reader, kind: TakeoutKind): List<TakeoutMeasurement> {
    val field = when (kind) { TakeoutKind.WEIGHT -> "weight grams"; TakeoutKind.BODY_FAT -> "body fat percentage"; TakeoutKind.HEIGHT -> "height millimeters"; TakeoutKind.RESTING_HEART_RATE -> "beats per minute" }
    return readTakeoutCsv(reader, setOf("timestamp", field, "data source")).map { row ->
        val numeric = row.required(field).toDoubleOrNull() ?: throw IllegalArgumentException("Nieprawidłowa wartość pomiaru")
        require(numeric.isFinite() && when (kind) {
            TakeoutKind.WEIGHT -> numeric > 0.0 && numeric <= 1_000_000.0
            TakeoutKind.BODY_FAT -> numeric in 0.0..100.0
            TakeoutKind.HEIGHT -> numeric > 0.0 && numeric <= 3_000.0
            TakeoutKind.RESTING_HEART_RATE -> numeric in 1.0..300.0
        }) { when (kind) {
            TakeoutKind.WEIGHT -> "Nieprawidłowa wartość wagi w eksporcie"
            TakeoutKind.BODY_FAT -> "Nieprawidłowa wartość tkanki tłuszczowej w eksporcie"
            TakeoutKind.HEIGHT -> "Nieprawidłowa wartość wzrostu w eksporcie"
            TakeoutKind.RESTING_HEART_RATE -> "Nieprawidłowa wartość tętna spoczynkowego w eksporcie"
        } }
        TakeoutMeasurement(kind, timestamp(row.required("timestamp")), if (kind == TakeoutKind.WEIGHT || kind == TakeoutKind.HEIGHT) numeric / 1000.0 else numeric, row.required("data source"))
    }
}

internal fun readTakeoutSleeps(sleepReader: Reader, stageReader: Reader): List<TakeoutSleep> {
    val sleepFields = setOf("sleep_id", "sleep_start", "sleep_end", "start_utc_offset", "end_utc_offset", "data_source", "sleep_last_updated", "minutes_asleep")
    val stageFields = setOf("sleep_id", "sleep_stage_id", "sleep_stage_start", "sleep_stage_end", "sleep_stage_type")
    val sleepRows = readTakeoutCsv(sleepReader, sleepFields)
    val stageRows = readTakeoutCsv(stageReader, stageFields)
    val sleepHasUser = sleepRows.firstOrNull()?.containsKey("user_id") == true
    val stagesHaveUser = stageRows.firstOrNull()?.containsKey("user_id") == true
    if (sleepRows.isNotEmpty() && stageRows.isNotEmpty()) {
        require(sleepHasUser == stagesHaveUser) { "Nie można dopasować konta dla faz snu" }
    }
    val stagesBySleep = stageRows.groupBy { it.required("sleep_id") }
    val ids = sleepRows.map { it.required("sleep_id") }
    require(ids.distinct().size == ids.size) { "Powtórzone identyfikatory sesji snu" }
    val sleepIds = ids.toSet()
    require(stagesBySleep.keys.all { it in sleepIds }) { "Faza snu bez odpowiadającej sesji" }
    return sleepRows.map { row ->
        val id = row.required("sleep_id")
        val start = timestamp(row.required("sleep_start"))
        val end = timestamp(row.required("sleep_end"))
        require(start < end) { "Nieprawidłowy przedział snu" }
        val stages = stagesBySleep[id].orEmpty().map { stage ->
            if (sleepHasUser && stagesHaveUser) require(stage.required("user_id") == row.required("user_id")) { "Konto fazy snu nie pasuje do sesji" }
            val type = stage.required("sleep_stage_type")
            require(type in setOf("AWAKE", "LIGHT", "DEEP", "REM", "ASLEEP")) { "Nieobsługiwany rodzaj fazy snu" }
            val from = timestamp(stage.required("sleep_stage_start"))
            val to = timestamp(stage.required("sleep_stage_end"))
            require(from >= start && to <= end && from < to) { "Faza poza sesją snu" }
            TakeoutStage(stage.required("sleep_stage_id"), from, to, type)
        }.sortedBy { it.start }
        require(stages.map { it.id }.distinct().size == stages.size && stages.zipWithNext().all { (a, b) -> a.end <= b.start }) { "Niejednoznaczne fazy snu" }
        val minutes = row.getValue("minutes_asleep").takeIf { it.isNotBlank() }?.let {
            (it.toLongOrNull() ?: throw IllegalArgumentException("Nieprawidłowy czas snu")).also { n -> require(n >= 0) }
        }
        TakeoutSleep(id, start, end, offset(row.required("start_utc_offset")), offset(row.required("end_utc_offset")),
            row.required("data_source"), timestamp(row.required("sleep_last_updated")), minutes, stages,
            row["algorithm_version"]?.takeIf { it.isNotBlank() })
    }
}
