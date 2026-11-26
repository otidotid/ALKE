package com.otidotid.alke.analytics
import com.otidotid.alke.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyRestCreditTest {
    private val day = DailyMetric("strap","2026-11-26",totalSleepMin=360.0,
        efficiency=0.9,deepMin=70.0,remMin=90.0,lightMin=200.0)
    @Test fun napChangesOnlyExistingDurationTerm() {
        val base = RestScorer.restFromDaily(day)!!
        assertEquals(base + 6.25,RestScorer.dailyRestFromDaily(day,60.0)!!,0.011)
        assertEquals(base,RestScorer.restFromDaily(day)!!,0.0)
    }
    @Test fun creditCapsAtNeedAndInvalidCreditIsIgnored() {
        assertEquals(RestScorer.dailyRestFromDaily(day,120.0)!!,RestScorer.dailyRestFromDaily(day,300.0)!!,0.0)
        assertEquals(RestScorer.restFromDaily(day)!!,RestScorer.dailyRestFromDaily(day,Double.NaN)!!,0.0)
        assertEquals(92.0,RestScorer.dailyRestFromDaily(day,0.0,92.0)!!,0.0)
    }
}
