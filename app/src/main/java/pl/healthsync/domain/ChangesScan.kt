package pl.healthsync.domain

import java.time.LocalDate

data class ChangesPage(val dates: Set<LocalDate>, val hasDeletion: Boolean, val nextToken: String, val more: Boolean, val expired: Boolean)

interface ChangesSource {
    suspend fun initialToken(metric: String): String
    suspend fun page(metric: String, token: String): ChangesPage
    suspend fun refreshDay(metric: String, date: LocalDate)
}

interface ChangesState {
    suspend fun token(metric: String): String?
    suspend fun save(metric: String, token: String, deletionSeen: Boolean)
}

enum class ChangesOutcome { INITIALIZED, CHECKED, EXPIRED }

class ChangesScan(private val source: ChangesSource, private val state: ChangesState) {
    suspend fun run(metric: String): ChangesOutcome {
        var token = state.token(metric)
        if (token == null) {
            state.save(metric, source.initialToken(metric), false)
            return ChangesOutcome.INITIALIZED
        }
        do {
            val page = source.page(metric, token!!)
            if (page.expired) return ChangesOutcome.EXPIRED
            page.dates.sorted().forEach { source.refreshDay(metric, it) }
            state.save(metric, page.nextToken, page.hasDeletion)
            token = page.nextToken
        } while (page.more)
        return ChangesOutcome.CHECKED
    }
}
