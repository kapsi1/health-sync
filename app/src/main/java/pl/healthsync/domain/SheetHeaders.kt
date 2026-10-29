package pl.healthsync.domain

/** Stable identifiers stay in app storage; only the labels are visible in Sheets. */
class SheetHeaders(labels: Map<String, String>, aliases: Map<String, String> = emptyMap()) {
    private val names = mapOf("date" to "Data") + labels
    private val primaryIdentifiers = names.entries.associate { it.value to it.key }
    private val identifiers = primaryIdentifiers + aliases

    init {
        require(names.size == labels.size + 1 && names.values.none { it.isBlank() })
        require(primaryIdentifiers.size == names.size) { "Nazwy kolumn aplikacji muszą być unikalne" }
        require(aliases.keys.intersect(primaryIdentifiers.keys).isEmpty() && aliases.keys.intersect(names.keys).isEmpty() && aliases.values.all { it in names }) { "Niejednoznaczne dawne nazwy kolumn" }
        require(names.keys.intersect(names.values.toSet()).isEmpty()) { "Nazwa kolumny koliduje z identyfikatorem" }
    }

    fun label(id: String): String = names[id] ?: id
    fun id(header: String): String = identifiers[header] ?: header

    /** Reject ambiguous legacy/new labels and formula-generated managed headers. */
    fun resolve(displayed: List<String>, formulas: List<String>): List<String> {
        val canonical = displayed.map(::id)
        require(canonical.filter { it.isNotBlank() }.distinct().size == canonical.count { it.isNotBlank() }) {
            "Niejednoznaczne nagłówki: usuń powtórzoną kolumnę aplikacji"
        }
        displayed.forEachIndexed { index, header ->
            if (id(header) in names && formulas.getOrNull(index).orEmpty() != header) {
                throw IllegalArgumentException("Nagłówek kolumny aplikacji nie może być formułą")
            }
        }
        return canonical
    }
}
