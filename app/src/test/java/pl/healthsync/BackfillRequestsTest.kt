package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.healthsync.domain.updateBackfillRequests

class BackfillRequestsTest {
    @Test fun disablingAndReenablingCreatesNewRequest() {
        val pending = setOf("weight|old", "steps|untouched")
        val disabled = updateBackfillRequests(setOf("weight", "steps"), setOf("steps"), pending) { "unused" }
        assertEquals(setOf("steps|untouched"), disabled)
        val enabled = updateBackfillRequests(setOf("steps"), setOf("steps", "weight"), disabled) { "new" }
        assertEquals(setOf("steps|untouched", "weight|new"), enabled)
        assertEquals(setOf("steps|untouched", "weight|new"), enabled - "weight|old")
    }
}
