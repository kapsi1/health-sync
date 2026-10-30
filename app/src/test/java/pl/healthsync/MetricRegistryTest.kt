package pl.healthsync

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.*
import org.junit.Test

class MetricRegistryTest {
    @Test fun `retired columns remain managed separately from new metrics`() {
        val active = MetricRegistry.all.associateBy { it.id }
        assertFalse(active.keys.any { it in setOf("heart_rate_avg", "heart_rate_min", "heart_rate_max", "floors", "elevation", "height_avg", "hydration", "lean_body_mass", "vo2_max", "body_temperature", "respiratory_rate") })
        assertEquals("skin_temperature_delta_c", active.getValue("skin_temperature_delta").column)
        assertEquals("respiratory_sleep_avg", active.getValue("respiratory_rate_sleep").column)
        assertTrue(MetricRegistry.managedColumns.containsAll(setOf("heart_rate_avg_bpm", "body_temperature_avg_c", "respiratory_rate_avg")))
        assertEquals("respiratory_rate_avg", MetricRegistry.sheetHeaders.id("Oddechy/min"))
        assertEquals("respiratory_sleep_avg", MetricRegistry.sheetHeaders.id("Oddechy/min podczas snu"))
        assertEquals("body_temperature_avg_c", MetricRegistry.sheetHeaders.id("Temperatura ciała"))
    }

    @Test fun `sleep breathing requires both breathing and sleep permissions`() {
        val metric = MetricRegistry.all.single { it.id == "respiratory_rate_sleep" }
        assertTrue(requiredMetricPermissions(metric).contains(metric.permission))
        assertTrue(requiredMetricPermissions(metric).contains(HealthPermission.getReadPermission(SleepSessionRecord::class)))
    }
}
