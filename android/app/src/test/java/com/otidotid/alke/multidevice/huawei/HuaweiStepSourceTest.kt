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
