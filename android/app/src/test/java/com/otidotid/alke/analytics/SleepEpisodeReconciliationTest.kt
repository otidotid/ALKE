package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepEpisodeReconciliationTest {
    private fun sleep(start: Long, end: Long, type: String? = null) =
        DetectedSleep(start, end, 0.9, emptyList(), 55, 45.0)
            .copy(cloudEpisodeType = type)

    @Test fun localNightSurvivesMissingCloudResult() {
        val main = sleep(1000, 28000)
        assertEquals(listOf(main), reconcileSleepEpisodes(listOf(main), emptyList()))
    }

    @Test fun misclassifiedCloudNapCannotEraseMainSleep() {
        val main = sleep(1000, 28000)
        val cloudNap = sleep(2000, 5000, "NAP")
        assertEquals(listOf(main), reconcileSleepEpisodes(listOf(main), listOf(cloudNap)))
    }

    @Test fun disjointCloudNapAndDaySleepRemainAvailable() {
        val main = sleep(1000, 28000)
        val nap = sleep(30000, 32000, "NAP")
        val daytime = sleep(40000, 46000, "DAY_SLEEP")
        assertEquals(listOf(main, nap, daytime),
            reconcileSleepEpisodes(listOf(main), listOf(daytime, nap)))
    }

    @Test fun importedHypnogramStillWinsOverLocal() {
        val local = sleep(1000, 28000)
        val imported = sleep(1200, 27000)
        assertEquals(listOf(imported), reconcileSleepEpisodes(listOf(local), listOf(imported)))
    }
}
