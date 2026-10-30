package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.readTakeoutArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TakeoutArchiveTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out -> entries.forEach { (name, data) ->
            out.putNextEntry(ZipEntry(name)); out.write(data.toByteArray()); out.closeEntry()
        } }
        return bytes.toByteArray()
    }
    @Test fun `reads supported measurements and ignores unrelated files`() {
        val input = zip(
            "Takeout/Google Health/Physical Activity_GoogleData/Weight/weight.csv" to "timestamp,weight grams,data source\n2026-01-01T00:00:00Z,70000,Google Health App\n",
            "Takeout/Google Health/other.csv" to "not,a,health,record"
        )
        val result = readTakeoutArchive(ByteArrayInputStream(input))
        assertEquals(70.0, result.weights.single().value, 0.0)
        assertEquals(setOf("weight"), result.foundTypes)
    }
    @Test(expected = IllegalArgumentException::class) fun `does not accept missing sleep stages`() {
        readTakeoutArchive(ByteArrayInputStream(zip("Takeout/Google Health/Health Fitness Data_GoogleData/UserSleeps_2026-01-01.csv" to "")))
    }
    @Test fun `reads height from recognized path`() {
        val input = zip("Takeout/Google Health/Physical Activity_GoogleData/Height/height.csv" to "timestamp,height millimeters,data source\n2026-01-01T00:00:00Z,1750,Google Health App\n")
        assertEquals(1.75, readTakeoutArchive(ByteArrayInputStream(input)).heights.single().value, 0.0)
    }
    @Test fun `archive with fractional daily resting heart rate remains readable`() {
        val path = "Takeout/Google Health/Physical Activity_GoogleData/Daily Resting Heart Rate/daily_resting_heart_rate.csv"
        val result = readTakeoutArchive(ByteArrayInputStream(zip(path to "timestamp,beats per minute,data source\n2026-06-12T00:00:00Z,61.678,Google Health App\n")))
        assertEquals(61.678, result.restingHeartRates.single().value, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun `does not combine ambiguous account exports`() {
        val path = "Takeout/Google Health/Physical Activity_GoogleData/Weight/weight.csv"
        readTakeoutArchive(ByteArrayInputStream(zip(path to "", "another/$path" to "")))
    }
}
