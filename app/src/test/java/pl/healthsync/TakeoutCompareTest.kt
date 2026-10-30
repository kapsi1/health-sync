package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.Instant
import java.time.ZoneOffset

class TakeoutCompareTest {
    @Test fun `daily resting heart rate skips existing day and duplicate source days`() {
        val zone = java.time.ZoneId.of("Europe/Warsaw")
        val time = Instant.parse("2026-06-12T00:00:00Z")
        val row = TakeoutMeasurement(TakeoutKind.RESTING_HEART_RATE, time, 60.0, "Google Health App")
        val existing = ExistingMeasurement(TakeoutKind.RESTING_HEART_RATE, time.plusSeconds(36000), 65.0)
        assertEquals(ImportDecision.ALREADY_PRESENT, compareDailyRestingHeartRates(listOf(row), listOf(existing), zone).single().second)
        assertTrue(compareDailyRestingHeartRates(listOf(row, row.copy(time = time.plusSeconds(60))), emptyList(), zone).all { it.second == ImportDecision.CONFLICT })
        assertEquals(ImportDecision.ORIGINATES_IN_CONNECT, compareDailyRestingHeartRates(listOf(row.copy(source = "Fit Health Connect")), emptyList(), zone).single().second)
        assertEquals(ImportDecision.INSERT, compareDailyRestingHeartRates(listOf(row), emptyList(), zone).single().second)
    }
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
        assertEquals(ImportDecision.CONFLICT, classifySleep(sleep.copy(stages = listOf(stage.copy(start = at.plusSeconds(1)))), emptyList()))
    }
    @Test fun `client ids are deterministic and distinct by kind and source`() {
        val first = takeoutClientId("sleep", "account", "example")
        assertEquals(first, takeoutClientId("sleep", "account", "example"))
        assertNotEquals(first, takeoutClientId("sleep", "account", "another"))
        assertNotEquals(first, takeoutClientId("weight", "account", "example"))
        assertNotEquals(first, takeoutClientId("sleep", "other-account", "example"))
        assertFalse(first.contains("example"))
    }
    @Test fun `all members of overlapping sleep groups conflict but adjacent sessions do not`() {
        fun session(id: String, start: Long, end: Long) = TakeoutSleep(id, at.plusSeconds(start), at.plusSeconds(end), ZoneOffset.UTC, ZoneOffset.UTC, "DERIVED", at, null, emptyList())
        assertEquals(setOf("a", "b", "c"), overlappingTakeoutSleepIds(listOf(session("c", 19, 30), session("a", 0, 10), session("d", 30, 40), session("b", 5, 20))))
    }
    @Test fun `sleep conflict reasons distinguish boundaries and absent stages`() {
        val stage = TakeoutStage("s", at, at.plusSeconds(3600), "LIGHT")
        val candidate = TakeoutSleep("id", at, at.plusSeconds(3600), ZoneOffset.UTC, ZoneOffset.UTC, "DERIVED", at, 60, listOf(stage))
        assertEquals(SleepConflictReason.BOUNDS_DIFFER, compareSleep(candidate, listOf(ExistingSleep(at.plusSeconds(1), at.plusSeconds(3600)))).reason)
        assertEquals(SleepConflictReason.EXISTING_NO_STAGES, compareSleep(candidate, listOf(ExistingSleep(at, at.plusSeconds(3600)))).reason)
        assertEquals(SleepConflictReason.STAGES_DIFFER, compareSleep(candidate, listOf(ExistingSleep(at, at.plusSeconds(3600), listOf(ExistingSleepStage(at, at.plusSeconds(3600), "REM"))))).reason)
        assertEquals(SleepConflictReason.SOURCE_OVERLAP, compareSleep(candidate, emptyList(), true).reason)
    }

    @Test fun `v2 is selected only for clear pairs and multiple v2 are conflicts`() {
        val stage = TakeoutStage("stage", at, at.plusSeconds(3600), "LIGHT")
        val base = TakeoutSleep("v1", at, at.plusSeconds(3600), ZoneOffset.UTC, ZoneOffset.UTC, "DERIVED", at, 60, listOf(stage), "sleep_session_v1")
        val v2 = base.copy(id = "v2", algorithmVersion = "sleep_session_v2")
        val decisions = resolveSleepV2(listOf(base, v2), emptyList())
        assertEquals(ImportDecision.SUPERSEDED, decisions.getValue("v1").decision)
        assertEquals(ImportDecision.INSERT, decisions.getValue("v2").decision)
        val ambiguous = resolveSleepV2(listOf(base, v2, v2.copy(id = "v2b")), emptyList())
        assertTrue(ambiguous.values.all { it.reason == SleepConflictReason.NO_UNIQUE_V2 })
        assertEquals(SleepConflictReason.NO_UNIQUE_V2, resolveSleepV2(listOf(base), emptyList()).getValue("v1").reason)
        assertEquals(ImportDecision.UNKNOWN_SOURCE, resolveSleepV2(listOf(base, v2), emptyList(), null).getValue("v2").decision)
        assertEquals(ImportDecision.UNKNOWN_SOURCE, resolveSleepV2(listOf(base, v2), emptyList(), setOf("v1")).getValue("v2").decision)
    }

    @Test fun `v2 is blocked when other version overlaps existing connect session`() {
        val stage = TakeoutStage("stage", at.plusSeconds(60), at.plusSeconds(3600), "LIGHT")
        val v2 = TakeoutSleep("v2", at.plusSeconds(60), at.plusSeconds(3600), ZoneOffset.UTC, ZoneOffset.UTC, "DERIVED", at, 59, listOf(stage), "sleep_session_v2")
        val v1 = v2.copy(id = "v1", start = at, algorithmVersion = "sleep_session_v1")
        val existing = ExistingSleep(at, at.plusSeconds(30))
        assertEquals(SleepConflictReason.ALTERNATIVE_IN_CONNECT, resolveSleepV2(listOf(v1, v2), listOf(existing)).getValue("v2").reason)
    }

    @Test fun `non-overlapping v2 sessions can replace one longer v1`() {
        fun session(id: String, start: Long, end: Long, version: String): TakeoutSleep {
            val a = at.plusSeconds(start); val b = at.plusSeconds(end)
            return TakeoutSleep(id, a, b, ZoneOffset.UTC, ZoneOffset.UTC, "DERIVED", at, (end - start) / 60,
                listOf(TakeoutStage(id, a, b, "LIGHT")), version)
        }
        val v1 = session("v1", 0, 10800, "sleep_session_v1")
        val early = session("early", 0, 1800, "sleep_session_v2")
        val late = session("late", 3600, 7200, "sleep_session_v2")
        val decisions = resolveSleepV2(listOf(v1, early, late), emptyList())
        assertEquals(ImportDecision.SUPERSEDED, decisions.getValue("v1").decision)
        assertEquals(ImportDecision.INSERT, decisions.getValue("early").decision)
        assertEquals(ImportDecision.INSERT, decisions.getValue("late").decision)
        val existingEarly = ExistingSleep(early.start, early.end, listOf(ExistingSleepStage(early.start, early.end, "LIGHT")))
        val resumed = resolveSleepV2(listOf(v1, early, late), listOf(existingEarly))
        assertEquals(ImportDecision.ALREADY_PRESENT, resumed.getValue("early").decision)
        assertEquals(ImportDecision.INSERT, resumed.getValue("late").decision)
        val foreign = ExistingSleep(at.plusSeconds(2400), at.plusSeconds(3000))
        assertEquals(ImportDecision.CONFLICT, resolveSleepV2(listOf(v1, early, late), listOf(foreign)).getValue("late").decision)
    }
}
