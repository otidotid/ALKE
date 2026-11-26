package com.otidotid.alke.analytics

import com.otidotid.alke.data.DailyMetric
import com.otidotid.alke.data.SleepSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnifiedSleepRestTest {
    private val start=1_800_000_000L
    private val main=SleepSession("strap",start,start+7*3600,
        stagesJSON="""{"awake":60,"light":180,"deep":90,"rem":90}""")
    private val napStart=start+13*3600
    private val nap=SleepSession("strap",napStart,napStart+3600,
        stagesJSON="""{"awake":10,"light":20,"deep":20,"rem":10}""")
    private val night=DailyMetric("strap","2027-01-15",totalSleepMin=360.0,
        efficiency=360.0/420.0,lightMin=180.0,deepMin=90.0,remMin=90.0)
    @Test fun aggregatesDurationEfficiencyAndRestorativeStagesWithoutMutatingNight() {
        val result=UnifiedSleepRest.score(night,listOf(main,nap),0,null)!!
        assertEquals(410.0,result.asleepMin,1e-9)
        assertEquals(480.0,result.inBedMin,1e-9)
        assertEquals(110.0,result.deepMin,1e-9)
        assertEquals(100.0,result.remMin,1e-9)
        assertEquals(50.0,result.supplementalMin,1e-9)
        assertEquals(RestScorer.rest(410*60.0,410.0/480,110*60.0,100*60.0)!!,result.rest,1e-9)
        assertEquals(360.0,night.totalSleepMin!!,1e-9)
        assertEquals(90.0,night.deepMin!!,1e-9)
    }
    @Test fun refusesOverlapMissingStagesAndAlreadyCombinedMain() {
        assertNull(UnifiedSleepRest.score(night,listOf(main,nap.copy(startTs=start+3600,endTs=start+7200)),0,null))
        assertNull(UnifiedSleepRest.score(night,listOf(main,nap.copy(stagesJSON=null)),0,null))
        assertNull(UnifiedSleepRest.score(night.copy(totalSleepMin=410.0),listOf(main,nap),0,null))
        assertNull(UnifiedSleepRest.score(night,listOf(main,nap,nap.copy(deviceId="other")),0,null))
    }
    @Test fun rejectsIncompleteEpochsRatherThanInferringWakeOrRestorativeSleep() {
        val hole=nap.copy(stagesJSON="""[{"start":$napStart,"end":${napStart+1800},"stage":"light"}]""")
        assertNull(UnifiedSleepRest.score(night,listOf(main,hole),0,null))
    }
    @Test fun sleepTargetIsAnExplicitInput() {
        val a=UnifiedSleepRest.score(night,listOf(main,nap),0,null,8.0)!!
        val b=UnifiedSleepRest.score(night,listOf(main,nap),0,null,9.0)!!
        assertTrue(a.rest>b.rest)
    }
}
