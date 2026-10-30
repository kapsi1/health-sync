package pl.healthsync.data

import android.content.Context
import android.net.Uri
import pl.healthsync.domain.TakeoutArchive
import pl.healthsync.domain.readTakeoutArchive
import java.security.MessageDigest

internal data class VerifiedTakeoutFile(val archive: TakeoutArchive, val digest: String)

/** Verify the archive on both sides of parsing, and again when resuming. */
internal fun readVerifiedTakeout(context: Context, uri: Uri): VerifiedTakeoutFile {
    fun digest(): String {
        val hash = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: error("Nie można otworzyć archiwum")
        input.use { stream ->
        val buffer = ByteArray(32 * 1024)
        var size = 0L
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            size += count
            require(size <= 512L * 1024 * 1024) { "Archiwum przekracza limit rozmiaru" }
            hash.update(buffer, 0, count)
        }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    val before = digest()
    val archive = context.contentResolver.openInputStream(uri)?.use { readTakeoutArchive(it) }
        ?: error("Nie można ponownie otworzyć archiwum")
    check(before == digest()) { "Archiwum zmieniło się podczas analizy. Wybierz je ponownie." }
    return VerifiedTakeoutFile(archive, before)
}

/** Local namespace for deterministic IDs; normalized account label is not saved in plaintext. */
internal fun takeoutAccountKey(accountLabel: String): String {
    val normalized = accountLabel.trim().lowercase(java.util.Locale.ROOT)
    require(normalized.isNotBlank() && normalized.length <= 256) { "Podaj nazwę konta źródłowego" }
    return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
