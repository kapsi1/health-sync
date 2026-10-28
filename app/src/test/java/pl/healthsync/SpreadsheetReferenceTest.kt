package pl.healthsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.healthsync.domain.spreadsheetId
import pl.healthsync.domain.pickedSpreadsheetId

class SpreadsheetReferenceTest {
    @Test fun acceptsIdAndGoogleSheetsLink() {
        val id = "abcDEF_123-456"
        assertEquals(id, spreadsheetId("  $id  "))
        assertEquals(id, spreadsheetId("https://docs.google.com/spreadsheets/d/$id/edit?gid=42#gid=42"))
    }
    @Test fun rejectsOtherHostsAndInvalidReferences() {
        assertNull(spreadsheetId("https://docs.google.com.evil.example/spreadsheets/d/abcDEF_123-456/edit"))
        assertNull(spreadsheetId("https://docs.google.com@evil.example/spreadsheets/d/abcDEF_123-456/edit"))
        assertNull(spreadsheetId("abc/123456789"))
        assertNull(spreadsheetId("https://docs.google.com/document/d/abcDEF_123-456/edit"))
    }
    @Test fun pickerAcceptsExactlyOneValidId() {
        assertEquals("abcDEF123456", pickedSpreadsheetId(" abcDEF123456 "))
        assertNull(pickedSpreadsheetId(null))
        assertNull(pickedSpreadsheetId(""))
        assertNull(pickedSpreadsheetId("abcDEF123456,xyzABC123456"))
        assertNull(pickedSpreadsheetId("invalid id"))
    }
}
