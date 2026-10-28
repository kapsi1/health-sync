package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.LocalDate

class SheetMergeTest {
    private val date = LocalDate.parse("2026-10-28")

    @Test fun `updates only active cells and preserves unrelated columns`() {
        val plan = planSheetMerge(listOf("date", "manual_note", "steps", "weight"), listOf("2026-10-28"), listOf(SheetRow(date, mapOf("steps" to 0.0))), setOf("steps"))
        assertEquals(listOf(SheetCellUpdate(2, 3, 0.0)), plan.updates)
        assertTrue(plan.newRows.isEmpty())
        assertTrue(plan.newColumns.isEmpty())
    }

    @Test fun `retry after uncertain append updates existing date`() {
        val row = SheetRow(date, mapOf("steps" to 10.0))
        assertEquals(1, planSheetMerge(listOf("date", "steps"), emptyList(), listOf(row), setOf("steps")).newRows.size)
        val retry = planSheetMerge(listOf("date", "steps"), listOf(date.toString()), listOf(row), setOf("steps"))
        assertTrue(retry.newRows.isEmpty())
        assertEquals(listOf(SheetCellUpdate(2, 2, 10.0)), retry.updates)
    }

    @Test fun `duplicate dates are rejected before writing`() {
        try { planSheetMerge(listOf("date"), listOf(date.toString(), date.toString()), emptyList(), emptySet()); fail("expected failure") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `columns append and existing positions remain stable`() {
        val plan = planSheetMerge(listOf("date", "steps"), emptyList(), listOf(SheetRow(date, mapOf("weight" to 70.0))), setOf("steps", "weight"))
        assertEquals(listOf("weight"), plan.newColumns)
        assertEquals(mapOf(3 to 70.0), plan.newRows.single().cells)
    }
}
