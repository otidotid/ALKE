package com.otidotid.alke.analytics

import com.otidotid.alke.data.RespSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class SleepBreathingRegularityEngineTest {
    private val start = 1_700_000_000L
    private val nightSeconds = 5 * 60 * 60L

    private fun recording(
        seconds: Long = nightSeconds,
        rateForMinute: (Int) -> Double = { 15.0 },
        keepMinute: (Int) -> Boolean = { true },
        flat: Boolean = false,
    ): List<RespSample> = (0L until seconds).mapNotNull { offset ->
        val minute = (offset / 60L).toInt()
        if (!keepMinute(minute)) return@mapNotNull null
        val bpm = rateForMinute(minute)
        // Each 5-minute window restarts phase so its physiology remains explicit.
        val withinWindow = (offset % 300L).toDouble()
        val raw = if (flat) 500 else (1000.0 * sin(2 * PI * withinWindow * bpm / 60.0)).roundToInt()
        RespSample("my-whoop", start + offset, raw)
    }

    @Test fun measuredSteadyNightIsDescriptiveNotDiagnostic() {
        val result = SleepBreathingRegularityEngine.assess(start, start + nightSeconds, 290.0, recording())
        assertEquals(SleepBreathingRegularityEngine.State.RELATIVELY_STEADY, result.state)
        assertTrue(result.hasEvidence)
        assertTrue(result.coveragePercent >= 90)
        assertTrue(result.windows.isNotEmpty())
        assertEquals(15.0, result.medianBpm!!, 0.1)
    }

    @Test fun moreVariableNightUsesDirectRespiratoryWindowsOnly() {
        val result = SleepBreathingRegularityEngine.assess(
            start, start + nightSeconds, 290.0,
            recording(rateForMinute = { if ((it / 5) % 2 == 0) 12.0 else 20.0 }),
        )
        assertEquals(SleepBreathingRegularityEngine.State.MORE_VARIABLE, result.state)
        assertTrue(result.variabilityPercent!! > SleepBreathingRegularityEngine.STEADY_MAD_PERCENT)
    }

    @Test fun noRawRespirationNeverBecomesNormalOrAtRisk() {
        val result = SleepBreathingRegularityEngine.assess(start, start + nightSeconds, 290.0, emptyList())
        assertEquals(SleepBreathingRegularityEngine.State.INSUFFICIENT_DATA, result.state)
        assertEquals(SleepBreathingRegularityEngine.Reason.NO_DIRECT_RESPIRATION, result.reason)
        assertFalse(result.hasEvidence)
        assertEquals(null, result.medianBpm)
    }

    @Test fun lessThanFourHoursOfSleepIsInsufficient() {
        val result = SleepBreathingRegularityEngine.assess(start, start + nightSeconds, 239.0, recording())
        assertEquals(SleepBreathingRegularityEngine.Reason.SLEEP_TOO_SHORT, result.reason)
        assertFalse(result.hasEvidence)
    }

    @Test fun missingAsleepDurationIsInsufficientEvenWhenBedTimeIsLong() {
        val result = SleepBreathingRegularityEngine.assess(start, start + nightSeconds, null, recording())
        assertEquals(SleepBreathingRegularityEngine.Reason.SLEEP_TOO_SHORT, result.reason)
    }

    @Test fun aNightWithLargeUnobservedGapsDoesNotInventRegularity() {
        val result = SleepBreathingRegularityEngine.assess(
            start, start + nightSeconds, 290.0,
            recording(keepMinute = { it / 5 < 25 }),
        )
        assertEquals(SleepBreathingRegularityEngine.State.INSUFFICIENT_DATA, result.state)
        assertEquals(SleepBreathingRegularityEngine.Reason.LOW_COVERAGE, result.reason)
    }

    @Test fun flatSensorCannotProduceAReassuringLabel() {
        val result = SleepBreathingRegularityEngine.assess(
            start, start + nightSeconds, 290.0, recording(flat = true),
        )
        assertEquals(SleepBreathingRegularityEngine.Reason.UNUSABLE_SIGNAL, result.reason)
    }

    @Test fun duplicateTimestampsAreNotMistakenForCoverage() {
        val oneMinute = recording(seconds = 60L)
        val duplicate = List(600) { oneMinute[it % oneMinute.size] }
        val result = SleepBreathingRegularityEngine.assess(start, start + nightSeconds, 290.0, duplicate)
        assertFalse(result.hasEvidence)
    }

    @Test fun invertedOrOversizedWindowsFailClosed() {
        assertEquals(
            SleepBreathingRegularityEngine.State.INSUFFICIENT_DATA,
            SleepBreathingRegularityEngine.assess(start + 1, start, 290.0, emptyList()).state,
        )
        assertEquals(
            SleepBreathingRegularityEngine.State.INSUFFICIENT_DATA,
            SleepBreathingRegularityEngine.assess(start, start + 20 * 3600L, 700.0, emptyList()).state,
        )
    }
}
