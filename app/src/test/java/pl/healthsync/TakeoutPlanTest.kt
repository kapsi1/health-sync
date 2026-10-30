package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.data.*
import pl.healthsync.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TakeoutPlanTest {
    private val at = Instant.parse("2026-06-11T22:22:00Z")
    private val stage = TakeoutStage("stage", at, at.plusSeconds(3600), "REM")
    private val v2 = TakeoutSleep("v2", at, at.plusSeconds(3600), ZoneOffset.ofHours(2), ZoneOffset.ofHours(2), "DERIVED", at, 60, listOf(stage), "sleep_session_v2")
    private val v1 = v2.copy(id = "v1", algorithmVersion = "sleep_session_v1")
    private val start = LocalDate.of(2026, 6, 12)

    @Test fun `resume recreates frozen version and rejects changed content`() {
        val archive = TakeoutArchive(emptyList(), emptyList(), listOf(v1, v2), setOf("sleep", "stages", "sleep_origins"), emptySet())
        val all = reconstructTakeoutPlan(archive, start, start, setOf(TakeoutType.SLEEP), "account")
        val candidate = takeoutPayloadCandidate(all.single().record)
        val saved = listOf(TakeoutImportRecord("run", candidate.clientId, candidate.kind, "pending", candidate.contentHash))
        assertEquals(all, matchRecordedTakeoutPlan(all, saved))
        val changed = reconstructTakeoutPlan(archive.copy(sleeps = listOf(v1, v2.copy(stages = listOf(stage.copy(type = "DEEP"))))), start, start, setOf(TakeoutType.SLEEP), "account")
        try { matchRecordedTakeoutPlan(changed, saved); fail("Expected failure") } catch (_: IllegalStateException) { }
    }

    @Test fun `missing source metadata blocks plan reconstruction`() {
        val archive = TakeoutArchive(emptyList(), emptyList(), listOf(v2), setOf("sleep", "stages"), null)
        assertTrue(reconstructTakeoutPlan(archive, start, start, setOf(TakeoutType.SLEEP), "account").isEmpty())
    }
}
