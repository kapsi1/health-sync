package pl.healthsync.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.healthsync.domain.SheetCellUpdate
import pl.healthsync.domain.SheetGateway
import pl.healthsync.domain.SheetNewRow
import pl.healthsync.domain.SheetSnapshot
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One gateway instance is used for one serialized batch. Never logs credentials or responses. */
class SheetGatewayHttp(private val spreadsheetId: String, private val token: String) : SheetGateway {
    private var headerCount = 0
    private val base = "https://sheets.googleapis.com/v4/spreadsheets/${encode(spreadsheetId)}/values"

    override suspend fun snapshot(): SheetSnapshot {
        val json = call("GET", "${base}/${encode("'daily'")}")
        val rows = json.optJSONArray("values") ?: JSONArray()
        val header = if (rows.length() == 0) emptyList() else rows.getJSONArray(0).let { a -> (0 until a.length()).map { a.optString(it, "") } }
        headerCount = header.size
        val dates = (1 until rows.length()).map { i -> rows.getJSONArray(i).optString(0, "").ifBlank { null } }
        return SheetSnapshot(header, dates)
    }

    override suspend fun addColumns(headers: List<String>) {
        if (headers.isEmpty()) return
        val cells = JSONArray().put(JSONArray(headers))
        val range = "'daily'!${column(headerCount + 1)}1"
        call("PUT", "$base/${encode(range)}?valueInputOption=RAW", JSONObject().put("range", range).put("majorDimension", "ROWS").put("values", cells))
        headerCount += headers.size
    }

    override suspend fun updateCells(updates: List<SheetCellUpdate>) {
        if (updates.isEmpty()) return
        val data = JSONArray()
        updates.forEach { update ->
            val cell = "'daily'!${column(update.columnNumber)}${update.rowNumber}"
            data.put(JSONObject().put("range", cell).put("values", JSONArray().put(JSONArray().put(update.value))))
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
                    else -> array.put(row.cells[column] ?: "")
                }
            }
            data.put(array)
        }
        call("POST", "$base/${encode("'daily'!A1")}:append?valueInputOption=RAW&insertDataOption=INSERT_ROWS", JSONObject().put("majorDimension", "ROWS").put("values", data))
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

private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
internal fun column(number: Int): String {
    require(number > 0)
    var index = number
    val result = StringBuilder()
    while (index > 0) { index--; result.append(('A'.code + index % 26).toChar()); index /= 26 }
    return result.reverse().toString()
}
