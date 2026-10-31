package pl.healthsync.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import pl.healthsync.domain.*
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

internal enum class TakeoutType(val label: String) { SLEEP("Sen"), WEIGHT("Waga"), FAT("Tkanka tłuszczowa"), HEIGHT("Wzrost"), RESTING_HEART_RATE("Tętno spoczynkowe"), EXERCISE("Treningi"), HRV("HRV podczas snu"), BREATHING("Oddechy/min podczas snu"), SKIN_TEMPERATURE("Wahania temperatury skóry") }
internal data class SkippedTakeoutEntry(val type: TakeoutType, val time: java.time.Instant, val description: String, val reason: String, val end: java.time.Instant? = null)
internal sealed interface TakeoutSelection {
    data class Measurement(val row: TakeoutMeasurement) : TakeoutSelection
    data class Sleep(val row: TakeoutSleep, val sourceStart: java.time.Instant, val sourceEnd: java.time.Instant) : TakeoutSelection
    data class Exercise(val row: TakeoutExercise) : TakeoutSelection
    data class Nightly(val row: TakeoutNightly, val sleep: TakeoutSleep) : TakeoutSelection
}
internal data class TakeoutPreview(val counts: Map<TakeoutType, Map<ImportDecision, Int>>, val candidates: List<TakeoutSelection> = emptyList(), val skipped: List<SkippedTakeoutEntry> = emptyList())

internal fun takeoutReadPermissions(types: Set<TakeoutType>): Set<String> = buildSet {
    if (TakeoutType.SLEEP in types) add(HealthPermission.getReadPermission(SleepSessionRecord::class))
    if (TakeoutType.WEIGHT in types) add(HealthPermission.getReadPermission(WeightRecord::class))
    if (TakeoutType.FAT in types) add(HealthPermission.getReadPermission(BodyFatRecord::class))
    if (TakeoutType.HEIGHT in types) add(HealthPermission.getReadPermission(HeightRecord::class))
    if (TakeoutType.RESTING_HEART_RATE in types) add(HealthPermission.getReadPermission(RestingHeartRateRecord::class))
    if (TakeoutType.EXERCISE in types) add(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
    if (TakeoutType.HRV in types) { add(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)); add(HealthPermission.getReadPermission(SleepSessionRecord::class)) }
    if (TakeoutType.BREATHING in types) { add(HealthPermission.getReadPermission(RespiratoryRateRecord::class)); add(HealthPermission.getReadPermission(SleepSessionRecord::class)) }
    if (TakeoutType.SKIN_TEMPERATURE in types) add(HealthPermission.getReadPermission(SkinTemperatureRecord::class))
    // Sleep sessions may start before the requested period, so the comparison needs all history.
    add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
}

/** Read-only: an incomplete or unauthorized read must never be presented as missing data. */
internal class TakeoutInspector(private val client: HealthConnectClient, private val excludedExerciseIds: Set<String> = emptySet()) {
    private val zone = ZoneId.of("Europe/Warsaw")

    suspend fun inspect(archive: TakeoutArchive, types: Set<TakeoutType>, start: LocalDate, end: LocalDate): TakeoutPreview {
        require(types.isNotEmpty() && start <= end) { "Wybierz rodzaje danych i poprawny zakres dat." }
        val missingFiles = types.filterNot { type -> when (type) {
            TakeoutType.SLEEP -> "sleep" in archive.foundTypes && "stages" in archive.foundTypes
            TakeoutType.WEIGHT -> "weight" in archive.foundTypes
            TakeoutType.FAT -> "fat" in archive.foundTypes
            TakeoutType.HEIGHT -> "height" in archive.foundTypes
            TakeoutType.RESTING_HEART_RATE -> "resting_heart_rate" in archive.foundTypes
            TakeoutType.EXERCISE -> "exercise" in archive.foundTypes
            TakeoutType.HRV -> "HRV" in archive.foundTypes && "sleep" in archive.foundTypes
            TakeoutType.BREATHING -> "BREATHING" in archive.foundTypes && "sleep" in archive.foundTypes
            TakeoutType.SKIN_TEMPERATURE -> "SKIN_TEMPERATURE" in archive.foundTypes && "sleep" in archive.foundTypes
        } }
        require(missingFiles.isEmpty()) { "Brak plików w archiwum dla: ${missingFiles.joinToString { it.label }}." }
        check(client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            "Na tym urządzeniu nie można odczytać całej historii wymaganej do porównania."
        }
        check(client.permissionController.getGrantedPermissions().containsAll(takeoutReadPermissions(types))) { "Brakuje zgody na odczyt wybranych danych lub starszej historii." }
        val from = start.atStartOfDay(zone).toInstant()
        val until = end.plusDays(1).atStartOfDay(zone).toInstant()
        val counts = mutableMapOf<TakeoutType, Map<ImportDecision, Int>>()
        val skipped = mutableListOf<SkippedTakeoutEntry>()
        val candidates = mutableListOf<TakeoutSelection>()
        fun addMeasurements(type: TakeoutType, compared: List<Pair<TakeoutMeasurement, ImportDecision>>) {
            val duplicates = compared.filter { it.first.source == "Google Health App" }.groupingBy { it.first.time }.eachCount()
            compared.filter { it.second !in setOf(ImportDecision.INSERT, ImportDecision.ALREADY_PRESENT, ImportDecision.ORIGINATES_IN_CONNECT) }.forEach { (row, decision) ->
                val reason = when (decision) {
                    ImportDecision.ALREADY_PRESENT -> if (type == TakeoutType.RESTING_HEART_RATE) "W Connect jest już wpis z tego dnia" else "Taki pomiar jest już w Connect"
                    ImportDecision.ORIGINATES_IN_CONNECT -> "Eksport oznacza wpis jako pochodzący z Connect; jego obecność nie została sprawdzona"
                    ImportDecision.UNKNOWN_SOURCE -> "Nierozpoznane źródło: ${row.source}"
                    ImportDecision.CONFLICT -> if (type == TakeoutType.RESTING_HEART_RATE) "Wiele podsumowań tętna spoczynkowego w eksporcie dla tego dnia" else if ((duplicates[row.time] ?: 0) > 1) "Wiele pomiarów w eksporcie ma ten sam czas" else "W Connect jest pomiar o tym samym czasie, ale innej wartości"
                    else -> "Wpis pominięto"
                }
                skipped.add(SkippedTakeoutEntry(type, row.time, "", reason))
            }
        }
        if (TakeoutType.WEIGHT in types) {
            val source = archive.weights.filter { it.time >= from && it.time < until }
            val existing = if (source.isEmpty()) emptyList() else readAll(WeightRecord::class, TimeRangeFilter.between(from, until)).map { ExistingMeasurement(TakeoutKind.WEIGHT, it.time, it.weight.inKilograms) }
            val compared = compareMeasurements(source, existing)
            addMeasurements(TakeoutType.WEIGHT, compared)
            counts[TakeoutType.WEIGHT] = compared.map { it.second }.groupingBy { it }.eachCount()
            candidates.addAll(compared.filter { it.second == ImportDecision.INSERT }.map { TakeoutSelection.Measurement(it.first) })
        }
        if (TakeoutType.FAT in types) {
            val source = archive.bodyFat.filter { it.time >= from && it.time < until }
            val existing = if (source.isEmpty()) emptyList() else readAll(BodyFatRecord::class, TimeRangeFilter.between(from, until)).map { ExistingMeasurement(TakeoutKind.BODY_FAT, it.time, it.percentage.value) }
            val compared = compareMeasurements(source, existing)
            addMeasurements(TakeoutType.FAT, compared)
            counts[TakeoutType.FAT] = compared.map { it.second }.groupingBy { it }.eachCount()
            candidates.addAll(compared.filter { it.second == ImportDecision.INSERT }.map { TakeoutSelection.Measurement(it.first) })
        }
        if (TakeoutType.HEIGHT in types) {
            val source = archive.heights.filter { it.time >= from && it.time < until }
            val existing = if (source.isEmpty()) emptyList() else readAll(HeightRecord::class, TimeRangeFilter.between(from, until)).map { ExistingMeasurement(TakeoutKind.HEIGHT, it.time, it.height.inMeters) }
            val compared = compareMeasurements(source, existing)
            addMeasurements(TakeoutType.HEIGHT, compared)
            counts[TakeoutType.HEIGHT] = compared.map { it.second }.groupingBy { it }.eachCount()
            candidates.addAll(compared.filter { it.second == ImportDecision.INSERT }.map { TakeoutSelection.Measurement(it.first) })
        }
        if (TakeoutType.RESTING_HEART_RATE in types) {
            val source = archive.restingHeartRates.filter { it.time >= from && it.time < until }
            val existing = if (source.isEmpty()) emptyList() else readAll(RestingHeartRateRecord::class, TimeRangeFilter.between(from, until)).map { ExistingMeasurement(TakeoutKind.RESTING_HEART_RATE, it.time, it.beatsPerMinute.toDouble()) }
            val compared = compareDailyRestingHeartRates(source, existing, zone)
            addMeasurements(TakeoutType.RESTING_HEART_RATE, compared)
            counts[TakeoutType.RESTING_HEART_RATE] = compared.map { it.second }.groupingBy { it }.eachCount()
            candidates.addAll(compared.filter { it.second == ImportDecision.INSERT }.map { TakeoutSelection.Measurement(it.first) })
        }
        if (TakeoutType.SLEEP in types) {
            val source = archive.sleeps.filter { it.end >= from && it.end < until }
            // Query the full earlier history: even a session beginning before the chosen range can overlap.
            val existing = if (source.isEmpty()) emptyList() else readAll(SleepSessionRecord::class, TimeRangeFilter.after(java.time.Instant.parse("1900-01-01T00:00:00Z"))).map { row ->
                ExistingSleep(row.startTime, row.endTime, row.stages.map { stage ->
                    ExistingSleepStage(stage.startTime, stage.endTime, when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "ASLEEP"
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
                        SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
                        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
                        else -> "UNKNOWN"
                    })
                })
            }
            val decisions = resolveSleepV2(archive.sleeps, existing, archive.thirdPartySleepIds)
            val groupsById = takeoutSleepGroups(archive.sleeps).flatMap { group -> group.map { it.id to group } }.toMap()
            candidates.addAll(source.filter { decisions.getValue(it.id).decision == ImportDecision.INSERT }.map { row ->
                val group = groupsById.getValue(row.id)
                TakeoutSelection.Sleep(row, group.minOf { it.start }, group.maxOf { it.end })
            })
            val comparisons = source.map { candidate -> decisions.getValue(candidate.id) }
            counts[TakeoutType.SLEEP] = comparisons.groupingBy { it.decision }.eachCount()
            source.forEach { row ->
                val comparison = decisions.getValue(row.id)
                if (comparison.decision !in setOf(ImportDecision.INSERT, ImportDecision.ALREADY_PRESENT, ImportDecision.SUPERSEDED)) {
                    val reason = comparison.reason?.label ?: when (comparison.decision) {
                        ImportDecision.SUPERSEDED -> "Wersja v1 jest pomijana; decyzję dla v2 podano osobno"
                        ImportDecision.ALREADY_PRESENT -> "Taka sesja jest już w Connect"
                        ImportDecision.UNKNOWN_SOURCE -> "Nie można potwierdzić źródła sesji lub grupy sesji"
                        else -> "Sesja wymaga sprawdzenia"
                    }
                    skipped.add(SkippedTakeoutEntry(TakeoutType.SLEEP, row.start, row.algorithmVersion ?: "brak wersji", reason, row.end))
                }
            }
        }
        if (TakeoutType.EXERCISE in types) {
            val all = archive.readExercises().filterNot { it.id in excludedExerciseIds }
            val source = all.filter { it.start >= from && it.start < until }
            val decisions = selectedTakeoutExercises(all)
            val existing = if (source.isEmpty()) emptyList() else readAll(ExerciseSessionRecord::class, TimeRangeFilter.after(java.time.Instant.parse("1900-01-01T00:00:00Z")))
            val compared = source.map { row ->
                val overlaps = existing.filter { it.startTime < row.end && row.start < it.endTime }
                val matches = overlaps.filter { exerciseMatchesTakeout(row, it) }
                val decision = when {
                    matches.size == 1 -> ImportDecision.ALREADY_PRESENT
                    matches.size > 1 -> ImportDecision.CONFLICT
                    decisions.getValue(row.id) != ImportDecision.INSERT -> decisions.getValue(row.id)
                    row.unsupportedDetails || row.deletionReason != "UNSPECIFIED" || row.logType !in setOf("TRACKER", "AUTO_DETECTED", "MANUAL") || takeoutExerciseType(row.activity) == null -> ImportDecision.CONFLICT
                    overlaps.isEmpty() -> ImportDecision.INSERT
                    else -> ImportDecision.CONFLICT
                }
                row to decision
            }
            counts[TakeoutType.EXERCISE] = compared.map { it.second }.groupingBy { it }.eachCount()
            compared.filter { it.second !in setOf(ImportDecision.INSERT, ImportDecision.ALREADY_PRESENT, ImportDecision.SUPERSEDED) }.forEach { (row, decision) ->
                val reason = when (decision) {
                    ImportDecision.SUPERSEDED -> "Wybrano nakładający się spacer z opaski"
                    ImportDecision.ALREADY_PRESENT -> "Taki trening jest już w Connect"
                    else -> if (decisions[row.id] == ImportDecision.CONFLICT) "Niejednoznacznie nakładające się treningi w Takeout" else if (row.unsupportedDetails || row.deletionReason != "UNSPECIFIED" || row.logType !in setOf("TRACKER", "AUTO_DETECTED", "MANUAL") || takeoutExerciseType(row.activity) == null) "Nieobsługiwany typ, źródło lub szczegóły treningu" else "Nakładający się trening w Connect różni się od eksportu"
                }
                skipped.add(SkippedTakeoutEntry(TakeoutType.EXERCISE, row.start, row.activity, reason))
            }
            candidates.addAll(compared.filter { it.second == ImportDecision.INSERT }.map { TakeoutSelection.Exercise(it.first) })
        }
        for ((type, kind) in listOf(TakeoutType.HRV to NightlyKind.HRV, TakeoutType.BREATHING to NightlyKind.BREATHING, TakeoutType.SKIN_TEMPERATURE to NightlyKind.SKIN_TEMPERATURE)) {
            if (type !in types) continue
            if (kind == NightlyKind.SKIN_TEMPERATURE) check(client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) { "Health Connect nie obsługuje temperatury skóry" }
            val source = archive.readNightly(kind).filter { it.day in start..end }
            val days = source.groupingBy { it.day }.eachCount()
            val filter = TimeRangeFilter.after(java.time.Instant.parse("1900-01-01T00:00:00Z"))
            val existingDays: Set<LocalDate> = when (kind) {
                NightlyKind.HRV -> readAll(HeartRateVariabilityRmssdRecord::class, filter).map { it.time.atZone(zone).toLocalDate() }.toSet()
                NightlyKind.BREATHING -> readAll(RespiratoryRateRecord::class, filter).map { it.time.atZone(zone).toLocalDate() }.toSet()
                NightlyKind.SKIN_TEMPERATURE -> readAll(SkinTemperatureRecord::class, filter).map { it.endTime.atZone(zone).toLocalDate() }.toSet()
            }
            val compared = source.map { row ->
                val sleep = nightlySleepForDay(row.day, archive.sleeps, archive.thirdPartySleepIds, zone)
                val decision = when {
                    row.day in existingDays -> ImportDecision.ALREADY_PRESENT
                    days.getValue(row.day) != 1 || sleep == null || row.value == null -> ImportDecision.CONFLICT
                    runCatching { mapTakeoutNightly(row, sleep, "validation") }.isFailure -> ImportDecision.CONFLICT
                    else -> ImportDecision.INSERT
                }
                if (decision == ImportDecision.INSERT) candidates.add(TakeoutSelection.Nightly(row, requireNotNull(sleep)))
                else if (decision != ImportDecision.ALREADY_PRESENT) {
                    val reason = when {
                        row.day in existingDays -> "W Connect jest już wpis z tego dnia"
                        days.getValue(row.day) != 1 -> "Wiele podsumowań w eksporcie dla tego dnia"
                        sleep == null -> "Brak jednoznacznej sesji snu v2 dłuższej niż 3 godziny"
                        row.value == null -> "Brak wartości lub temperatury bazowej w eksporcie"
                        else -> "Nieprawidłowa wartość podsumowania"
                    }
                    skipped.add(SkippedTakeoutEntry(type, row.day.atStartOfDay(zone).toInstant(), "", reason))
                }
                decision
            }
            counts[type] = compared.groupingBy { it }.eachCount()
        }
        return TakeoutPreview(counts, candidates, skipped.sortedBy { it.time })
    }

    private fun compareMeasurements(source: List<TakeoutMeasurement>, existing: List<ExistingMeasurement>): List<Pair<TakeoutMeasurement, ImportDecision>> {
        val frequencies = source.filter { it.source == "Google Health App" }.groupingBy { it.time }.eachCount()
        val existingByKey = existing.groupBy { it.kind to it.time }
        return source.map { row -> row to
            if (row.source == "Google Health App" && frequencies.getValue(row.time) > 1) ImportDecision.CONFLICT
            else classifyMeasurement(row, existingByKey[row.kind to row.time].orEmpty())
        }
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val result = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, filter, pageToken = token))
            result.addAll(page.records)
            check(result.size <= 100_000) { "Zbyt dużo rekordów do bezpiecznego porównania." }
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return result
    }
}

internal fun exerciseMatchesTakeout(row: TakeoutExercise, existing: ExerciseSessionRecord): Boolean =
    row.start.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) == existing.startTime.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) &&
    row.end.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) == existing.endTime.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) &&
    (existing.exerciseType == takeoutExerciseType(row.activity) || (row.activity == "Weights" && existing.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING))
