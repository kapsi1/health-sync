package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.io.StringReader

class TakeoutActivityCsvTest {
    @Test fun `recognized sources and unknown sources remain distinguishable`() {
        val data = "timestamp,steps,data source\n2026-06-12T00:00:00Z,12,Charge 6\n2026-06-12T00:01:00Z,3,Phone Health Connect\n2026-06-12T00:02:00Z,5,Unknown\n"
        val rows = mutableListOf<TakeoutActivitySample>()
        readTakeoutActivityCsv(StringReader(data), TakeoutActivityKind.STEPS, rows::add)
        assertEquals(listOf(ActivitySourceCategory.WEARABLE, ActivitySourceCategory.CONNECT, ActivitySourceCategory.UNKNOWN), rows.map { it.source })
    }

    @Test fun `active calories recognizes export header`() {
        val rows = mutableListOf<TakeoutActivitySample>()
        readTakeoutActivityCsv(StringReader("timestamp,Kilocalories,data source\n2026-06-12T00:00:00Z,1.25,Google Health App\n"), TakeoutActivityKind.ACTIVE_CALORIES, rows::add)
        assertEquals(1.25, rows.single().value, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fractional step counts are rejected`() {
        readTakeoutActivityCsv(StringReader("timestamp,steps,data source\n2026-06-12T00:00:00Z,1.5,Charge 6\n"), TakeoutActivityKind.STEPS) { }
    }
}
