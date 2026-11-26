package com.otidotid.alke.analytics

import com.otidotid.alke.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SupplementalSleepCreditTest {
    private fun session(s:Long,e:Long,json:String?,id:String="strap") =
        SleepSession(deviceId=id,startTs=s,endTs=e,stagesJSON=json)
    private fun staged(s:Long,e:Long,id:String="strap") =
        session(s,e,"""[{"start":$s,"end":$e,"stage":"light"}]""",id)

    @Test fun duplicateSegmentsCountOnlyOnce() {
        assertEquals(30.0,SupplementalSleepCredit.minutes(
            listOf(staged(0,1200),staged(600,1800,"other")),emptyList()),1e-8)
    }
    @Test fun mainSleepCannotBeCreditedAgain() {
        assertEquals(10.0,SupplementalSleepCredit.minutes(
            listOf(staged(0,1800)),listOf(SupplementalSleepCredit.Span(0,1200))),1e-8)
    }
    @Test fun awakeAndUnknownStagesDoNotEarnSleepCredit() {
        val row=session(0,1800,"""[{"start":0,"end":600,"stage":"awake"},{"start":600,"end":1200,"stage":"unknown"},{"start":1200,"end":1800,"stage":"rem"}]""")
        assertEquals(10.0,SupplementalSleepCredit.minutes(listOf(row),emptyList()),1e-8)
        assertEquals(0.0,SupplementalSleepCredit.minutes(listOf(session(0,1800,null)),emptyList()),1e-8)
    }
    @Test fun summaryCannotBeProratedAcrossOverlap() {
        val row=session(0,1800,"""{"light":15,"deep":5,"rem":0,"awake":10}""")
        assertEquals(20.0,SupplementalSleepCredit.minutes(listOf(row),emptyList()),1e-8)
        assertEquals(0.0,SupplementalSleepCredit.minutes(listOf(row),listOf(SupplementalSleepCredit.Span(0,300))),1e-8)
    }
    @Test fun segmentIsClippedToSessionBounds() {
        val row=session(600,1200,"""[{"start":0,"end":1800,"stage":"deep"}]""")
        assertEquals(10.0,SupplementalSleepCredit.minutes(listOf(row),emptyList()),1e-8)
    }
}
