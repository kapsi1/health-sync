package pl.healthsync

internal enum class RecoveryDestination { SHEET, DATA, SYNC }

internal fun recoveryDestination(message: String): RecoveryDestination {
    val lower = message.lowercase()
    return when {
        listOf("arkusz", "google sheets", "google", "http 401", "http 403", "http 404").any(lower::contains) -> RecoveryDestination.SHEET
        listOf("health connect", "zgod", "uprawnie", "wybór danych").any(lower::contains) -> RecoveryDestination.DATA
        else -> RecoveryDestination.SYNC
    }
}
