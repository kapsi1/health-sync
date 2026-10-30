package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TakeoutActivityArchiveTest {
    private val date = LocalDate.parse("2026-06-12")
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun archive(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> files.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        return out.toByteArray()
    }

    @Test fun `estimates from multiple files combine with exclusions and ambiguous days`() {
        val header = "timestamp,steps,data source\n"
        val a = "Takeout/Google Health/Physical Activity_GoogleData/Steps/steps_2026-05-01.csv"
        val b = "Takeout/Google Health/Physical Activity_GoogleData/Steps/steps_2026-06-01.csv"
        val zip = archive(a to (header + "2026-06-12T07:00:00Z,10,Charge 6\n2026-06-12T07:00:30Z,7,MobileTrack\n"),
            b to (header + "2026-06-12T07:01:00Z,2,Phone Health Connect\n2026-06-13T07:00:00Z,1,Charge 6\n2026-06-13T07:00:30Z,2,Charge 6\n"))
        val results = readTakeoutActivityArchive(ByteArrayInputStream(zip), setOf(TakeoutActivityKind.STEPS), date, date.plusDays(1), zone).getValue(TakeoutActivityKind.STEPS)
        assertEquals(10.0, results.getValue(date).value!!, 0.0)
        assertEquals(1, results.getValue(date).excludedConnectSamples)
        assertNull(results.getValue(date.plusDays(1)).value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `range is bounded`() {
        readTakeoutActivityArchive(ByteArrayInputStream(archive()), setOf(TakeoutActivityKind.STEPS), date, date.plusDays(366), zone)
    }

    @Test fun `calorie archive sums different seconds in same minute`() {
        val file = "Takeout/Google Health/Physical Activity_GoogleData/Calories/calories_2026-06-01.csv"
        val data = "timestamp,calories,data source\n2026-06-12T07:00:00Z,0.04,Google Health App\n2026-06-12T07:00:02Z,3.19,Google Health App\n"
        val result = readTakeoutActivityArchive(ByteArrayInputStream(archive(file to data)), setOf(TakeoutActivityKind.TOTAL_CALORIES), date, date, zone)
        assertEquals(3.23, result.getValue(TakeoutActivityKind.TOTAL_CALORIES).getValue(date).value!!, 0.000001)
    }
}
