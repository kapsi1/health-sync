package pl.healthsync.domain

import java.io.Reader
import java.time.Instant
import java.time.format.DateTimeParseException

/** Reads one CSV incrementally. The consumer can aggregate without retaining every source row. */
internal fun readTakeoutActivityCsv(
    reader: Reader,
    kind: TakeoutActivityKind,
    consume: (TakeoutActivitySample) -> Unit,
) {
    val valueColumn = when (kind) {
        TakeoutActivityKind.STEPS -> "steps"
        TakeoutActivityKind.DISTANCE -> "distance"
        TakeoutActivityKind.TOTAL_CALORIES -> "calories"
        TakeoutActivityKind.ACTIVE_CALORIES -> "Kilocalories"
    }
    val csv = TakeoutCsv(reader)
    val header = csv.nextRow() ?: throw IllegalArgumentException("Brak nagłówka CSV")
    require(header.none { it.isBlank() } && header.distinct().size == header.size) { "Nieobsługiwany nagłówek CSV" }
    val timestamp = header.indexOf("timestamp")
    val source = header.indexOf("data source")
    val value = header.indexOf(valueColumn)
    require(timestamp >= 0 && source >= 0 && value >= 0) { "Nieobsługiwany nagłówek CSV" }
    var rows = 0
    while (true) {
        val fields = csv.nextRow() ?: break
        if (fields.size == 1 && fields[0].isBlank()) continue
        require(fields.size == header.size) { "Nieprawidłowa liczba pól CSV" }
        require(++rows <= 2_000_000) { "Zbyt wiele rekordów CSV" }
        val time = try { Instant.parse(fields[timestamp]) } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Nieprawidłowy czas UTC w eksporcie")
        }
        val number = fields[value].toDoubleOrNull() ?: throw IllegalArgumentException("Nieprawidłowa wartość aktywności")
        require(number.isFinite() && number >= 0 && (kind != TakeoutActivityKind.STEPS || number % 1.0 == 0.0)) { "Nieprawidłowa wartość aktywności" }
        val origin = when (fields[source].trim()) {
            "Charge 6" -> ActivitySourceCategory.WEARABLE
            "MobileTrack" -> ActivitySourceCategory.PHONE
            "Google Health App" -> ActivitySourceCategory.GOOGLE_HEALTH
            "Google Fit" -> ActivitySourceCategory.GOOGLE_FIT
            "Fit Health Connect", "Phone Health Connect" -> ActivitySourceCategory.CONNECT
            else -> ActivitySourceCategory.UNKNOWN
        }
        consume(TakeoutActivitySample(time, number, origin))
    }
}
