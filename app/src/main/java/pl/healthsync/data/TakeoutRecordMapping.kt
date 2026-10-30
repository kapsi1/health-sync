package pl.healthsync.data

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Length
import pl.healthsync.domain.*

/** Produces records only; callers must independently verify permissions, origin and conflicts. */
internal fun mapTakeoutSleep(row: TakeoutSleep, accountKey: String): SleepSessionRecord {
    require(row.algorithmVersion == "sleep_session_v2" && classifySleep(row, emptyList()) == ImportDecision.INSERT)
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
    val kind = when (row.kind) { TakeoutKind.WEIGHT -> "weight"; TakeoutKind.BODY_FAT -> "body_fat"; TakeoutKind.HEIGHT -> "height" }
    val id = takeoutClientId(kind, accountKey, row.time.toString())
    val metadata = Metadata.unknownRecordingMethod(clientRecordId = id, clientRecordVersion = 0)
    return when (row.kind) {
        TakeoutKind.WEIGHT -> WeightRecord(time = row.time, zoneOffset = null, weight = Mass.kilograms(row.value), metadata = metadata)
        TakeoutKind.BODY_FAT -> BodyFatRecord(time = row.time, zoneOffset = null, percentage = Percentage(row.value), metadata = metadata)
        TakeoutKind.HEIGHT -> HeightRecord(time = row.time, zoneOffset = null, height = Length.meters(row.value), metadata = metadata)
    }
}

internal fun mapTakeoutSelection(selection: TakeoutSelection, accountKey: String): TakeoutPlannedRecord = when (selection) {
    is TakeoutSelection.Measurement -> TakeoutPlannedRecord(mapTakeoutMeasurement(selection.row, accountKey))
    is TakeoutSelection.Sleep -> TakeoutPlannedRecord(mapTakeoutSleep(selection.row, accountKey), selection.sourceStart, selection.sourceEnd)
}
