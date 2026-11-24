package com.otidotid.alke.ui

import com.otidotid.alke.analytics.SleepHypopneaRiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HypopneaTodaySelectionTest {
    private fun state(day: String?, risk: SleepHypopneaRiskEngine.Risk) =
        HypopneaTodaySelection.State(day, SleepHypopneaRiskEngine.Assessment(risk))

    @Test fun newMorningWithInsufficientEvidenceCannotReuseYesterdaysLow() {
        val previous = state("2026-11-23", SleepHypopneaRiskEngine.Risk.LOW)
        val next = state("2026-11-24", SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        val result = HypopneaTodaySelection.choose(previous, next)
        assertEquals("2026-11-24", result.wakeDay)
        assertEquals(SleepHypopneaRiskEngine.Risk.UNAVAILABLE, result.assessment.risk)
        assertFalse(result.assessment.isReady)
    }

    @Test fun newMorningWhileSettlingCannotReuseYesterdaysHigh() {
        val previous = state("2026-11-23", SleepHypopneaRiskEngine.Risk.HIGH)
        val next = state("2026-11-24", SleepHypopneaRiskEngine.Risk.WAITING)
        assertEquals(SleepHypopneaRiskEngine.Risk.WAITING,
            HypopneaTodaySelection.choose(previous, next).assessment.risk)
    }

    @Test fun daytimeSyncCannotFlashPendingForSameCompletedMorning() {
        val previous = state("2026-11-24", SleepHypopneaRiskEngine.Risk.MEDIUM)
        val next = state("2026-11-24", SleepHypopneaRiskEngine.Risk.WAITING)
        assertEquals(previous, HypopneaTodaySelection.choose(previous, next))
    }

    @Test fun temporaryEmptySessionSnapshotRetainsLastCompletedMorning() {
        val previous = state("2026-11-24", SleepHypopneaRiskEngine.Risk.LOW)
        val next = state(null, SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        assertEquals(previous, HypopneaTodaySelection.choose(previous, next))
    }

    @Test fun adequateEvidenceCompletesNewMorningWithoutChangingItsDate() {
        val previous = state("2026-11-24", SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        val next = state("2026-11-24", SleepHypopneaRiskEngine.Risk.LOW)
        val result = HypopneaTodaySelection.choose(previous, next)
        assertEquals("2026-11-24", result.wakeDay)
        assertTrue(result.assessment.isReady)
    }
}
