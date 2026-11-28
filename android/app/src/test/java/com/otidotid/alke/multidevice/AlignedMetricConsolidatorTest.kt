package com.otidotid.alke.multidevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.otidotid.alke.multidevice.AlignedMetricConsolidator as C
import com.otidotid.alke.multidevice.WearableSourcePolicy as P

class AlignedMetricConsolidatorTest {
    private val windows = (0L..3L).map { C.Interval(it * 60, (it + 1) * 60) }

    private fun observation(i: Int, source: P.Source, value: Int, valid: Boolean = true) =
        C.Observation(windows[i], P.Candidate(source, value, valid, true, 1, source.name))

    @Test fun fallbackAndMissingCoverageWithoutDoubleCounting() {
        val rows = listOf(
            observation(0, P.Source.WHOOP, 10),
            observation(1, P.Source.WHOOP, 20),
            observation(1, P.Source.HUAWEI, 18),
            observation(2, P.Source.WHOOP, 30),
            observation(2, P.Source.HUAWEI, 999, false),
        )
        val output = C.reconcile(P.Metric.STEPS, windows, rows)
        assertEquals(listOf(10, 18, 30, null), output.map { it.candidate?.value })
        assertEquals(output, C.reconcile(P.Metric.STEPS, windows, rows + rows))
    }

    @Test fun rejectsUnalignedAndOverlappingWindows() {
        assertTrue(runCatching {
            C.reconcile(P.Metric.STEPS, listOf(C.Interval(0, 60), C.Interval(30, 90)),
                emptyList<C.Observation<Int>>())
        }.isFailure)
        assertTrue(runCatching {
            C.reconcile(P.Metric.STEPS, windows,
                listOf(observation(0, P.Source.WHOOP, 10).copy(interval = C.Interval(0, 120))))
        }.isFailure)
    }
}
