package com.otidotid.alke.multidevice.huawei

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HuaweiStepSourceTest {
    private fun minute(t: Long, steps: Int?) = HuaweiFitnessRead.Minute(t,steps,null,null)

    @Test fun missingMinuteDoesNotDisplaceWholeWindowFallback() {
        val c = HuaweiStepSource.coverage(listOf(minute(0,10),minute(60,null),minute(120,12)),0,180)
        assertFalse(c.complete)
        assertEquals(2,c.observedMinutes)
        assertEquals(22L,c.steps)
    }

    @Test fun explicitZeroIsObservedAndDuplicateIsNotSummed() {
        val c = HuaweiStepSource.coverage(listOf(minute(0,0),minute(60,12),minute(60,12)),0,120)
        assertTrue(c.complete)
        assertEquals(12L,c.steps)
    }

    @Test fun conflictsAndMisalignedWindowsFailCoverage() {
        assertFalse(HuaweiStepSource.coverage(listOf(minute(0,10),minute(0,11)),0,60).complete)
        assertFalse(HuaweiStepSource.coverage(listOf(minute(0,10)),1,61).complete)
    }

    @Test fun partialFusionDoesNotDoubleCountAndLeavesUncoveredMinutesUnknown() {
        val h = listOf(minute(0,0),minute(60,12),minute(60,12),minute(120,null))
        val w = listOf(HuaweiStepSource.MinuteCount(0,20),HuaweiStepSource.MinuteCount(60,25),
            HuaweiStepSource.MinuteCount(120,7))
        val complete = HuaweiStepSource.fuseMinutes(h,w,0,180)
        assertEquals(HuaweiStepSource.Fusion(2,1,0,19),complete)
        assertEquals(19L,complete.completeSteps)
        val incomplete = HuaweiStepSource.fuseMinutes(h,w,0,240)
        assertEquals(1,incomplete.missingMinutes)
        assertNull(incomplete.completeSteps)
    }

    @Test fun conflictingNativeMinuteUsesOnlyIndependentValidFallback() {
        val h = listOf(minute(0,1),minute(0,2))
        assertEquals(3L,HuaweiStepSource.fuseMinutes(h,listOf(
            HuaweiStepSource.MinuteCount(0,3)),0,60).completeSteps)
        assertNull(HuaweiStepSource.fuseMinutes(h,emptyList(),0,60).completeSteps)
    }

    @Test fun calibratedMotionOnlyFillsMissingHuaweiMinutes() {
        fun g(t: Long,x: Double, id: String="whoop") =
            com.otidotid.alke.data.GravitySample(id,t,x,0.0,0.0)
        val h = listOf(minute(0,10),minute(120,0))
        val raw = listOf(g(0,0.0),g(30,9.0),g(60,0.0),g(90,0.7),g(120,10.0),g(150,20.0))
        val result = requireNotNull(HuaweiStepSource.fuseMotion(h,raw,0,180,10.0))
        assertEquals(17L,result.steps)
        assertEquals(2,result.huaweiMinutes)
        assertEquals(1,result.fallbackMinutes)
        assertEquals(0,result.missingMinutes)
        assertEquals(result,HuaweiStepSource.fuseMotion(h+h,raw+raw,0,180,10.0))
    }

    @Test fun missingCalibrationOrCrossDeviceOrLongGapDoesNotInventSteps() {
        fun g(t: Long,x: Double,id: String="whoop") =
            com.otidotid.alke.data.GravitySample(id,t,x,0.0,0.0)
        val h = listOf(minute(0,10))
        val raw = listOf(g(60,0.0),g(90,10.0,"other"),g(240,20.0,"other"))
        assertEquals(10L,HuaweiStepSource.fuseMotion(h,raw,0,300,10.0)?.steps)
        assertEquals(4,HuaweiStepSource.fuseMotion(h,raw,0,300,null)?.missingMinutes)
        assertNull(HuaweiStepSource.fuseMotion(emptyList(),raw,0,300,10.0))
    }

    @Test fun completeCalendarDayOnlyAndDstIsHandled() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026,3,8)
        val start = date.atStartOfDay(zone).toEpochSecond()
        val end = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val rows = (start until end step 60).map { minute(it,1) }
        assertEquals(mapOf(date.toString() to 1380L),HuaweiStepSource.completeDays(rows,zone,end))
        assertTrue(HuaweiStepSource.completeDays(rows.dropLast(1),zone,end).isEmpty())
        assertTrue(HuaweiStepSource.completeDays(rows,zone,end-1).isEmpty())
    }
}
