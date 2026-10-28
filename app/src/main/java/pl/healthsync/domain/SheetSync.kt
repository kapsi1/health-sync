package pl.healthsync.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class SheetSnapshot(val headers: List<String>, val dateCells: List<String?>)
data class PendingSheetDay(val date: LocalDate, val cells: Map<String, Double>, val revision: String)
data class PendingBatch(val rows: List<PendingSheetDay>)

interface SheetGateway {
    suspend fun snapshot(): SheetSnapshot
    suspend fun addColumns(headers: List<String>)
    suspend fun updateCells(updates: List<SheetCellUpdate>)
    suspend fun appendRows(rows: List<SheetNewRow>)
}

interface PendingQueue {
    suspend fun snapshot(limit: Int): PendingBatch
    /** Clear only if the stored revision still matches the uploaded snapshot. */
    suspend fun acknowledge(batch: PendingBatch)
}

/** A single instance is shared by manual and background synchronizers. */
class SheetSync(private val gateway: SheetGateway, private val queue: PendingQueue, private val lock: Mutex = Mutex()) {
    suspend fun uploadBatch(limit: Int = 100): Int = lock.withLock {
        require(limit in 1..500)
        val batch = queue.snapshot(limit)
        if (batch.rows.isEmpty()) return@withLock 0
        val snapshot = gateway.snapshot()
        val rows = batch.rows.map { SheetRow(it.date, it.cells) }
        val columns = rows.flatMap { it.cells.keys }.toSet()
        val plan = planSheetMerge(snapshot.headers, snapshot.dateCells, rows, columns)
        if (plan.newColumns.isNotEmpty()) gateway.addColumns(plan.newColumns)
        if (plan.updates.isNotEmpty()) gateway.updateCells(plan.updates)
        if (plan.newRows.isNotEmpty()) gateway.appendRows(plan.newRows)
        queue.acknowledge(batch)
        batch.rows.size
    }
}
