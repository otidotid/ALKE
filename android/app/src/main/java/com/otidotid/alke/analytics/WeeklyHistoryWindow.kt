package com.otidotid.alke.analytics

import java.util.NavigableMap

/**
 * Get the exact same ascending, last-wins day slice as the legacy
 * `byDay.values.filter { it.day in from..to }.sortedBy { it.day }`, without
 * scanning and sorting the entire historical map for every weekly anchor.
 *
 * Callers construct a TreeMap from history.associateBy { it.day }, preserving
 * the original duplicate-day winner and lexicographic ISO date ordering.
 */
internal object WeeklyHistoryWindow {
    fun <T> rows(byDay: NavigableMap<String, T>, from: String, through: String): List<T> =
        if (from > through) emptyList() else byDay.subMap(from, true, through, true).values.toList()
}
