package pl.healthsync.domain

import java.time.LocalDate

data class SheetRow(val date: LocalDate, val cells: Map<String, Double>)
data class SheetCellUpdate(val rowNumber: Int, val columnNumber: Int, val value: Double)
data class SheetNewRow(val date: LocalDate, val cells: Map<Int, Double>)
data class SheetPlan(val newColumns: List<String>, val updates: List<SheetCellUpdate>, val newRows: List<SheetNewRow>)

/** Plan changes against a freshly read snapshot; caller must reread after ambiguous write results. */
fun planSheetMerge(headers: List<String>, existingDates: List<String?>, rows: List<SheetRow>, metricColumns: Set<String>): SheetPlan {
    require(headers.firstOrNull() == "date") { "Pierwsza kolumna arkusza musi mieć nazwę date" }
    require(headers.filter { it.isNotEmpty() }.distinct().size == headers.count { it.isNotEmpty() }) { "Powtórzone nagłówki" }
    val parsedDates = existingDates.mapIndexedNotNull { index, value ->
        if (value.isNullOrBlank()) null else LocalDate.parse(value) to index + 2
    }
    require(parsedDates.map { it.first }.distinct().size == parsedDates.size) { "Powtórzone daty w arkuszu" }
    require(rows.map { it.date }.distinct().size == rows.size) { "Powtórzone daty w danych" }
    require("date" !in metricColumns && "" !in metricColumns)
    require(rows.all { row -> row.cells.keys.all { it in metricColumns } && row.cells.values.all { it.isFinite() } })
    val missing = metricColumns.filterNot { it in headers }.sorted()
    val positions = (headers + missing).mapIndexed { index, header -> header to index + 1 }.toMap()
    val lookup = parsedDates.toMap()
    val updates = mutableListOf<SheetCellUpdate>()
    val newRows = mutableListOf<SheetNewRow>()
    for (row in rows.sortedBy { it.date }) {
        val cells = row.cells.mapKeys { (header, _) -> positions.getValue(header) }
        val number = lookup[row.date]
        if (number == null) newRows += SheetNewRow(row.date, cells)
        else cells.forEach { (column, value) -> updates += SheetCellUpdate(number, column, value) }
    }
    return SheetPlan(missing, updates, newRows)
}
