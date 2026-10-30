package pl.healthsync.domain

import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipInputStream

/** Read-only analysis; the returned estimates must be reviewed before any import is planned. */
internal fun readTakeoutActivityArchive(
    input: InputStream,
    kinds: Set<TakeoutActivityKind>,
    start: LocalDate,
    end: LocalDate,
    zone: ZoneId,
): Map<TakeoutActivityKind, Map<LocalDate, TakeoutActivityEstimate>> {
    require(start <= end && ChronoUnit.DAYS.between(start, end) < 366 && kinds.isNotEmpty()) { "Wybierz zakres do 366 dni i co najmniej jeden rodzaj aktywności" }
    val days = mutableMapOf<TakeoutActivityKind, MutableMap<LocalDate, TakeoutActivityDayAccumulator>>()
    val seenPaths = mutableSetOf<String>()
    var count = 0
    var expanded = 0L
    var selectedRows = 0
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(++count <= 30_000) { "Archiwum zawiera zbyt wiele plików" }
            val path = entry.name.replace('\\', '/')
            val kind = when {
                path.contains("/Physical Activity_GoogleData/Steps/") && path.substringAfterLast('/').matches(Regex("steps_[0-9-]+\\.csv")) -> TakeoutActivityKind.STEPS
                path.contains("/Physical Activity_GoogleData/Distance/") && path.substringAfterLast('/').matches(Regex("distance_[0-9-]+\\.csv")) -> TakeoutActivityKind.DISTANCE
                path.contains("/Physical Activity_GoogleData/Calories/") && path.substringAfterLast('/').matches(Regex("calories_[0-9-]+\\.csv")) -> TakeoutActivityKind.TOTAL_CALORIES
                path.contains("/Physical Activity_GoogleData/Active Energy Burned/") && path.substringAfterLast('/').matches(Regex("active_energy_burned_[0-9-]+\\.csv")) -> TakeoutActivityKind.ACTIVE_CALORIES
                else -> null
            }
            if (kind != null) require(seenPaths.add(path)) { "Powtórzony plik aktywności w archiwum" }
            val counted = object : InputStream() {
                override fun read(): Int {
                    val value = zip.read()
                    if (value >= 0) { expanded++; require(expanded <= 512L * 1024 * 1024) { "Archiwum po rozpakowaniu przekracza limit" } }
                    return value
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val value = zip.read(buffer, offset, length)
                    if (value > 0) { expanded += value; require(expanded <= 512L * 1024 * 1024) { "Archiwum po rozpakowaniu przekracza limit" } }
                    return value
                }
            }
            if (!entry.isDirectory && kind != null && kind in kinds) {
                readTakeoutActivityCsv(counted.reader(Charsets.UTF_8), kind) { sample ->
                    val day = sample.time.atZone(zone).toLocalDate()
                    if (day >= start && day <= end) {
                        require(++selectedRows <= 1_500_000) { "Zbyt wiele pomiarów do analizy; wybierz krótszy zakres" }
                        days.getOrPut(kind) { mutableMapOf() }.getOrPut(day) {
                            TakeoutActivityDayAccumulator(sumDistinctSameMinute = kind == TakeoutActivityKind.TOTAL_CALORIES || kind == TakeoutActivityKind.ACTIVE_CALORIES)
                        }.add(sample)
                    }
                }
            }
            val buffer = ByteArray(8192)
            while (counted.read(buffer) != -1) { /* Drain unselected data while enforcing the archive limit. */ }
            zip.closeEntry()
        }
    }
    return days.mapValues { (_, perDay) -> perDay.mapValues { (_, accumulator) -> accumulator.result() } }
}
