package pl.healthsync.data

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
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
    if (TakeoutType.SLEEP in types && archive.thirdPartySleepIds != null) {
        val sources = archive.thirdPartySleepIds
        takeoutSleepGroups(archive.sleeps).forEach { group ->
            val v2 = selectedSleepV2(group)
            if (v2 != null && group.none { it.id in sources }) {
                v2.forEach { row ->
                if (row.end >= from && row.end < until && classifySleep(row, emptyList()) == ImportDecision.INSERT) {
                    output.add(mapTakeoutSelection(TakeoutSelection.Sleep(row, group.minOf { it.start }, group.maxOf { it.end }), accountKey))
                }
                }
            }
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
            is HeightRecord -> result.getOrPut("height_avg") { mutableSetOf() }.add(record.time.atZone(zone).toLocalDate())
        }
    }
    return result
}
