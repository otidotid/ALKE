package com.otidotid.alke.ai

import com.otidotid.alke.analytics.SleepStageTotals
import com.otidotid.alke.data.CloudSleepLedger
import org.junit.Assert.*
import org.junit.Test

class CloudSleepOwnershipTest {
    @Test fun switchingModesDoesNotRewriteHistoryOrClaimAnEpisodeAcrossRevocation() {
        val history = CloudSleepLedger.History(listOf(
            CloudSleepLedger.Mode(100, true), CloudSleepLedger.Mode(200, false),
            CloudSleepLedger.Mode(350, true)), emptyList())
        assertFalse(history.owns(99))
        assertTrue(history.covers(100, 200))
        assertFalse(history.covers(190, 210))
        assertFalse(history.touches(200, 350))
        assertTrue(history.touches(320, 400))
    }

    @Test fun supplementalCategoriesCannotBecomeMainSleepOrBridgeIntoIt() {
        val nap = SleepStageTotals.NightBlock(1000, 2000, "NAP")
        val day = SleepStageTotals.NightBlock(2100, 2600, "DAY_SLEEP")
        val main = SleepStageTotals.NightBlock(2750, 3500, "MAIN_SLEEP")
        assertNull(SleepStageTotals.mainNightIndex(listOf(nap, day), 0))
        assertNull(SleepStageTotals.mainNightGroupIndices(listOf(nap, day), 0))
        assertEquals(listOf(2), SleepStageTotals.mainNightGroupIndices(listOf(nap, day, main), 0))
        assertEquals(listOf(1), SleepStageTotals.mainNightGroupIndices(listOf(nap, main.copy(episodeType = null)), 0))
    }
}
