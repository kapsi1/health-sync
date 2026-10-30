package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.healthsync.data.extraColumnsNeeded

class SheetCapacityTest {
    @Test fun `expands only when new headers exceed grid`() {
        assertEquals(2, extraColumnsNeeded(16, 18))
        assertEquals(0, extraColumnsNeeded(16, 16))
        assertEquals(0, extraColumnsNeeded(26, 18))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `negative grid size is invalid`() { extraColumnsNeeded(-1, 3) }
}
