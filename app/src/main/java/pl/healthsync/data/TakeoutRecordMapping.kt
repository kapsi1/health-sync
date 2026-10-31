package pl.healthsync.data

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.Record
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
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import pl.healthsync.domain.*
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Produces records only; callers must independently verify permissions, origin and conflicts. */
internal fun mapTakeoutSleep(row: TakeoutSleep, accountKey: String): SleepSessionRecord {
    require(row.algorithmVersion in setOf("sleep_session_v1", "sleep_session_v2") && classifySleep(row, emptyList()) == ImportDecision.INSERT)
    val stages = row.stages.map { stage ->
        val kind = when (stage.type) {
            "AWAKE" -> SleepSessionRecord.STAGE_TYPE_AWAKE
            "ASLEEP" -> SleepSessionRecord.STAGE_TYPE_SLEEPING
            "LIGHT" -> SleepSessionRecord.STAGE_TYPE_LIGHT
            "DEEP" -> SleepSessionRecord.STAGE_TYPE_DEEP
            "REM" -> SleepSessionRecord.STAGE_TYPE_REM
            else -> error("Nieobsługiwana faza snu")
        }
        SleepSessionRecord.Stage(stage.start, stage.end, kind)
    }
    val id = takeoutClientId("sleep", accountKey, row.id)
    return SleepSessionRecord(startTime = row.start, startZoneOffset = row.startOffset,
        endTime = row.end, endZoneOffset = row.endOffset, stages = stages,
        metadata = Metadata.unknownRecordingMethod(clientRecordId = id, clientRecordVersion = row.lastUpdated.toEpochMilli().coerceAtLeast(0)))
}

/** The export lacks a measurement ID and zone offset; preserve the instant and leave the offset unknown. */
internal fun mapTakeoutMeasurement(row: TakeoutMeasurement, accountKey: String): Record {
    require(classifyMeasurement(row, emptyList()) == ImportDecision.INSERT)
    val kind = when (row.kind) { TakeoutKind.WEIGHT -> "weight"; TakeoutKind.BODY_FAT -> "body_fat"; TakeoutKind.HEIGHT -> "height"; TakeoutKind.RESTING_HEART_RATE -> "resting_heart_rate" }
    val sourceKey = if (row.kind == TakeoutKind.RESTING_HEART_RATE) row.time.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate().toString() else row.time.toString()
    val id = takeoutClientId(kind, accountKey, sourceKey)
    val metadata = Metadata.unknownRecordingMethod(clientRecordId = id, clientRecordVersion = 0)
    return when (row.kind) {
        TakeoutKind.WEIGHT -> WeightRecord(time = row.time, zoneOffset = null, weight = Mass.kilograms(row.value), metadata = metadata)
        TakeoutKind.BODY_FAT -> BodyFatRecord(time = row.time, zoneOffset = null, percentage = Percentage(row.value), metadata = metadata)
        TakeoutKind.HEIGHT -> HeightRecord(time = row.time, zoneOffset = null, height = Length.meters(row.value), metadata = metadata)
        TakeoutKind.RESTING_HEART_RATE -> RestingHeartRateRecord(time = row.time, zoneOffset = null, beatsPerMinute = row.value.roundToLong(), metadata = metadata)
    }
}

internal fun mapTakeoutSelection(selection: TakeoutSelection, accountKey: String): TakeoutPlannedRecord = when (selection) {
    is TakeoutSelection.Measurement -> TakeoutPlannedRecord(mapTakeoutMeasurement(selection.row, accountKey))
    is TakeoutSelection.Sleep -> TakeoutPlannedRecord(mapTakeoutSleep(selection.row, accountKey), selection.sourceStart, selection.sourceEnd)
    is TakeoutSelection.Exercise -> mapTakeoutExercise(selection.row, accountKey)
    is TakeoutSelection.Nightly -> mapTakeoutNightly(selection.row, selection.sleep, accountKey)
}

/** The timestamp or interval denotes the related sleep, not a raw measurement time. */
internal fun mapTakeoutNightly(row: TakeoutNightly, sleep: TakeoutSleep, accountKey: String): TakeoutPlannedRecord {
    val value = requireNotNull(row.value)
    require(value.isFinite())
    val key = when (row.kind) { NightlyKind.HRV -> "night_hrv"; NightlyKind.BREATHING -> "night_breathing"; NightlyKind.SKIN_TEMPERATURE -> "night_skin" }
    val metadata = Metadata.unknownRecordingMethod(clientRecordId = takeoutClientId(key, accountKey, row.day.toString()), clientRecordVersion = 0)
    val record: Record = when (row.kind) {
        NightlyKind.HRV -> HeartRateVariabilityRmssdRecord(time = sleep.end, zoneOffset = sleep.endOffset, heartRateVariabilityMillis = value, metadata = metadata)
        NightlyKind.BREATHING -> RespiratoryRateRecord(time = sleep.end, zoneOffset = sleep.endOffset, rate = value, metadata = metadata)
        NightlyKind.SKIN_TEMPERATURE -> SkinTemperatureRecord(startTime = sleep.start, endTime = sleep.end, startZoneOffset = sleep.startOffset, endZoneOffset = sleep.endOffset,
            baseline = Temperature.celsius(requireNotNull(row.baseline)), measurementLocation = SkinTemperatureRecord.MEASUREMENT_LOCATION_WRIST,
            deltas = listOf(SkinTemperatureRecord.Delta(sleep.start, TemperatureDelta.celsius(value))), metadata = metadata)
    }
    return TakeoutPlannedRecord(record)
}

/** One estimated interval for the local calendar day, never an invented point measurement. */
internal fun mapTakeoutActivity(kind: TakeoutActivityKind, date: LocalDate, value: Double, accountKey: String): TakeoutPlannedRecord {
    require(value.isFinite() && value > 0) { "Nieprawidłowa dzienna suma aktywności" }
    val zone = ZoneId.of("Europe/Warsaw")
    val start = date.atStartOfDay(zone)
    val end = date.plusDays(1).atStartOfDay(zone)
    val key = when (kind) {
        TakeoutActivityKind.STEPS -> "steps"
        TakeoutActivityKind.DISTANCE -> "distance"
        TakeoutActivityKind.TOTAL_CALORIES -> "total_calories"
        TakeoutActivityKind.ACTIVE_CALORIES -> "active_calories"
    }
    val metadata = Metadata.unknownRecordingMethod(clientRecordId = takeoutClientId(key, accountKey, date.toString()), clientRecordVersion = 0)
    val record: Record = when (kind) {
        TakeoutActivityKind.STEPS -> {
            require(value % 1.0 == 0.0 && value <= Long.MAX_VALUE.toDouble()) { "Nieprawidłowa liczba kroków" }
            StepsRecord(startTime = start.toInstant(), startZoneOffset = start.offset, endTime = end.toInstant(), endZoneOffset = end.offset, count = value.toLong(), metadata = metadata)
        }
        TakeoutActivityKind.DISTANCE -> DistanceRecord(startTime = start.toInstant(), startZoneOffset = start.offset, endTime = end.toInstant(), endZoneOffset = end.offset, distance = Length.meters(value), metadata = metadata)
        TakeoutActivityKind.TOTAL_CALORIES -> TotalCaloriesBurnedRecord(startTime = start.toInstant(), startZoneOffset = start.offset, endTime = end.toInstant(), endZoneOffset = end.offset, energy = Energy.kilocalories(value), metadata = metadata)
        TakeoutActivityKind.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord(startTime = start.toInstant(), startZoneOffset = start.offset, endTime = end.toInstant(), endZoneOffset = end.offset, energy = Energy.kilocalories(value), metadata = metadata)
    }
    return TakeoutPlannedRecord(record)
}

/** An unknown activity or unsupported source details must be reviewed rather than guessed. */
internal fun takeoutExerciseType(name: String): Int? = when (name) {
    "Outdoor Walk" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
    "Weightlifting" -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
    "Weights" -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
    "Treadmill Run" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
    "Bike", "Outdoor Bike" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
    "Spinning" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
    "Dancing" -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
    "Aerobics", "Aerobic Workout" -> ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS
    "Elliptical" -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
    "Rollerblading" -> ExerciseSessionRecord.EXERCISE_TYPE_SKATING
    "Sport" -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    else -> null
}

internal fun mapTakeoutExercise(row: TakeoutExercise, accountKey: String): TakeoutPlannedRecord {
    require(row.logType in setOf("TRACKER", "AUTO_DETECTED", "MANUAL") && row.deletionReason == "UNSPECIFIED" && !row.unsupportedDetails) { "Nie można bezpiecznie odtworzyć tego treningu" }
    val type = requireNotNull(takeoutExerciseType(row.activity)) { "Nieznany typ treningu" }
    val metadata = Metadata.unknownRecordingMethod(clientRecordId = takeoutClientId("exercise", accountKey, row.id), clientRecordVersion = row.lastUpdated.toEpochMilli().coerceAtLeast(0))
    return TakeoutPlannedRecord(ExerciseSessionRecord(startTime = row.start, startZoneOffset = row.offset, endTime = row.end,
        endZoneOffset = row.offset, exerciseType = type, title = row.activity, metadata = metadata))
}
