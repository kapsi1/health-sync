package pl.healthsync

import androidx.health.connect.client.records.SleepSessionRecord

internal val sleepMetricIds = setOf("sleep_duration", "sleep_awake")

internal fun SleepSessionRecord.toSleepInterval() = SleepInterval(startTime, endTime, stages.map { stage ->
    val state = when (stage.stage) {
        SleepSessionRecord.STAGE_TYPE_SLEEPING, SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_DEEP, SleepSessionRecord.STAGE_TYPE_REM -> SleepState.ASLEEP
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepState.AWAKE
        else -> SleepState.UNKNOWN
    }
    SleepStageInterval(stage.startTime, stage.endTime, state)
})
