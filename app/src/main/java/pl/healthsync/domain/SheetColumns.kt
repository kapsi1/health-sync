package pl.healthsync.domain

data class ColumnVisibility(val index: Int, val hidden: Boolean)

fun planColumnVisibility(headers: List<String>, known: Set<String>, selected: Set<String>): List<ColumnVisibility> {
    require(selected.all { it in known })
    require(headers.firstOrNull() == "date")
    require(headers.filter { it.isNotEmpty() }.distinct().size == headers.count { it.isNotEmpty() })
    return headers.mapIndexedNotNull { index, header ->
        if (index == 0 || header !in known) null else ColumnVisibility(index, header !in selected)
    }
}
