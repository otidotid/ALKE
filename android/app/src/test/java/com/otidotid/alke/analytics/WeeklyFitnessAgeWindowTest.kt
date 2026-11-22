package com.otidotid.alke.analytics

import com.otidotid.alke.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class WeeklyFitnessAgeWindowTest {
    @Test fun sundayAndMondayRowsNeverContaminatePriorSaturdaysFitnessAge() {
        val saturday = LocalDate.of(2026, 11, 21)
        val days = (-10..2).map { offset ->
            DailyMetric(deviceId = "test-strap", day = saturday.plusDays(offset.toLong()).toString())
        }.reversed()
        val window = WeeklyFitnessAgeWindow.select(days, "2026-11-01", saturday.toString())
        assertEquals((6 downTo 0).map { saturday.minusDays(it.toLong()).toString() },
            window.map { it.day })
        assertFalse(window.any { it.day > saturday.toString() })
    }

    @Test fun midnightAndSaturdayWindowPreservePriorSevenDaySemantics() {
        val saturday = LocalDate.of(2026, 11, 21)
        val days = (0..6).map {
            DailyMetric(deviceId = "test-strap", day = saturday.minusDays((6-it).toLong()).toString())
        }
        val expected = days.filter { it.day in "2026-11-01".."2026-11-21" }
            .sortedBy { it.day }.takeLast(7)
        assertEquals(expected, WeeklyFitnessAgeWindow.select(days, "2026-11-01", saturday.toString()))
    }
}
