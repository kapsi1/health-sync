package pl.healthsync.domain

import java.net.URI

fun spreadsheetId(input: String): String? {
    val text = input.trim()
    val id = if (text.startsWith("https://")) {
        val uri = try { URI(text) } catch (_: Exception) { return null }
        if (uri.host != "docs.google.com" || uri.userInfo != null || uri.port != -1) return null
        Regex("^/spreadsheets/d/([A-Za-z0-9_-]+)(?:/.*)?$").matchEntire(uri.path ?: "")?.groupValues?.get(1) ?: return null
    } else text
    return id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,}")) }
}
