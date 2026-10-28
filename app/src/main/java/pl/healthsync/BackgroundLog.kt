package pl.healthsync

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun appendSyncLog(existing: String, message: String, now: Instant): String {
    val timestamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.of("Europe/Warsaw")).format(now)
    val entry = "$timestamp — ${message.replace('\n', ' ')}"
    return (listOf(entry) + existing.lineSequence().filter { it.isNotBlank() }.toList()).take(50).joinToString("\n")
}
