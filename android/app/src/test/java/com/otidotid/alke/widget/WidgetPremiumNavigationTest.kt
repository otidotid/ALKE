package com.otidotid.alke.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPremiumNavigationTest {
    @Test fun widgetNavigationOnlyAcceptsKnownHeartRateAction() {
        assertEquals(
            "alke_hr_timeline",
            WidgetNavigation.routeForAction(WidgetNavigation.ACTION_HEART_RATE_TIMELINE),
        )
        assertNull(WidgetNavigation.routeForAction(null))
        assertNull(WidgetNavigation.routeForAction("android.intent.action.MAIN"))
        assertNull(WidgetNavigation.routeForAction("alke_hr_timeline"))
        assertNull(WidgetNavigation.routeForAction("com.otidotid.alke.action.OPEN_SETTINGS"))
    }

    @Test fun aFreshStreamOverridesTheSlowerSnapshot() {
        val stored = WidgetSnapshot(
            heartRate = 65,
            heartRateAtMs = 50_000L,
            heartRateStale = true,
            connected = true,
        )
        val state = PremiumWidgetStateEngine.readLive(
            stored, streamBpm = 81, streamAtMs = 100_000L,
            streamActive = true, nowMs = 101_000L,
        )
        assertEquals(81, state.heartRate)
        assertEquals(PremiumHrStatus.LIVE, state.heartRateStatus)
        assertEquals(100_000L, state.heartRateAtMs)
    }

    @Test fun disconnectedOrOldStreamIsNotPresentedAsRealtime() {
        val stored = WidgetSnapshot(heartRate = 71, connected = false)
        val stale = PremiumWidgetStateEngine.readLive(
            stored, streamBpm = 79, streamAtMs = 100_000L,
            streamActive = true, nowMs = 120_000L,
        )
        assertEquals(PremiumHrStatus.DISCONNECTED, stale.heartRateStatus)
        val inactive = PremiumWidgetStateEngine.readLive(
            stored.copy(connected = true),
            streamBpm = 79, streamAtMs = 100_000L, streamActive = false,
            nowMs = 101_000L,
        )
        assertEquals(PremiumHrStatus.LAST_RECORDED, inactive.heartRateStatus)
    }

    @Test fun invalidStreamNeverReplacesKnownGoodSnapshot() {
        val stored = WidgetSnapshot(heartRate = 68, heartRateAtMs = 100_000L, connected = true)
        val state = PremiumWidgetStateEngine.readLive(
            stored, streamBpm = 0, streamAtMs = 101_000L,
            streamActive = true, nowMs = 101_000L,
        )
        assertEquals(68, state.heartRate)
    }

    @Test fun batteryGaugeColorUsesSafeBands() {
        assertEquals(PremiumWidgetPalette.Red, batteryAccent(12))
        assertEquals(PremiumWidgetPalette.Amber, batteryAccent(33))
        assertEquals(PremiumWidgetPalette.Green, batteryAccent(88))
        assertEquals(PremiumWidgetPalette.Secondary, batteryAccent(null))
    }
}
