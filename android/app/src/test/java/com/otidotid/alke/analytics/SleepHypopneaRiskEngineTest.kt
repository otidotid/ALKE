package com.otidotid.alke.analytics

import com.otidotid.alke.data.GravitySample
import com.otidotid.alke.data.RrInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** No synthetic input may become a patient diagnosis; these are algorithm shape tests only. */
class SleepHypopneaRiskEngineTest {
    private val start = 1_792_000_000L
    private val length = 4L * 3600L
    private val end = start + length

    private fun beats(pattern: (Int) -> Int): List<RrInterval> {
        val out = ArrayList<RrInterval>()
        for (bucket in 0 until (length / 5L).toInt()) {
            val ms = pattern(bucket * 5)
            repeat(4) { j ->
                out.add(RrInterval("my-whoop", start + bucket * 5 + j, ms))
            }
        }
        return out
    }

    @Test fun missingNightIsUnavailableRatherThanReportedLow() {
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 3600L, emptyList(), emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.UNAVAILABLE, a.risk)
        assertFalse(a.isReady)
    }

    @Test fun unfinishedSleepIsNeverAnalyzed() {
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 100L,
            beats { 820 }, emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.WAITING, a.risk)
        assertFalse(a.isReady)
    }

    @Test fun shortNapIsNotAFullNightRiskCategory() {
        val a = SleepHypopneaRiskEngine.assess(start, start + 1800L, end + 3600L,
            beats { 820 }, emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.UNAVAILABLE, a.risk)
    }

    @Test fun stableHighCoverageBeatIntervalsProduceLowPatternEstimate() {
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 3600L,
            beats { 840 }, emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.LOW, a.risk)
        assertTrue(a.isReady)
        assertTrue((a.cvhrPatternsPerHour ?: -1.0) < 5.0)
    }

    @Test fun recurringSixtySecondBeatIntervalDipsYieldHighPatternEstimate() {
        // Repeatable illustrative CVHR-like shape: 40s-wide interval dip each 60s.
        // This is not a medical severity or sensitivity/specificity test.
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 3600L,
            beats { t ->
                val d = abs(t % 60 - 30)
                860 - (240.0 * max(0.0, 1.0 - d / 20.0)).toInt()
            }, emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.HIGH, a.risk)
        assertTrue(a.isReady)
    }

    @Test fun singlePulseDipCannotSatisfyAutocorrelationRule() {
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 3600L,
            beats { t ->
                val d = abs(t - 4000)
                860 - (240.0 * max(0.0, 1.0 - d / 20.0)).toInt()
            }, emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.LOW, a.risk)
    }

    @Test fun insufficientCoverageDoesNotBecomeLow() {
        val truncated = beats { 840 }.filter { it.ts < start + 3600L }
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 3600L,
            truncated, emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.UNAVAILABLE, a.risk)
    }

    @Test fun flaggedBeatsDoNotCreateRiskEvidence() {
        val invalid = beats { 840 }.map { it.copy(tsSuspect = 1) }
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 3600L,
            invalid, emptyList())
        assertEquals(SleepHypopneaRiskEngine.Risk.UNAVAILABLE, a.risk)
    }

    @Test fun strongMotionSuppressesPulseDipPattern() {
        val motion = (0 until length.toInt() step 5).map {
            GravitySample("my-whoop", start + it, 0.0, 0.0, 1.0, dynAccel = 0.6)
        }
        val a = SleepHypopneaRiskEngine.assess(start, end, end + 3600L,
            beats { t ->
                val d = abs(t % 60 - 30)
                860 - (240.0 * max(0.0, 1.0 - d / 20.0)).toInt()
            }, emptyList(), motion = motion)
        assertEquals(SleepHypopneaRiskEngine.Risk.LOW, a.risk)
    }
}
