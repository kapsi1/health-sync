package pl.healthsync

import org.junit.Assert.*
import org.junit.Test
import pl.healthsync.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TakeoutActivityEstimateTest {
    private val day = LocalDate.parse("2026-06-12")
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun sample(time: String, value: Double, source: ActivitySourceCategory) = TakeoutActivitySample(Instant.parse(time), value, source)
    private fun estimate(vararg samples: TakeoutActivitySample) = estimateTakeoutActivityDay(day, zone, samples.asSequence())

    @Test fun `wearable wins overlapping minutes while phone fills other minutes`() {
        val result = estimate(sample("2026-06-12T07:00:00Z", 5.0, ActivitySourceCategory.WEARABLE),
            sample("2026-06-12T07:00:20Z", 8.0, ActivitySourceCategory.PHONE),
            sample("2026-06-12T07:01:00Z", 3.0, ActivitySourceCategory.PHONE))
        assertEquals(8.0, result.value!!, 0.0)
    }

    @Test fun `google health wins over fit and connect records are excluded`() {
        val result = estimate(sample("2026-06-12T07:00:00Z", 7.0, ActivitySourceCategory.GOOGLE_HEALTH),
            sample("2026-06-12T07:00:00Z", 9.0, ActivitySourceCategory.GOOGLE_FIT),
            sample("2026-06-12T07:01:00Z", 50.0, ActivitySourceCategory.CONNECT))
        assertEquals(7.0, result.value!!, 0.0)
        assertEquals(1, result.excludedConnectSamples)
    }

    @Test fun `ambiguous or unknown source invalidates estimate`() {
        assertNull(estimate(sample("2026-06-12T07:00:00Z", 1.0, ActivitySourceCategory.UNKNOWN)).value)
        assertNull(estimate(sample("2026-06-12T07:00:00Z", 1.0, ActivitySourceCategory.PHONE),
            sample("2026-06-12T07:00:30Z", 1.0, ActivitySourceCategory.PHONE)).value)
    }

    @Test fun `only the selected local day contributes`() {
        val result = estimate(sample("2026-06-11T21:59:00Z", 99.0, ActivitySourceCategory.WEARABLE),
            sample("2026-06-11T22:00:00Z", 2.0, ActivitySourceCategory.WEARABLE),
            sample("2026-06-12T22:00:00Z", 99.0, ActivitySourceCategory.WEARABLE))
        assertEquals(2.0, result.value!!, 0.0)
    }

    @Test fun `calories sum distinct times in one minute but block identical timestamps`() {
        val sum = TakeoutActivityDayAccumulator(sumDistinctSameMinute = true)
        sum.add(sample("2026-06-03T18:57:00Z", 2.21013, ActivitySourceCategory.GOOGLE_HEALTH))
        sum.add(sample("2026-06-03T18:57:02Z", 1.96456, ActivitySourceCategory.GOOGLE_HEALTH))
        assertEquals(4.17469, sum.result().value!!, 0.000001)
        sum.add(sample("2026-06-03T18:57:02Z", 1.96456, ActivitySourceCategory.GOOGLE_HEALTH))
        assertNull(sum.result().value)
        assertEquals("Powtórzony czas wpisu źródła", sum.result().reason)
    }
}
