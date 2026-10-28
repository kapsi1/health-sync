package pl.healthsync.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pl.healthsync.MetricRegistry

@RunWith(AndroidJUnit4::class)
class SheetsCreatorTest {
    @Test fun spreadsheetHasExpectedTimezoneAndHeaders() {
        val payload = createSheetPayload()
        assertEquals("Europe/Warsaw", payload.getJSONObject("properties").getString("timeZone"))
        val sheet = payload.getJSONArray("sheets").getJSONObject(0)
        assertEquals("daily", sheet.getJSONObject("properties").getString("title"))
        val values = sheet.getJSONArray("data").getJSONObject(0).getJSONArray("rowData").getJSONObject(0).getJSONArray("values")
        val headers = (0 until values.length()).map { values.getJSONObject(it).getJSONObject("userEnteredValue").getString("stringValue") }
        assertEquals(listOf("date") + MetricRegistry.all.map { it.column }, headers)
    }
}
