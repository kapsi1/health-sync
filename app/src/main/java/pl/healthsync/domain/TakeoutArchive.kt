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
    val thirdPartySleepIds: Set<String>? = null,
    val heights: List<TakeoutMeasurement> = emptyList(),
    val restingHeartRates: List<TakeoutMeasurement> = emptyList(),
    private val exercisesFile: ByteArray? = null,
    private val nightlyFiles: Map<String, ByteArray> = emptyMap(),
) {
    fun readExercises(): List<TakeoutExercise> = exercisesFile?.inputStream()?.reader(Charsets.UTF_8)?.use(::readTakeoutExercises) ?: emptyList()
    fun readNightly(kind: NightlyKind): List<TakeoutNightly> = nightlyFiles[kind.name]?.inputStream()?.reader(Charsets.UTF_8)?.use { readTakeoutNightly(it, kind) } ?: emptyList()
}

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
                path.contains("/Health Fitness Data_GoogleData/") && name.matches(Regex("UserExercises_[0-9-]+\\.csv")) -> "exercise"
                path.contains("/Health Fitness Data_GoogleData/") && name == "User3rdPartySleepAttributes.csv" -> "sleep_origins"
                path.contains("/Physical Activity_GoogleData/Weight/") && name == "weight.csv" -> "weight"
                path.contains("/Physical Activity_GoogleData/Height/") && name == "height.csv" -> "height"
                path.contains("/Physical Activity_GoogleData/Daily Resting Heart Rate/") && name == "daily_resting_heart_rate.csv" -> "resting_heart_rate"
                path.contains("/Physical Activity_GoogleData/Daily Heart Rate Variability/") && name == "daily_heart_rate_variability.csv" -> "HRV"
                path.contains("/Physical Activity_GoogleData/Daily Respiratory Rate/") && name == "daily_respiratory_rate.csv" -> "BREATHING"
                path.contains("/Physical Activity_GoogleData/Daily Sleep Temperature Derivations/") && name == "daily_sleep_temperature_derivations.csv" -> "SKIN_TEMPERATURE"
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
    require(files["sleep"].orEmpty().size <= 1 && files["stages"].orEmpty().size <= 1 && files["weight"].orEmpty().size <= 1 && files["height"].orEmpty().size <= 1 && files["sleep_origins"].orEmpty().size <= 1 && files["resting_heart_rate"].orEmpty().size <= 1 && files["exercise"].orEmpty().size <= 1) {
        "W archiwum znaleziono wiele plików tego samego typu; nie można ich jednoznacznie połączyć"
    }
    require(files.containsKey("sleep") == files.containsKey("stages")) { "Do analizy snu potrzebne są pliki sesji i faz" }
    fun ByteArray.reader() = inputStream().reader(Charsets.UTF_8)
    val weight = files["weight"].orEmpty().flatMap { it.reader().use { reader -> readTakeoutMeasurements(reader, TakeoutKind.WEIGHT) } }
    val fat = files["fat"].orEmpty().flatMap { it.reader().use { reader -> readTakeoutMeasurements(reader, TakeoutKind.BODY_FAT) } }
    val height = files["height"].orEmpty().flatMap { it.reader().use { reader -> readTakeoutMeasurements(reader, TakeoutKind.HEIGHT) } }
    val resting = files["resting_heart_rate"].orEmpty().flatMap { it.reader().use { reader -> readTakeoutMeasurements(reader, TakeoutKind.RESTING_HEART_RATE) } }
    val sleeps = if (files.containsKey("sleep")) files.getValue("sleep").single().reader().use { a ->
        files.getValue("stages").single().reader().use { b -> readTakeoutSleeps(a, b) }
    } else emptyList()
    val thirdPartyIds = files["sleep_origins"]?.single()?.reader()?.use { reader ->
        readTakeoutCsv(reader, setOf("sleep_id", "data_source")).map { row ->
            row.getValue("sleep_id").also { require(it.isNotBlank()) { "Brak identyfikatora zewnętrznej sesji snu" } }
        }.toSet()
    }
    val nightly = NightlyKind.entries.mapNotNull { kind -> files[kind.name]?.let { require(it.size == 1) { "Wiele plików podsumowania nocnego" }; kind.name to it.single() } }.toMap()
    return TakeoutArchive(weight, fat, sleeps, files.keys, thirdPartyIds, height, resting, files["exercise"]?.single(), nightly)
}
