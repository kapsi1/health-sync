package pl.healthsync.domain

fun updateBackfillRequests(previous: Set<String>, selected: Set<String>, requests: Set<String>, newId: () -> String): Set<String> {
    val result = requests.filter { it.substringBefore('|') in selected }.toMutableSet()
    (selected - previous).forEach { id ->
        result.removeAll { it.substringBefore('|') == id }
        result.add("$id|${newId()}")
    }
    return result
}
