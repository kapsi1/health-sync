package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.healthsync.data.column

class SheetColumnTest {
    @Test fun `A1 columns handle transitions`() {
        assertEquals("A", column(1))
        assertEquals("Z", column(26))
        assertEquals("AA", column(27))
        assertEquals("ZZ", column(702))
        assertEquals("AAA", column(703))
    }
}
