package com.otidotid.alke.analytics

import com.otidotid.alke.data.MetricSeriesRow

/**
 * Publishes production step estimates for every day that has WHOOP motion.
 *
 * Important: publication is intentionally independent of DailyMetric. A current day can have live
 * GravitySample motion hours before sleep/recovery creates its DailyMetric row. Requiring DailyMetric
 * caused yesterday's estimate to freeze at the last analytics snapshot and left a new day blank.
 */
object StepsProductionPublisher {
    fun rows(
        computedDeviceId: String,
        motionByDay: Map<String, Double>,
        referenceStepsByDay: Map<String, Double>,
        calibration: StepsEstimateEngine.Calibration,
    ): List<MetricSeriesRow> = motionByDay.flatMap { (day, motion) ->
        if (referenceStepsByDay.containsKey(day)) return@flatMap emptyList()
        val estimate = StepsEstimateEngine.estimate(motion, calibration) ?: return@flatMap emptyList()
        listOf(
            MetricSeriesRow(computedDeviceId, day, "steps_ai_raw", estimate.toDouble()),
            // Local-first final. AI may replace this row only after a generation check.
            MetricSeriesRow(computedDeviceId, day, "steps_est", estimate.toDouble()),
        )
    }
}
