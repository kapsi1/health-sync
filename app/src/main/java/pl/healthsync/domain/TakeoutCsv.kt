package pl.healthsync.domain

import java.io.PushbackReader
import java.io.Reader

/** Reads bounded RFC-style CSV without leaking record values in exceptions. */
internal class TakeoutCsv(reader: Reader) {
    private val input = PushbackReader(reader, 1)
    private var atStart = true
    private val maxColumns = 256
    private val maxFieldChars = 65_536

    fun nextRow(): List<String>? {
        val values = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var seen = false
        fun append(ch: Char) {
            require(field.length < maxFieldChars) { "Pole CSV przekracza limit" }
            field.append(ch)
        }
        fun finishField() {
            require(values.size < maxColumns) { "Zbyt wiele kolumn CSV" }
            values.add(field.toString()); field.setLength(0); closedQuote = false
        }
        while (true) {
            var code = input.read()
            if (atStart) { atStart = false; if (code == 0xfeff) code = input.read() }
            if (code == -1) {
                require(!quoted) { "Niedomknięte pole CSV" }
                if (!seen && values.isEmpty() && field.isEmpty()) return null
                finishField(); return values
            }
            seen = true
            val ch = code.toChar()
            if (quoted) {
                if (ch == '"') {
                    val next = input.read()
                    if (next == '"'.code) append('"')
                    else { quoted = false; closedQuote = true; if (next != -1) input.unread(next) }
                } else append(ch)
                continue
            }
            when (ch) {
                '"' -> { require(field.isEmpty() && !closedQuote) { "Nieprawidłowy cudzysłów CSV" }; quoted = true }
                ',' -> finishField()
                '\n' -> { finishField(); return values }
                '\r' -> {
                    val next = input.read(); if (next != -1 && next != '\n'.code) input.unread(next)
                    finishField(); return values
                }
                else -> { require(!closedQuote) { "Nieprawidłowa zawartość po cudzysłowie CSV" }; append(ch) }
            }
        }
    }
}

internal fun readTakeoutCsv(reader: Reader, required: Set<String>, maxRows: Int = 200_000): List<Map<String, String>> {
    val csv = TakeoutCsv(reader)
    val header = csv.nextRow() ?: throw IllegalArgumentException("Brak nagłówka CSV")
    require(header.none { it.isBlank() } && header.distinct().size == header.size && header.containsAll(required)) { "Nieobsługiwany nagłówek CSV" }
    val rows = mutableListOf<Map<String, String>>()
    while (true) {
        val row = csv.nextRow() ?: break
        if (row.size == 1 && row[0].isBlank()) continue
        require(row.size == header.size) { "Nieprawidłowa liczba pól CSV" }
        require(rows.size < maxRows) { "Zbyt wiele rekordów CSV" }
        rows.add(header.zip(row).toMap())
    }
    return rows
}
