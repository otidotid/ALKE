package com.otidotid.alke.analytics

import com.otidotid.alke.data.HrSample
import com.otidotid.alke.data.WorkoutRow
import kotlin.math.roundToInt

/**
 * Reconciles an imported Health Connect workout against the ALKE strap HR trace.
 *
 * Imported Health Connect workout heart rate is retained when the ALKE strap has no
 * independent HR samples; when strap samples exist, those remain authoritative.
 * Workout calories and Effort are still locally computed from strap evidence only.
 *
 * Workout calories are an interval breakdown only. They are never added to DailyMetric active
 * calories, whose whole-day raw-HR pipeline already includes the same interval exactly once.
 */
object HealthConnectWorkoutReconciler {
    const val sourceId = "health-connect"

    fun reconcile(
        row: WorkoutRow,
        samples: List<HrSample>,
        profile: UserProfile,
        maxHr: Double,
        restingHr: Double?,
        effortMethod: StrainScorer.Method,
        sex: String,
    ): WorkoutRow {
        if (!row.source.equals(sourceId, ignoreCase = true)) return row

        val window = samples
            .asSequence()
            .filter { it.ts in row.startTs..row.endTs && it.bpm > 0 }
            .sortedBy { it.ts }
            .toList()

        if (window.size < 2) {
            // Retain real imported Health Connect HR for pace-based fitness estimation
            // and the exercise detail. Do not invent Effort or trust external calories.
            return row.copy(energyKcal = null, strain = null)
        }

        val kcal = runCatching {
            Calories.estimateBoutCalories(
                hrSamples = window,
                profile = profile,
                hrmax = maxHr,
                restingHR = restingHr,
            ).first
        }.getOrNull()?.takeIf { it.isFinite() && it > 0.0 }

        val effort = runCatching {
            StrainScorer.strain(
                hr = window,
                maxHR = maxHr,
                restingHR = restingHr ?: StrainScorer.defaultRestingHR,
                method = effortMethod,
                sex = sex,
            )
        }.getOrNull()

        return row.copy(
            avgHr = window.map { it.bpm }.average().roundToInt(),
            maxHr = window.maxOf { it.bpm },
            energyKcal = kcal,
            strain = effort,
        )
    }
}