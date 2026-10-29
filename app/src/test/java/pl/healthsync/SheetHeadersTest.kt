package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.LocalDate

class SheetHeadersTest {
    private val headers = SheetHeaders(mapOf("steps" to "Kroki", "distance_km" to "Dystans", "sleep_hours" to "Czas snu"))

    @Test fun `legacy and polish headers map to the same stable ids after columns move`() {
        val visible = listOf("Data", "Własna notatka", "Dystans", "steps", "Czas snu")
        val canonical = headers.resolve(visible, visible)
        assertEquals(listOf("date", "Własna notatka", "distance_km", "steps", "sleep_hours"), canonical)
        val row = SheetRow(LocalDate.parse("2026-10-29"), mapOf("distance_km" to 2.3, "sleep_hours" to 7.5))
        val plan = planSheetMerge(canonical, listOf("2026-10-29"), listOf(row), row.cells.keys)
        assertEquals(setOf(SheetCellUpdate(2, 3, 2.3), SheetCellUpdate(2, 5, 7.5)), plan.updates.toSet())
        assertTrue(plan.newColumns.isEmpty())
        assertEquals("Dystans", headers.label("distance_km"))
        assertEquals("Własna notatka", headers.label("Własna notatka"))
    }

    @Test fun `visibility uses internal ids for localized headers`() {
        val visible = listOf("Data", "Dystans", "Własna", "Kroki")
        val canonical = headers.resolve(visible, visible)
        assertEquals(listOf(ColumnVisibility(1, true), ColumnVisibility(3, false)),
            planColumnVisibility(canonical, setOf("distance_km", "steps"), setOf("steps")))
    }

    @Test(expected = IllegalArgumentException::class) fun `duplicate legacy and translated columns stop before writing`() {
        val visible = listOf("date", "distance_km", "Dystans")
        headers.resolve(visible, visible)
    }

    @Test(expected = IllegalArgumentException::class) fun `formula returning managed label is never overwritten`() {
        headers.resolve(listOf("Data", "Dystans"), listOf("Data", "=\"Dystans\""))
    }

    @Test fun `repeated migration recognizes already translated labels`() {
        val old = listOf("date", "steps", "distance_km")
        val canonical = headers.resolve(old, old)
        val translated = canonical.map(headers::label)
        assertEquals(listOf("Data", "Kroki", "Dystans"), translated)
        assertEquals(canonical, headers.resolve(translated, translated))
    }

    @Test fun `old labels resolve to unchanged identifiers and current labels`() {
        val map = SheetHeaders(mapOf("weight_avg_kg" to "Waga", "body_fat_avg_percent" to "% tłuszczu"),
            mapOf("Średnia masa ciała" to "weight_avg_kg", "Średnia tkanka tłuszczowa" to "body_fat_avg_percent"))
        val old = listOf("Data", "Średnia masa ciała", "Średnia tkanka tłuszczowa")
        val resolved = map.resolve(old, old)
        assertEquals(listOf("date", "weight_avg_kg", "body_fat_avg_percent"), resolved)
        assertEquals(listOf("Data", "Waga", "% tłuszczu"), resolved.map(map::label))
    }

    @Test(expected = IllegalArgumentException::class) fun `old and new name for the same column are rejected`() {
        val map = SheetHeaders(mapOf("weight_avg_kg" to "Waga"), mapOf("Średnia masa ciała" to "weight_avg_kg"))
        val both = listOf("Data", "Waga", "Średnia masa ciała")
        map.resolve(both, both)
    }

    @Test fun `old weight and distance headers retain their identities with unit labels`() {
        val map = SheetHeaders(mapOf("weight_avg_kg" to "Waga kg", "distance_km" to "Dystans km"),
            mapOf("Waga" to "weight_avg_kg", "Średnia masa ciała" to "weight_avg_kg", "Dystans" to "distance_km"))
        val old = listOf("Data", "Dystans", "Waga")
        val canonical = map.resolve(old, old)
        assertEquals(listOf("date", "distance_km", "weight_avg_kg"), canonical)
        assertEquals(listOf("Data", "Dystans km", "Waga kg"), canonical.map(map::label))
        assertEquals("weight_avg_kg", map.id("Średnia masa ciała"))
    }

    @Test fun `previous last measurements stay separate and are hidden when mean is selected`() {
        val map = SheetHeaders(mapOf("body_fat_avg_percent" to "Średnia tkanka tłuszczowa",
            "body_fat_percent" to "Tkanka tłuszczowa — ostatni pomiar"))
        val original = listOf("Data", "Tkanka tłuszczowa — ostatni pomiar")
        val canonical = map.resolve(original, original)
        val row = SheetRow(LocalDate.parse("2026-10-29"), mapOf("body_fat_avg_percent" to 20.0))
        val plan = planSheetMerge(canonical, listOf("2026-10-29"), listOf(row), row.cells.keys)
        assertEquals(listOf("body_fat_avg_percent"), plan.newColumns)
        assertEquals(listOf(SheetCellUpdate(2, 3, 20.0)), plan.updates)
        assertEquals(listOf(ColumnVisibility(1, true), ColumnVisibility(2, false)),
            planColumnVisibility(canonical + plan.newColumns, setOf("body_fat_percent", "body_fat_avg_percent"), row.cells.keys))
    }
}
