package pl.healthsync.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.healthsync.domain.SheetCellUpdate
import pl.healthsync.domain.SheetGateway
import pl.healthsync.domain.SheetNewRow
import pl.healthsync.domain.SheetSnapshot
import pl.healthsync.domain.planColumnVisibility
import pl.healthsync.domain.planSheetMerge
import pl.healthsync.domain.numericColumns
import pl.healthsync.domain.integerColumns
import pl.healthsync.domain.durationColumns
import pl.healthsync.domain.legacyDurationText
import pl.healthsync.domain.sheetValue
import pl.healthsync.MetricRegistry
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One gateway instance is used for one serialized batch. Never logs credentials or responses. */
class SheetGatewayHttp(private val spreadsheetId: String, private val token: String) : SheetGateway {
    private var headerCount = 0
    private var headers = emptyList<String>()
    private var visibleHeaders = emptyList<String>()
    private var hasUnlabeledData = false
    private var dailySheetId: Int? = null
    private val spreadsheet = "https://sheets.googleapis.com/v4/spreadsheets/${encode(spreadsheetId)}"
    private val base = "$spreadsheet/values"

    suspend fun spreadsheetName(): String = call("GET", "$spreadsheet?fields=${encode("properties.title")}")
        .getJSONObject("properties").getString("title")

    suspend fun reconcileColumns(known: Set<String>, selected: Set<String>) {
        val snapshot = snapshot()
        planSheetMerge(snapshot.headers, snapshot.dateCells, emptyList(), emptySet())
        require(selected.all { it in known })
        val missing = selected.filterNot { it in snapshot.headers }.sorted()
        if (missing.isNotEmpty()) check(!hasUnlabeledData) { "Dane znajdują się w kolumnach bez nagłówka" }
        renameManagedHeaders(snapshot.headers)
        if (missing.isNotEmpty()) {
            addColumns(missing)
        }
        val currentHeaders = snapshot.headers + missing
        val visibility = planColumnVisibility(currentHeaders, known, selected)
        if (visibility.isEmpty()) return
        val meta = call("GET", "$spreadsheet?fields=${encode("sheets.properties(sheetId,title)")}")
        val sheets = meta.getJSONArray("sheets")
        val daily = (0 until sheets.length()).map { sheets.getJSONObject(it).getJSONObject("properties") }
            .single { it.getString("title") == "daily" }
        val sheetId = daily.getInt("sheetId")
        dailySheetId = sheetId
        val requests = JSONArray()
        visibility.forEach { item ->
            val range = JSONObject().put("sheetId", sheetId).put("dimension", "COLUMNS")
                .put("startIndex", item.index).put("endIndex", item.index + 1)
            val properties = JSONObject().put("hiddenByUser", item.hidden)
            requests.put(JSONObject().put("updateDimensionProperties", JSONObject().put("range", range).put("properties", properties).put("fields", "hiddenByUser")))
        }
        currentHeaders.forEachIndexed { index, header ->
            if (header in integerColumns) requests.put(integerNumberFormatRequest(sheetId, index))
            else if (header in numericColumns) requests.put(resetNumberFormatRequest(sheetId, index))
        }
        call("POST", "$spreadsheet:batchUpdate", JSONObject().put("requests", requests))
        currentHeaders.forEachIndexed { index, header ->
            if (header in durationColumns) migrateDurationColumn(index + 1)
        }
    }

    private suspend fun renameManagedHeaders(canonical: List<String>) {
        val data = JSONArray()
        canonical.forEachIndexed { index, id ->
            val label = MetricRegistry.sheetHeaders.label(id)
            if (label != visibleHeaders[index]) {
                val range = "'daily'!${column(index + 1)}1"
                data.put(JSONObject().put("range", range).put("values", JSONArray().put(JSONArray().put(label))))
            }
        }
        if (data.length() > 0) {
            call("POST", "$base:batchUpdate", JSONObject().put("valueInputOption", "RAW").put("data", data))
            visibleHeaders = canonical.map(MetricRegistry.sheetHeaders::label)
        }
    }

    private suspend fun migrateDurationColumn(columnNumber: Int) {
        val name = column(columnNumber)
        val range = "'daily'!${name}2:${name}"
        val response = call("GET", "$base/${encode(range)}?valueRenderOption=FORMULA")
        val rows = response.optJSONArray("values") ?: return
        val changes = JSONArray()
        for (i in 0 until rows.length()) {
            val value = legacyDurationText(rows.optJSONArray(i)?.opt(0)) ?: continue
            val cellRange = "'daily'!$name${i + 2}"
            changes.put(JSONObject().put("range", cellRange).put("values", JSONArray().put(JSONArray().put(value))))
        }
        if (changes.length() > 0) call("POST", "$base:batchUpdate", JSONObject().put("valueInputOption", "RAW").put("data", changes))
    }

    override suspend fun snapshot(): SheetSnapshot {
        val json = call("GET", "${base}/${encode("'daily'")}")
        val rows = json.optJSONArray("values") ?: JSONArray()
        val displayed = if (rows.length() == 0) emptyList() else rows.getJSONArray(0).let { a -> (0 until a.length()).map { a.optString(it, "") } }
        val headerRows = call("GET", "$base/${encode("'daily'!1:1")}?valueRenderOption=FORMULA").optJSONArray("values")
        val formulas = if (headerRows == null || headerRows.length() == 0) emptyList() else headerRows.getJSONArray(0).let { a -> (0 until a.length()).map { a.optString(it, "") } }
        val header = MetricRegistry.sheetHeaders.resolve(displayed, formulas)
        visibleHeaders = displayed
        headers = header
        headerCount = header.size
        hasUnlabeledData = (1 until rows.length()).any { i ->
            val row = rows.getJSONArray(i)
            (headerCount until row.length()).any { col -> row.optString(col, "").isNotEmpty() }
        }
        val dates = (1 until rows.length()).map { i -> rows.getJSONArray(i).optString(0, "").ifBlank { null } }
        return SheetSnapshot(header, dates)
    }

    override suspend fun addColumns(headers: List<String>) {
        if (headers.isEmpty()) return
        check(!hasUnlabeledData) { "Dane znajdują się w kolumnach bez nagłówka" }
        val cells = JSONArray().put(JSONArray(headers.map(MetricRegistry.sheetHeaders::label)))
        val range = "'daily'!${column(headerCount + 1)}1"
        call("PUT", "$base/${encode(range)}?valueInputOption=RAW", JSONObject().put("range", range).put("majorDimension", "ROWS").put("values", cells))
        this.headers = this.headers + headers
        this.visibleHeaders = this.visibleHeaders + headers.map(MetricRegistry.sheetHeaders::label)
        headerCount += headers.size
    }

    override suspend fun updateCells(updates: List<SheetCellUpdate>) {
        if (updates.isEmpty()) return
        val data = JSONArray()
        updates.forEach { update ->
            val cell = "'daily'!${column(update.columnNumber)}${update.rowNumber}"
            val header = headers.getOrNull(update.columnNumber - 1).orEmpty()
            data.put(JSONObject().put("range", cell).put("values", JSONArray().put(JSONArray().put(sheetValue(header, update.value)))))
        }
        call("POST", "$base:batchUpdate", JSONObject().put("valueInputOption", "RAW").put("data", data))
    }

    override suspend fun appendRows(rows: List<SheetNewRow>) {
        if (rows.isEmpty()) return
        val data = JSONArray()
        for (row in rows) {
            val width = maxOf(headerCount, row.cells.keys.maxOrNull() ?: 1)
            val array = JSONArray()
            for (column in 1..width) {
                when (column) {
                    1 -> array.put(row.date.toString())
                    else -> array.put(row.cells[column]?.let { sheetValue(headers.getOrNull(column - 1).orEmpty(), it) } ?: "")
                }
            }
            data.put(array)
        }
        call("POST", "$base/${encode("'daily'!A1")}:append?valueInputOption=RAW&insertDataOption=INSERT_ROWS", JSONObject().put("majorDimension", "ROWS").put("values", data))
    }

    override suspend fun sortRowsByDate() {
        val sheetId = dailySheetId ?: run {
            val meta = call("GET", "$spreadsheet?fields=${encode("sheets.properties(sheetId,title)")}")
            val sheets = meta.getJSONArray("sheets")
            (0 until sheets.length()).map { sheets.getJSONObject(it).getJSONObject("properties") }
                .single { it.getString("title") == "daily" }.getInt("sheetId").also { dailySheetId = it }
        }
        call("POST", "$spreadsheet:batchUpdate", dateSortRequest(sheetId))
    }

    private suspend fun call(method: String, endpoint: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw SheetHttpError(code)
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally { conn.disconnect() }
    }
}

/** Clear the custom number format, letting Sheets display stored rounded numbers automatically. */
internal fun resetNumberFormatRequest(sheetId: Int, columnIndex: Int): JSONObject {
    val range = JSONObject().put("sheetId", sheetId).put("startRowIndex", 1)
        .put("startColumnIndex", columnIndex).put("endColumnIndex", columnIndex + 1)
    return JSONObject().put("repeatCell", JSONObject().put("range", range)
        .put("cell", JSONObject()).put("fields", "userEnteredFormat.numberFormat"))
}

/** Old calorie cells display whole numbers; new exports are rounded before writing. */
internal fun integerNumberFormatRequest(sheetId: Int, columnIndex: Int): JSONObject {
    val range = JSONObject().put("sheetId", sheetId).put("startRowIndex", 1)
        .put("startColumnIndex", columnIndex).put("endColumnIndex", columnIndex + 1)
    val format = JSONObject().put("type", "NUMBER").put("pattern", "0")
    val cell = JSONObject().put("userEnteredFormat", JSONObject().put("numberFormat", format))
    return JSONObject().put("repeatCell", JSONObject().put("range", range)
        .put("cell", cell).put("fields", "userEnteredFormat.numberFormat"))
}

/** Sort all columns together, excluding the header; open ends include user-added columns. */
internal fun dateSortRequest(sheetId: Int): JSONObject {
    val range = JSONObject().put("sheetId", sheetId).put("startRowIndex", 1).put("startColumnIndex", 0)
    val sortSpec = JSONObject().put("dimensionIndex", 0).put("sortOrder", "ASCENDING")
    val sort = JSONObject().put("range", range).put("sortSpecs", JSONArray().put(sortSpec))
    return JSONObject().put("requests", JSONArray().put(JSONObject().put("sortRange", sort)))
}

private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
internal fun column(number: Int): String {
    require(number > 0)
    var index = number
    val result = StringBuilder()
    while (index > 0) { index--; result.append(('A'.code + index % 26).toChar()); index /= 26 }
    return result.reverse().toString()
}
