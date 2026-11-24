package com.otidotid.alke.ui

import com.otidotid.alke.analytics.SleepHypopneaRiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** v619: Today and Sleep MUST NOT misattribute yesterday's risk to a new night. */
class SleepHypopneaNightStateTest {
    private fun night(key: String?, risk: SleepHypopneaRiskEngine.Risk) =
        SleepHypopneaData.NightResult(key, SleepHypopneaRiskEngine.Assessment(risk))

    @Test fun newerNightWithInsufficientCoverageDoesNotKeepYesterdayLow() {
        val yesterday = night("device:A:2026-11-23:100", SleepHypopneaRiskEngine.Risk.LOW)
        val today = night("device:A:2026-11-24:300", SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        assertEquals(today, SleepHypopneaData.visibleResult(yesterday, today))
    }

    @Test fun newerNightWaitingDoesNotKeepYesterdayHigh() {
        val yesterday = night("device:A:2026-11-23:100", SleepHypopneaRiskEngine.Risk.HIGH)
        val today = night("device:A:2026-11-24:300", SleepHypopneaRiskEngine.Risk.WAITING)
        assertEquals(today, SleepHypopneaData.visibleResult(yesterday, today))
    }

    @Test fun sameNightDaytimeSyncDoesNotFlashPending() {
        val saved = night("device:A:2026-11-24:300", SleepHypopneaRiskEngine.Risk.MEDIUM)
        val loading = night(saved.identity, SleepHypopneaRiskEngine.Risk.WAITING)
        assertEquals(saved, SleepHypopneaData.visibleResult(saved, loading))
    }

    @Test fun sameNightTransientInsufficientSamplesKeepsPinnedRisk() {
        val saved = night("device:A:2026-11-24:300", SleepHypopneaRiskEngine.Risk.LOW)
        val temporary = night(saved.identity, SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        assertEquals(saved, SleepHypopneaData.visibleResult(saved, temporary))
    }

    @Test fun firstRiskFromNewNightOverridesWaiting() {
        val loading = night("device:A:2026-11-24:300", SleepHypopneaRiskEngine.Risk.WAITING)
        val scored = night(loading.identity, SleepHypopneaRiskEngine.Risk.LOW)
        assertEquals(scored, SleepHypopneaData.visibleResult(loading, scored))
    }

    @Test fun temporaryEmptySessionListDoesNotErasePinnedNight() {
        val saved = night("device:A:2026-11-24:300", SleepHypopneaRiskEngine.Risk.LOW)
        val temporarilyEmpty = night(null, SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        assertEquals(saved, SleepHypopneaData.visibleResult(saved, temporarilyEmpty))
    }

    @Test fun noKnownNightAndNoPreviousVerdictStaysUnavailable() {
        val noNight = night(null, SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        assertEquals(noNight, SleepHypopneaData.visibleResult(null, noNight))
    }

    @Test fun sameWakeDayDifferentDetectedSessionGetsDifferentIdentity() {
        // Full regression for the previous day-only cache collision.
        val first = SleepHypopneaData.identity("strap-A", 1795482000L, 1795450000L)
        val second = SleepHypopneaData.identity("strap-A", 1795482000L, 1795454000L)
        assertNotEquals(first, second)
        assertNotEquals(first, SleepHypopneaData.identity("strap-B", 1795482000L, 1795450000L))
    }
}
