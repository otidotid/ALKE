package com.otidotid.alke.multidevice.huawei

import com.otidotid.alke.data.HrBucket
import org.junit.Assert.*
import org.junit.Test

class HuaweiTimelineSourceTest {
    private fun minute(t: Long, hr: Int?) = HuaweiFitnessRead.Minute(t, null, hr, null)
    private fun bucket(t: Long, avg: Double, min: Double = avg, max: Double = avg) = HrBucket(t, avg, min, max)

    @Test fun noHuaweiPreservesExactWhoopBuckets() {
        val original = listOf(bucket(0, 73.5, 54.0, 102.0))
        val result = HuaweiTimelineSource.combine(original, emptyList(),
            listOf(minute(60, null), minute(61, 80), minute(120, 255)), 300)
        assertSame(original, result.buckets)
        assertTrue(result.huaweiBucketStarts.isEmpty())
    }

    @Test fun primaryOnlyReplacesAlignedMinuteAndFallbackFillsRemainingWindows() {
        val untouched = bucket(300, 81.5, 60.0, 106.0)
        val result = HuaweiTimelineSource.combine(
            listOf(bucket(0, 70.0), untouched),
            listOf(bucket(0, 70.0, 65.0, 75.0), bucket(60, 60.0, 40.0, 110.0),
                bucket(120, 90.0, 85.0, 95.0)),
            listOf(minute(60, 80), minute(120, null), minute(180, 100)), 300)
        assertEquals(listOf(bucket(0, 85.0, 65.0, 100.0), untouched), result.buckets)
        assertEquals(setOf(0L), result.huaweiBucketStarts)
    }

    @Test fun historicalHuaweiOnlyAddsObservedBucketsWithoutFillingGaps() {
        val result = HuaweiTimelineSource.combine(emptyList(), emptyList(),
            listOf(minute(60, 70), minute(660, 90)), 300)
        assertEquals(listOf(bucket(0, 70.0), bucket(600, 90.0)), result.buckets)
        assertEquals(setOf(0L, 600L), result.huaweiBucketStarts)
    }
}
