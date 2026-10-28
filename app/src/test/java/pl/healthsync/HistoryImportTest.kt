package pl.healthsync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.LocalDate

class HistoryImportTest {
    private fun day(value: String) = LocalDate.parse(value)
    private class FakeStore : ManualHistoryStore {
        var session: Triple<List<String>, LocalDate, LocalDate>? = null
        override suspend fun prepareManual(metricIds: List<String>, start: LocalDate, endExclusive: LocalDate): LocalDate {
            val previous = session
            if (previous != null && previous.first == metricIds && previous.second == start && previous.third <= endExclusive) return previous.third
            invalidateAll(metricIds, chunks(start, endExclusive))
            session = Triple(metricIds, start, endExclusive)
            return endExclusive
        }
        override suspend fun finishManual(metricIds: List<String>, start: LocalDate, endExclusive: LocalDate) {
            if (session == Triple(metricIds, start, endExclusive)) session = null
        }
        val saved = mutableMapOf<Pair<String, DateChunk>, List<ImportedValue>>()
        override suspend fun isCompleted(metricId: String, range: DateChunk) = metricId to range in saved
        override suspend fun invalidateAll(metricIds: List<String>, ranges: List<DateChunk>) { metricIds.forEach { metric -> ranges.forEach { range -> saved.remove(metric to range) } } }
        override suspend fun commit(metricId: String, range: DateChunk, rows: List<ImportedValue>) { saved[metricId to range] = rows }
    }
    @Test fun `month chunks cover boundaries without gaps including leap day`() {
        assertEquals(listOf(
            DateChunk(day("2024-01-30"), day("2024-02-01")),
            DateChunk(day("2024-02-01"), day("2024-03-01")),
            DateChunk(day("2024-03-01"), day("2024-03-02"))
        ), chunks(day("2024-01-30"), day("2024-03-02")))
    }
    @Test fun `failed read does not create checkpoint and resumes completed chunks`() = runBlocking {
        val store = FakeStore()
        var shouldFail = true
        val reads = mutableListOf<DateChunk>()
        val source = object : HistorySource {
            override suspend fun read(metricId: String, range: DateChunk): List<ImportedValue> {
                reads += range
                if (shouldFail && range.start == day("2024-02-01")) error("offline")
                return listOf(ImportedValue(range.start, 0.0))
            }
        }
        val importer = HistoryImport(source, store) { day("2025-01-01") }
        try { importer.run(listOf("steps"), day("2024-01-01"), day("2024-03-01"), false) {} ; fail("expected failure") } catch (_: IllegalStateException) { }
        assertEquals(1, store.saved.size)
        shouldFail = false
        reads.clear()
        importer.run(listOf("steps"), day("2024-01-01"), day("2024-03-01"), false) {}
        assertEquals(listOf(day("2024-02-01")), reads.map { it.start })
        assertEquals(2, store.saved.size)
    }
    @Test fun `today is always reread and explicit refresh rereads historical chunks`() = runBlocking {
        val store = FakeStore()
        var count = 0
        val source = object : HistorySource { override suspend fun read(metricId: String, range: DateChunk): List<ImportedValue> { count++; return emptyList() } }
        val importer = HistoryImport(source, store) { day("2024-02-05") }
        repeat(2) { importer.run(listOf("steps"), day("2024-01-01"), day("2024-02-06"), false) {} }
        assertEquals(3, count)
        importer.run(listOf("steps"), day("2024-01-01"), day("2024-02-06"), true) {}
        assertEquals(5, count)
    }
    @Test fun `failed refresh clears previous checkpoint so resume retries`() = runBlocking {
        val store = FakeStore()
        var shouldFail = false
        val source = object : HistorySource {
            override suspend fun read(metricId: String, range: DateChunk): List<ImportedValue> {
                if (shouldFail) error("read failed")
                return emptyList()
            }
        }
        val importer = HistoryImport(source, store) { day("2025-01-01") }
        importer.run(listOf("steps"), day("2024-01-01"), day("2024-02-01"), false) {}
        shouldFail = true
        try { importer.run(listOf("steps"), day("2024-01-01"), day("2024-02-01"), true) {}; fail("expected failure") } catch (_: IllegalStateException) { }
        assertTrue(store.saved.isEmpty())
    }
    @Test fun `manual refresh resumes after failure and starts a fresh refresh after completion`() = runBlocking {
        val store = FakeStore()
        var fail = true
        val reads = mutableListOf<DateChunk>()
        val source = object : HistorySource {
            override suspend fun read(metricId: String, range: DateChunk): List<ImportedValue> {
                reads += range
                if (fail && range.start == day("2024-02-01")) error("read failed")
                return emptyList()
            }
        }
        val importer = HistoryImport(source, store) { day("2025-01-01") }
        try { importer.runManual(listOf("steps"), day("2024-01-01"), day("2024-03-01")) {}; fail("expected") } catch (_: IllegalStateException) { }
        assertNotNull(store.session)
        reads.clear(); fail = false
        importer.runManual(listOf("steps"), day("2024-01-01"), day("2024-03-01")) {}
        assertEquals(listOf(day("2024-02-01")), reads.map { it.start })
        assertNull(store.session)
        reads.clear()
        importer.runManual(listOf("steps"), day("2024-01-01"), day("2024-03-01")) {}
        assertEquals(listOf(day("2024-01-01"), day("2024-02-01")), reads.map { it.start })
    }

    @Test fun `resume on a later day refreshes the previously partial range and includes new days`() = runBlocking {
        val store = FakeStore()
        var currentDay = day("2024-03-10")
        var fail = true
        val reads = mutableListOf<Pair<String, DateChunk>>()
        val source = object : HistorySource {
            override suspend fun read(metricId: String, range: DateChunk): List<ImportedValue> {
                reads += metricId to range
                if (fail && metricId == "weight") error("interrupted")
                return emptyList()
            }
        }
        val importer = HistoryImport(source, store) { currentDay }
        try { importer.runManual(listOf("steps", "weight"), day("2024-03-01"), day("2024-03-11")) {}; fail("expected") } catch (_: IllegalStateException) { }
        currentDay = day("2024-03-11"); fail = false; reads.clear()
        importer.runManual(listOf("steps", "weight"), day("2024-03-01"), day("2024-03-12")) {}
        assertTrue(reads.contains("steps" to DateChunk(day("2024-03-01"), day("2024-03-11"))))
        assertTrue(reads.contains("steps" to DateChunk(day("2024-03-11"), day("2024-03-12"))))
        assertNull(store.session)
    }
}
