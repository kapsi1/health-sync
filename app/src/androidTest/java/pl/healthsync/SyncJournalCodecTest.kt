package pl.healthsync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncJournalCodecTest {
    @Test fun outcomeSurvivesSerializationAndPreviousErrorRemainsSearchable() {
        val first = appendEvent(null, "r", "Automatyczna", SyncEvent("1", "Start"), false, false)
        val failed = appendEvent(first, "r", "Automatyczna", SyncEvent("2", "Błąd"), true, true)
        val retry = appendEvent(failed, "r", "Automatyczna", SyncEvent("3", "Ponowienie"), false, false)
        val success = appendEvent(retry, "r", "Automatyczna", SyncEvent("4", "Gotowe"), false, true)
        assertEquals(listOf(success), decodeRuns(encodeRuns(listOf(success))))
        assertTrue(success.error)
        assertEquals(false, success.lastError)
    }

    @Test fun oldLogsWithEarlierErrorKeepUnknownOutcome() {
        val legacy = """[{"id":"r","kind":"Automatyczna","error":true,"complete":true,"events":[{"at":"1","message":"Gotowe"}]}]"""
        assertNull(decodeRuns(legacy).single().lastError)
        val clean = legacy.replace("\"error\":true", "\"error\":false")
        assertEquals(false, decodeRuns(clean).single().lastError)
        assertNull(decodeRuns(legacy.replace("\"complete\":true", "\"complete\":false")).single().lastError)
    }
}
