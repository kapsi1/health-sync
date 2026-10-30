package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.readTakeoutCsv
import java.io.StringReader

class TakeoutCsvTest {
    @Test fun `reads BOM quoted comma quote and newline`() {
        val content = "\ufefftimestamp,data source,value\r\n2026-01-01T00:00:00Z,\"Fit, \"\"device\"\"\napp\",42\r\n"
        val rows = readTakeoutCsv(StringReader(content), setOf("timestamp", "data source", "value"))
        assertEquals("Fit, \"device\"\napp", rows.single().getValue("data source"))
        assertEquals("42", rows.single().getValue("value"))
    }

    @Test(expected = IllegalArgumentException::class) fun `rejects missing header`() {
        readTakeoutCsv(StringReader("timestamp,value\n"), setOf("data source"))
    }

    @Test(expected = IllegalArgumentException::class) fun `rejects malformed quotes`() {
        readTakeoutCsv(StringReader("timestamp,value\nabc,\"unfinished"), setOf("timestamp"))
    }

    @Test(expected = IllegalArgumentException::class) fun `rejects rows wider than header`() {
        readTakeoutCsv(StringReader("timestamp,value\nabc,1,2\n"), setOf("timestamp"))
    }

    @Test(expected = IllegalArgumentException::class) fun `bounds number of rows`() {
        readTakeoutCsv(StringReader("value\n1\n2\n"), setOf("value"), maxRows = 1)
    }
}
