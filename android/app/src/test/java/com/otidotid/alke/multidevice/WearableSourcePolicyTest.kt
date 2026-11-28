package com.otidotid.alke.multidevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.otidotid.alke.multidevice.WearableSourcePolicy as Policy
import com.otidotid.alke.multidevice.WearableSourcePolicy.Metric
import com.otidotid.alke.multidevice.WearableSourcePolicy.Source

class WearableSourcePolicyTest {
    private fun sample(
        source: Source,
        value: Int,
        valid: Boolean = true,
        compatible: Boolean = true,
        revision: Long = 1,
        id: String = source.name,
    ) = Policy.Candidate(source, value, valid, compatible, revision, id)

    @Test fun exactApprovedPriorityMatrix() {
        val h = Source.HUAWEI
        val w = Source.WHOOP
        val expected = mapOf(
            Metric.HEART_RATE to listOf(h, w),
            Metric.HRV to listOf(w, h),
            Metric.RR_INTERVAL to listOf(w, h),
            Metric.SLEEP to listOf(w, h),
            Metric.STEPS to listOf(h, w),
            Metric.GPS to listOf(h, Source.PHONE_GPS),
            Metric.WORKOUT_DETAIL to listOf(h, w),
            Metric.SPO2 to listOf(w, h),
        )
        assertEquals(Metric.values().toSet(), expected.keys)
        expected.forEach { (metric, sources) ->
            assertEquals(sources, Policy.priority(metric))
            val input = sources.reversed().mapIndexed { i, source -> sample(source, i) }
            assertEquals(sources.first(), Policy.select(metric, input)?.source)
            assertEquals(sources.last(), Policy.select(metric, listOf(input.first()))?.source)
        }
    }

    @Test fun daytimeHuaweiAndContinuousWhoop() {
        val w = sample(Source.WHOOP, 72)
        val h = sample(Source.HUAWEI, 77)
        assertEquals(w, Policy.select(Metric.HEART_RATE, listOf(w)))
        assertEquals(h, Policy.select(Metric.HEART_RATE, listOf(w, h)))
        assertEquals(w, Policy.select(Metric.HEART_RATE, listOf(w, h.copy(valid = false))))
        assertEquals(w, Policy.select(Metric.HEART_RATE, listOf(w)))
    }

    @Test fun incompatibleHrvAndMissingDataStayMissing() {
        val h = sample(Source.HUAWEI, 60, compatible = false)
        assertNull(Policy.select(Metric.HRV, listOf(h)))
        assertNull(Policy.select<Int>(Metric.RR_INTERVAL, emptyList()))
        assertNull(Policy.select(Metric.GPS, listOf(sample(Source.WHOOP, 1))))
    }

    @Test fun latePrimaryAndRepeatSyncAreDeterministic() {
        val w = sample(Source.WHOOP, 100, revision = 4)
        val h = sample(Source.HUAWEI, 90)
        val updated = h.copy(value = 95, revision = 2)
        assertEquals(w, Policy.select(Metric.STEPS, listOf(w)))
        assertEquals(updated, Policy.select(Metric.STEPS, listOf(w, h, updated, updated)))
        assertEquals(updated, Policy.select(Metric.STEPS, listOf(updated, h, w)))
    }
}
