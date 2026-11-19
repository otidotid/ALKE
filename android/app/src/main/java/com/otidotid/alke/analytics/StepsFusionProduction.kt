package com.otidotid.alke.analytics

import kotlin.math.abs
import kotlin.math.min

/**
 * Production WHOOP 4.0 automatic steps calibration.
 *
 * Learns from days where ALKE has both WHOOP wrist-motion volume and a recorded reference step total
 * (Health Connect / phone / historical Zepp-derived data). No manual coefficient is used. The fit is
 * robust to wrist-motion outliers: it rejects implausible rows, trims ratio outliers with median absolute
 * deviation, then blends a motion-weighted median with a bounded through-origin least-squares estimate.
 * Missing reference days remain unknown and do not become zero.
 */
object StepsFusionProduction {
    private const val MIN_REFERENCE_STEPS = 100.0
    private const val MAX_REFERENCE_STEPS = 60_000.0
    private const val MAD_MULTIPLIER = 3.5
    private const val MEDIAN_WEIGHT = 0.80
    private const val LEAST_SQUARES_WEIGHT = 0.20

    data class Diagnostics(
        val rawDays: Int,
        val acceptedDays: Int,
        val rejectedDays: Int,
        val coefficient: Double,
        val relativeMad: Double,
        val confidence: Double,
    )

    data class Result(
        val calibration: StepsEstimateEngine.Calibration,
        val diagnostics: Diagnostics,
    )

    fun calibrate(points: List<StepsEstimateEngine.CalibrationPoint>): StepsEstimateEngine.Calibration? =
        fit(points)?.calibration

    fun fit(points: List<StepsEstimateEngine.CalibrationPoint>): Result? {
        val usable = points.filter {
            it.motion >= StepsEstimateEngine.MIN_MOTION_FOR_FIT &&
                it.motion.isFinite() &&
                it.steps.isFinite() &&
                it.steps in MIN_REFERENCE_STEPS..MAX_REFERENCE_STEPS
        }
        if (usable.size < StepsEstimateEngine.MIN_CALIBRATION_DAYS) return null

        val ratios = usable.map { it.steps / it.motion }
        val center = StepsEstimateEngine.median(ratios)
        if (!center.isFinite() || center <= 0.0) return null

        val absoluteDeviations = ratios.map { abs(it - center) }
        val mad = StepsEstimateEngine.median(absoluteDeviations)
        val tolerance = if (mad > 0.0) MAD_MULTIPLIER * mad else center * 0.20
        val accepted = usable.filter { abs(it.steps / it.motion - center) <= tolerance }
        if (accepted.size < StepsEstimateEngine.MIN_CALIBRATION_DAYS) return null

        val acceptedRatios = accepted.map { it.steps / it.motion }
        val motionWeights = accepted.map { it.motion }
        val robustMedian = StepsEstimateEngine.weightedMedian(acceptedRatios, motionWeights)

        val denominator = accepted.sumOf { it.motion * it.motion }
        val leastSquares = if (denominator > 0.0) {
            accepted.sumOf { it.motion * it.steps } / denominator
        } else {
            robustMedian
        }
        val boundedLeastSquares = leastSquares.coerceIn(robustMedian * 0.70, robustMedian * 1.30)
        val coefficient = MEDIAN_WEIGHT * robustMedian + LEAST_SQUARES_WEIGHT * boundedLeastSquares
        if (!coefficient.isFinite() || coefficient <= 0.0) return null

        val acceptedMad = StepsEstimateEngine.weightedMedian(
            acceptedRatios.map { abs(it - coefficient) },
            motionWeights,
        )
        val relativeMad = (acceptedMad / coefficient).coerceAtLeast(0.0)
        val sizeTerm = min(
            1.0,
            accepted.size.toDouble() / StepsEstimateEngine.GOOD_CALIBRATION_DAYS.toDouble(),
        )
        val retention = accepted.size.toDouble() / usable.size.toDouble()
        val tightness = (1.0 - relativeMad).coerceIn(0.0, 1.0)
        val confidence = (0.45 * sizeTerm + 0.35 * tightness + 0.20 * retention)
            .coerceIn(0.0, 1.0)

        val calibration = StepsEstimateEngine.Calibration(
            coefficient = coefficient,
            sampleDays = accepted.size,
            confidence = confidence,
            manual = false,
        )
        return Result(
            calibration = calibration,
            diagnostics = Diagnostics(
                rawDays = points.size,
                acceptedDays = accepted.size,
                rejectedDays = points.size - accepted.size,
                coefficient = coefficient,
                relativeMad = relativeMad,
                confidence = confidence,
            ),
        )
    }
}
