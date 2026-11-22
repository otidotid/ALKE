package com.otidotid.alke.analytics

import com.otidotid.alke.data.DailyMetric

/**
 * The last *completed* seven-day Fitness Age window, inclusive through the
 * latest Saturday. This is also the date used by weekly labs/provenance.
 *
 * Explicitly excludes Sunday–Friday records from the preceding Saturday score.
 * The same filter/sort/takeLast(7) semantics as the prior Saturday-only path
 * are preserved; only the upper bound is now the actual weekly anchor.
 */
internal object WeeklyFitnessAgeWindow {
    fun select(history: List<DailyMetric>, oldestDay: String, weeklyDay: String): List<DailyMetric> =
        history.filter { it.day in oldestDay..weeklyDay }.sortedBy { it.day }.takeLast(7)
}
