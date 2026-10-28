package pl.healthsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.LocalDate

class SheetSyncTest {
    private val date = LocalDate.parse("2026-10-20")
    private class FakeQueue(var batch: PendingBatch) : PendingQueue {
        var acknowledgments = 0
        override suspend fun snapshot(limit: Int) = batch
        override suspend fun acknowledge(batch: PendingBatch) { acknowledgments++; if (batch == this.batch) this.batch = PendingBatch(emptyList()) }
    }
    private class FakeGateway : SheetGateway {
        var headers = listOf("date", "steps")
        val dates = mutableListOf<String?>()
        var updates = 0
        var appendCalls = 0
        var timeoutAfterAppend = false
        override suspend fun snapshot() = SheetSnapshot(headers, dates.toList())
        override suspend fun addColumns(headers: List<String>) { this.headers += headers }
        override suspend fun updateCells(updates: List<SheetCellUpdate>) { this.updates += updates.size }
        override suspend fun appendRows(rows: List<SheetNewRow>) {
            appendCalls++
            dates.addAll(rows.map { it.date.toString() })
            if (timeoutAfterAppend) { timeoutAfterAppend = false; error("connection lost") }
        }
    }
    @Test fun `timeout after accepted append rereads and updates existing row`() = runBlocking {
        val queue = FakeQueue(PendingBatch(listOf(PendingSheetDay(date, mapOf("steps" to 5.0), "one"))))
        val gateway = FakeGateway().apply { timeoutAfterAppend = true }
        val sync = SheetSync(gateway, queue)
        try { sync.uploadBatch(); fail("expected timeout") } catch (_: IllegalStateException) { }
        assertEquals(0, queue.acknowledgments)
        sync.uploadBatch()
        assertEquals(1, gateway.appendCalls)
        assertEquals(1, gateway.updates)
        assertEquals(1, queue.acknowledgments)
    }
    @Test fun `duplicate dates abort without acknowledgment`() = runBlocking {
        val queue = FakeQueue(PendingBatch(listOf(PendingSheetDay(date, mapOf("steps" to 5.0), "one"))))
        val gateway = FakeGateway().apply { dates.addAll(listOf(date.toString(), date.toString())) }
        try { SheetSync(gateway, queue).uploadBatch(); fail("expected duplicate error") } catch (_: IllegalArgumentException) { }
        assertEquals(0, gateway.appendCalls)
        assertEquals(0, queue.acknowledgments)
    }
    @Test fun `changed observation remains queued after older upload`() = runBlocking {
        val first = PendingSheetDay(date, mapOf("steps" to 5.0), "one")
        val second = PendingSheetDay(date, mapOf("steps" to 6.0), "two")
        val queue = FakeQueue(PendingBatch(listOf(first)))
        val gateway = object : SheetGateway {
            override suspend fun snapshot() = SheetSnapshot(listOf("date", "steps"), emptyList())
            override suspend fun addColumns(headers: List<String>) = Unit
            override suspend fun updateCells(updates: List<SheetCellUpdate>) = Unit
            override suspend fun appendRows(rows: List<SheetNewRow>) { queue.batch = PendingBatch(listOf(second)) }
        }
        SheetSync(gateway, queue).uploadBatch()
        assertEquals(PendingBatch(listOf(second)), queue.batch)
    }
}
