package pl.healthsync.domain

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads only recognised files, without extracting paths or retaining unrelated data. */
internal data class TakeoutArchive(
    val weights: List<TakeoutMeasurement>,
    val bodyFat: List<TakeoutMeasurement>,
    val sleeps: List<TakeoutSleep>,
    val foundTypes: Set<String>,
)

internal fun readTakeoutArchive(input: InputStream): TakeoutArchive {
    val files = mutableMapOf<String, MutableList<ByteArray>>()
    var totalBytes = 0L
    var retainedBytes = 0L
    var entries = 0
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(++entries <= 30_000) { "Archiwum zawiera zbyt wiele plików" }
            val path = entry.name.replace('\\', '/')
            val name = path.substringAfterLast('/')
            val type = when {
                path.contains("/Health Fitness Data_GoogleData/") && name.matches(Regex("UserSleeps_[0-9-]+\\.csv")) -> "sleep"
                path.contains("/Health Fitness Data_GoogleData/") && name.matches(Regex("UserSleepStages_[0-9-]+\\.csv")) -> "stages"
                path.contains("/Physical Activity_GoogleData/Weight/") && name == "weight.csv" -> "weight"
                path.contains("/Physical Activity_GoogleData/Body Fat/") && name.matches(Regex("body_fat_[0-9-]+\\.csv")) -> "fat"
                else -> null
            }
            val output = if (type != null && !entry.isDirectory) ByteArrayOutputStream() else null
            val buffer = ByteArray(8192)
            while (true) {
                val count = zip.read(buffer)
                if (count == -1) break
                totalBytes += count
                require(totalBytes <= 512L * 1024 * 1024) { "Archiwum po rozpakowaniu przekracza limit" }
                if (output != null) {
                    require(output.size().toLong() + count <= 64L * 1024 * 1024) { "Plik danych przekracza limit" }
                    retainedBytes += count
                    require(retainedBytes <= 64L * 1024 * 1024) { "Pliki danych przekraczają limit" }
                    output.write(buffer, 0, count)
                }
            }
            if (output != null && type != null) files.getOrPut(type) { mutableListOf() }.add(output.toByteArray())
            zip.closeEntry()
        }
    }
    require(files["sleep"].orEmpty().size <= 1 && files["stages"].orEmpty().size <= 1 && files["weight"].orEmpty().size <= 1) {
        "W archiwum znaleziono wiele plików tego samego typu; nie można ich jednoznacznie połączyć"
    }
    require(files.containsKey("sleep") == files.containsKey("stages")) { "Do analizy snu potrzebne są pliki sesji i faz" }
    fun ByteArray.reader() = inputStream().reader(Charsets.UTF_8)
    val weight = files["weight"].orEmpty().flatMap { it.reader().use { reader -> readTakeoutMeasurements(reader, TakeoutKind.WEIGHT) } }
    val fat = files["fat"].orEmpty().flatMap { it.reader().use { reader -> readTakeoutMeasurements(reader, TakeoutKind.BODY_FAT) } }
    val sleeps = if (files.containsKey("sleep")) files.getValue("sleep").single().reader().use { a ->
        files.getValue("stages").single().reader().use { b -> readTakeoutSleeps(a, b) }
    } else emptyList()
    return TakeoutArchive(weight, fat, sleeps, files.keys)
}
