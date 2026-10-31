package pl.healthsync.data

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import pl.healthsync.domain.*
import java.time.LocalDate
import java.time.ZoneId

/** Reconstruct records from the same verified archive, never from newly computed import decisions. */
internal fun reconstructTakeoutPlan(archive: TakeoutArchive, start: LocalDate, end: LocalDate, types: Set<TakeoutType>, accountKey: String): List<TakeoutPlannedRecord> {
    require(start <= end && accountKey.isNotBlank())
    val zone = ZoneId.of("Europe/Warsaw")
    val from = start.atStartOfDay(zone).toInstant()
    val until = end.plusDays(1).atStartOfDay(zone).toInstant()
    val output = mutableListOf<TakeoutPlannedRecord>()
    if (TakeoutType.WEIGHT in types) archive.weights.filter { it.time >= from && it.time < until && it.source == "Google Health App" }.forEach {
        output.add(mapTakeoutSelection(TakeoutSelection.Measurement(it), accountKey))
    }
    if (TakeoutType.FAT in types) archive.bodyFat.filter { it.time >= from && it.time < until && it.source == "Google Health App" }.forEach {
        output.add(mapTakeoutSelection(TakeoutSelection.Measurement(it), accountKey))
    }
    if (TakeoutType.HEIGHT in types) archive.heights.filter { it.time >= from && it.time < until && it.source == "Google Health App" }.forEach {
        output.add(mapTakeoutSelection(TakeoutSelection.Measurement(it), accountKey))
    }
    if (TakeoutType.RESTING_HEART_RATE in types) {
        val rows = archive.restingHeartRates.filter { it.time >= from && it.time < until }
        compareDailyRestingHeartRates(rows, emptyList(), zone).filter { it.second == ImportDecision.INSERT }.forEach {
            output.add(mapTakeoutSelection(TakeoutSelection.Measurement(it.first), accountKey))
        }
    }
    if (TakeoutType.SLEEP in types && archive.thirdPartySleepIds != null) {
        val sources = archive.thirdPartySleepIds
        takeoutSleepGroups(archive.sleeps).forEach { group ->
            val v2 = selectedSleepForImport(group)
            if (v2 != null && group.none { it.id in sources }) {
                v2.forEach { row ->
                if (row.end >= from && row.end < until && classifySleep(row, emptyList()) == ImportDecision.INSERT) {
                    output.add(mapTakeoutSelection(TakeoutSelection.Sleep(row, group.minOf { it.start }, group.maxOf { it.end }), accountKey))
                }
                }
            }
        }
    }
    if (TakeoutType.EXERCISE in types) {
        val rows = archive.readExercises()
        // A frozen journal decides which IDs to resume; do not recompute mutable source exclusions.
        rows.filter { it.start >= from && it.start < until && !it.unsupportedDetails && it.deletionReason == "UNSPECIFIED" && it.logType in setOf("TRACKER", "AUTO_DETECTED", "MANUAL") && takeoutExerciseType(it.activity) != null }.forEach {
            output.add(mapTakeoutExercise(it, accountKey))
        }
    }
    for ((type, kind) in listOf(TakeoutType.HRV to NightlyKind.HRV, TakeoutType.BREATHING to NightlyKind.BREATHING, TakeoutType.SKIN_TEMPERATURE to NightlyKind.SKIN_TEMPERATURE)) {
        if (type !in types) continue
        val rows = archive.readNightly(kind).filter { it.day in start..end }
        val counts = rows.groupingBy { it.day }.eachCount()
        rows.filter { counts[it.day] == 1 && it.value != null }.forEach { row ->
            val sleep = nightlySleepForDay(row.day, archive.sleeps, archive.thirdPartySleepIds, zone)
            if (sleep != null) runCatching { mapTakeoutNightly(row, sleep, accountKey) }.getOrNull()?.let(output::add)
        }
    }
    return output
}

/** Reject any missing, changed or duplicated planned identity during resume. */
internal fun matchRecordedTakeoutPlan(all: List<TakeoutPlannedRecord>, stored: List<TakeoutImportRecord>): List<TakeoutPlannedRecord> {
    require(stored.isNotEmpty() && stored.map { it.clientRecordId }.distinct().size == stored.size)
    val grouped = all.groupBy { takeoutPayloadCandidate(it.record).clientId }
    return stored.map { entry ->
        val matches = grouped[entry.clientRecordId].orEmpty()
        check(matches.size == 1) { "Nie można jednoznacznie odtworzyć planu importu" }
        val candidate = takeoutPayloadCandidate(matches.single().record)
        check(candidate.kind == entry.kind && candidate.contentHash == entry.contentHash) { "Zawartość planu importu zmieniła się" }
        matches.single()
    }
}

internal fun affectedTakeoutDates(plan: List<TakeoutPlannedRecord>): Map<String, Set<LocalDate>> {
    val zone = ZoneId.of("Europe/Warsaw")
    val result = mutableMapOf<String, MutableSet<LocalDate>>()
    plan.forEach { item ->
        when (val record = item.record) {
            is SleepSessionRecord -> {
                val date = record.endTime.atZone(zone).toLocalDate()
                result.getOrPut("sleep_duration") { mutableSetOf() }.add(date)
                result.getOrPut("sleep_awake") { mutableSetOf() }.add(date)
            }
            is WeightRecord -> result.getOrPut("weight_avg") { mutableSetOf() }.add(record.time.atZone(zone).toLocalDate())
            is BodyFatRecord -> result.getOrPut("body_fat") { mutableSetOf() }.add(record.time.atZone(zone).toLocalDate())
            // Older sessions may still contain height; their records remain undoable, but height is no longer exported.
            is HeightRecord -> Unit
            is RestingHeartRateRecord -> result.getOrPut("resting_heart_rate") { mutableSetOf() }.add(record.time.atZone(zone).toLocalDate())
            is StepsRecord -> result.getOrPut("steps") { mutableSetOf() }.add(record.startTime.atZone(zone).toLocalDate())
            is DistanceRecord -> result.getOrPut("distance") { mutableSetOf() }.add(record.startTime.atZone(zone).toLocalDate())
            is TotalCaloriesBurnedRecord -> result.getOrPut("total_calories") { mutableSetOf() }.add(record.startTime.atZone(zone).toLocalDate())
            is ActiveCaloriesBurnedRecord -> result.getOrPut("active_calories") { mutableSetOf() }.add(record.startTime.atZone(zone).toLocalDate())
            is ExerciseSessionRecord -> {
                var day = record.startTime.atZone(zone).toLocalDate()
                val last = record.endTime.minusNanos(1).atZone(zone).toLocalDate()
                require(java.time.temporal.ChronoUnit.DAYS.between(day, last) <= 366) { "Zbyt długi trening" }
                while (day <= last) { result.getOrPut("exercise_duration") { mutableSetOf() }.add(day); day = day.plusDays(1) }
            }
            is HeartRateVariabilityRmssdRecord -> result.getOrPut("hrv_rmssd") { mutableSetOf() }.add(record.time.atZone(zone).toLocalDate())
            is RespiratoryRateRecord -> result.getOrPut("respiratory_rate_sleep") { mutableSetOf() }.add(record.time.atZone(zone).toLocalDate())
            is SkinTemperatureRecord -> result.getOrPut("skin_temperature_delta") { mutableSetOf() }.add(record.endTime.atZone(zone).toLocalDate())
        }
    }
    return result
}
