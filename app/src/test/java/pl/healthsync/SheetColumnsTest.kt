package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.healthsync.domain.ColumnVisibility
import pl.healthsync.domain.planColumnVisibility

class SheetColumnsTest {
    @Test fun onlyKnownColumnsAreChangedEvenWhenNoneSelected() {
        val headers = listOf("date", "steps", "user_note", "weight")
        assertEquals(listOf(ColumnVisibility(1, true), ColumnVisibility(3, true)), planColumnVisibility(headers, setOf("steps", "weight"), emptySet()))
        assertEquals(listOf(ColumnVisibility(1, false), ColumnVisibility(3, true)), planColumnVisibility(headers, setOf("steps", "weight"), setOf("steps")))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateHeadersAreRejected() {
        planColumnVisibility(listOf("date", "steps", "steps"), setOf("steps"), emptySet())
    }
}
