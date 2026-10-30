package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.Instant
import java.time.ZoneOffset

class TakeoutCompareTest {
    private val at = Instant.parse("2026-10-14T07:00:00Z")
    private val measurement = TakeoutMeasurement(TakeoutKind.WEIGHT, at, 70.5, "Google Health App")

    @Test fun `weight compares timestamp and value without excluding another measurement on the same day`() {
        assertEquals(ImportDecision.INSERT, classifyMeasurement(measurement, listOf(ExistingMeasurement(TakeoutKind.WEIGHT, at.plusSeconds(3600), 70.5))))
        assertEquals(ImportDecision.ALREADY_PRESENT, classifyMeasurement(measurement, listOf(ExistingMeasurement(TakeoutKind.WEIGHT, at, 70.5))))
        assertEquals(ImportDecision.CONFLICT, classifyMeasurement(measurement, listOf(ExistingMeasurement(TakeoutKind.WEIGHT, at, 71.0))))
    }
    @Test fun `previously exported Connect data is never inserted again`() {
        assertEquals(ImportDecision.ORIGINATES_IN_CONNECT, classifyMeasurement(measurement.copy(source = "Fit Health Connect"), emptyList()))
        assertEquals(ImportDecision.UNKNOWN_SOURCE, classifyMeasurement(measurement.copy(source = "unrecognized"), emptyList()))
    }
    @Test fun `sleep overlapping existing session requires resolution`() {
        val stage = TakeoutStage("stage", at, at.plusSeconds(3600), "LIGHT")
        val sleep = TakeoutSleep("s", at, at.plusSeconds(3600), ZoneOffset.UTC, ZoneOffset.UTC, "DERIVED", at, 60, listOf(stage))
        assertEquals(ImportDecision.INSERT, classifySleep(sleep, emptyList()))
        assertEquals(ImportDecision.CONFLICT, classifySleep(sleep, listOf(ExistingSleep(at, at.plusSeconds(3600)))))
        assertEquals(ImportDecision.ALREADY_PRESENT, classifySleep(sleep.copy(stages = listOf(stage)),
            listOf(ExistingSleep(at, at.plusSeconds(3600), listOf(ExistingSleepStage(at, at.plusSeconds(3600), "LIGHT"))))))
        assertEquals(ImportDecision.CONFLICT, classifySleep(sleep, listOf(ExistingSleep(at.plusSeconds(10), at.plusSeconds(3600)))))
        assertEquals(ImportDecision.INSERT, classifySleep(sleep, listOf(ExistingSleep(at.plusSeconds(3600), at.plusSeconds(7200)))))
        assertEquals(ImportDecision.CONFLICT, classifySleep(sleep.copy(minutesAsleep = 10), emptyList()))
        assertEquals(ImportDecision.CONFLICT, classifySleep(sleep.copy(stages = emptyList()), emptyList()))
    }
    @Test fun `client ids are deterministic and distinct by kind and source`() {
        val first = takeoutClientId("sleep", "account", "example")
        assertEquals(first, takeoutClientId("sleep", "account", "example"))
        assertNotEquals(first, takeoutClientId("sleep", "account", "another"))
        assertNotEquals(first, takeoutClientId("weight", "account", "example"))
        assertNotEquals(first, takeoutClientId("sleep", "other-account", "example"))
        assertFalse(first.contains("example"))
    }
}
