package com.otidotid.alke.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.TreeMap

class WeeklyHistoryWindowTest {
    @Test fun matchesLegacyWeeklyFilterForLongSparseHistoryAndDuplicates() {
        val origin = LocalDate.of(2025, 1, 1)
        val history = (0 until 730).filter { it % 7 != 2 }.map { idx ->
            origin.plusDays(idx.toLong()).toString() to "row-$idx"
        }.toMutableList()
        val duplicateDay = origin.plusDays(60).toString()
        history.add(duplicateDay to "latest-wins")
        val byDay = TreeMap(history.associate { it.first to it.second })
        for (week in 0..110) {
            val start = origin.plusDays(week.toLong() * 7).toString()
            val end = origin.plusDays(week.toLong() * 7 + 6).toString()
            val old = byDay.entries.filter { it.key in start..end }.sortedBy { it.key }.map { it.value }
            assertEquals("window $week", old, WeeklyHistoryWindow.rows(byDay, start, end))
        }
        assertEquals("latest-wins",
            WeeklyHistoryWindow.rows(byDay, duplicateDay, duplicateDay).single())
    }

    @Test fun emptyAndReversedWindowsRemainEmpty() {
        val indexed = TreeMap(mapOf("2026-11-21" to 5))
        assertEquals(emptyList<Int>(), WeeklyHistoryWindow.rows(indexed, "2026-11-01", "2026-11-20"))
        assertEquals(emptyList<Int>(), WeeklyHistoryWindow.rows(indexed, "2026-11-22", "2026-11-20"))
    }
}
