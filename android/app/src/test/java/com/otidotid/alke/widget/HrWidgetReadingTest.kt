package com.otidotid.alke.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrWidgetReadingTest {
    private val now = 10_000_000L

    @Test fun staleGlancePreferenceCannotMaskNewerSnapshot() {
        val stored = WidgetSnapshot(
            heartRate = 83,
            heartRateAtMs = now - 2_000L,
            updatedAtMs = now - 2_000L,
            connected = true,
            heartRateStale = false,
        )
        val result = HrWidgetReading.resolve(
            snapshot = stored,
            streamBpm = 65,
            streamAtMs = now - 3L * 3_600_000L,
            streamActive = true,
            nowMs = now,
        )
        assertEquals(83, result.heartRate)
        assertEquals(now - 2_000L, result.heartRateAtMs)
        assertFalse(result.heartRateStale)
    }

    @Test fun freshGlanceSampleWinsOverMinuteOldSnapshot() {
        val stored = WidgetSnapshot(
            heartRate = 79, heartRateAtMs = now - 55_000L,
            connected = true, heartRateStale = true,
        )
        val result = HrWidgetReading.resolve(stored, 91, now - 1_000L, true, now)
        assertEquals(91, result.heartRate)
        assertFalse(result.heartRateStale)
        assertEquals(1, result.hrSeries.size)
        assertEquals(91, result.hrSeries.single().bpm)
    }

    @Test fun noStreamStillShowsLastKnownBpmWithHonestStaleState() {
        val stored = WidgetSnapshot(heartRate = 78, heartRateAtMs = now - 3_600_000L, connected = false)
        val result = HrWidgetReading.resolve(stored, null, 0L, false, now)
        assertEquals(78, result.heartRate)
        assertTrue(result.heartRateStale)
        assertTrue(result.hrSeries.isEmpty())
        assertEquals(now - 3_600_000L, result.updatedAtMs)
    }

    @Test fun threeHourOldGlanceReadingDoesNotClaimLiveOrGraph() {
        val stored = WidgetSnapshot()
        val result = HrWidgetReading.resolve(
            stored, streamBpm = 81,
            streamAtMs = now - 3L * 3_600_000L,
            streamActive = true, nowMs = now,
        )
        assertEquals(81, result.heartRate)
        assertTrue(result.heartRateStale)
        assertTrue(result.hrSeries.isEmpty())
    }

    @Test fun invalidOrFutureGlanceTimestampCannotReplaceSnapshot() {
        val stored = WidgetSnapshot(heartRate = 70, heartRateAtMs = now - 3_000L, connected = true)
        val future = HrWidgetReading.resolve(stored, 80, now + 3_000L, true, now)
        assertEquals(70, future.heartRate)
        val invalid = HrWidgetReading.resolve(stored, 0, now - 1_000L, true, now)
        assertEquals(70, invalid.heartRate)
    }

    @Test fun streamingBpmChangesDoNotRebuildHistoricalGraphEachSecond() {
        val point = HrPoint(ts = (now / 1000L / 60L) * 60L - 60L, bpm = 77)
        val stored = WidgetSnapshot(
            heartRate = 77, heartRateAtMs = now - 5_000L, hrSeries = listOf(point),
            connected = true,
        )
        val result = HrWidgetReading.resolve(stored, 86, now - 500L, true, now)
        assertEquals(listOf(point), result.hrSeries)
        assertEquals(86, result.heartRate)
    }
}
