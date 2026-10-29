package pl.healthsync.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import pl.healthsync.MetricRegistry

@RunWith(AndroidJUnit4::class)
class SheetsCreatorTest {
    @Test fun spreadsheetHasExpectedTimezoneAndHeaders() {
        val payload = createSheetPayload(setOf("steps", "weight_avg"))
        assertEquals("Europe/Warsaw", payload.getJSONObject("properties").getString("timeZone"))
        val sheet = payload.getJSONArray("sheets").getJSONObject(0)
        assertEquals("daily", sheet.getJSONObject("properties").getString("title"))
        val values = sheet.getJSONArray("data").getJSONObject(0).getJSONArray("rowData").getJSONObject(0).getJSONArray("values")
        val headers = (0 until values.length()).map { values.getJSONObject(it).getJSONObject("userEnteredValue").getString("stringValue") }
        assertEquals(listOf("date", "steps", "weight_avg_kg"), headers)
    }
    @Test fun sortKeepsHeaderAndIncludesUserColumns() {
        val sort = dateSortRequest(42).getJSONArray("requests").getJSONObject(0).getJSONObject("sortRange")
        val range = sort.getJSONObject("range")
        assertEquals(42, range.getInt("sheetId"))
        assertEquals(1, range.getInt("startRowIndex"))
        assertEquals(0, range.getInt("startColumnIndex"))
        assertFalse(range.has("endColumnIndex"))
        assertFalse(range.has("endRowIndex"))
        val spec = sort.getJSONArray("sortSpecs").getJSONObject(0)
        assertEquals(0, spec.getInt("dimensionIndex"))
        assertEquals("ASCENDING", spec.getString("sortOrder"))
    }
}
