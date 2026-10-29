package pl.healthsync.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.healthsync.MetricRegistry
import java.net.HttpURLConnection
import java.net.URL

data class CreatedSheet(val id: String, val url: String)
class SheetHttpError(val code: Int) : Exception("HTTP $code")

class SheetsCreator {
    suspend fun create(token: String, selected: Set<String>): CreatedSheet = withContext(Dispatchers.IO) {
        val body = createSheetPayload(selected)
        val conn = (URL("https://sheets.googleapis.com/v4/spreadsheets?fields=spreadsheetId,spreadsheetUrl").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw SheetHttpError(code)
            val response = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val id = response.getString("spreadsheetId")
            val url = response.getString("spreadsheetUrl")
            require(id.isNotBlank() && url.startsWith("https://docs.google.com/spreadsheets/"))
            CreatedSheet(id, url)
        } finally { conn.disconnect() }
    }
}

internal fun createSheetPayload(selected: Set<String>): JSONObject {
    val headers = (listOf("date") + MetricRegistry.all.filter { it.id in selected }.map { it.column })
        .map(MetricRegistry.sheetHeaders::label)
    val values = JSONArray()
    headers.forEach { values.put(JSONObject().put("userEnteredValue", JSONObject().put("stringValue", it))) }
    val sheet = JSONObject()
        .put("properties", JSONObject().put("title", "daily"))
        .put("data", JSONArray().put(JSONObject().put("startRow", 0).put("startColumn", 0)
            .put("rowData", JSONArray().put(JSONObject().put("values", values)))))
    return JSONObject()
        .put("properties", JSONObject().put("title", "Health Connect – Daily Sync").put("timeZone", "Europe/Warsaw"))
        .put("sheets", JSONArray().put(sheet))
}
