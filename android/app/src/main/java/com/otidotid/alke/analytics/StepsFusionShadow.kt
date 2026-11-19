package com.otidotid.alke.analytics

import com.otidotid.alke.data.GravitySample
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Patch 3.1 shadow-only WHOOP 4.0 Steps Fusion core.
 *
 * WHOOP 4.0 gravity is too sparse to count individual footfalls. This engine therefore keeps the
 * existing production estimate untouched and evaluates a minute-bucket motion-volume model against
 * external teacher labels such as Zepp ACTIVITY_MINUTE. Missing teacher minutes remain UNKNOWN, never 0.
 * Nothing in this file writes DailyMetric.steps or changes the production StepsEstimateEngine.
 */
object StepsFusionShadow {
    const val MIN_LABELLED_MINUTES = 30
    const val MIN_ACTIVE_TEACHER_STEPS = 1
    const val MAX_TEACHER_STEPS_PER_MINUTE = 240
    const val MAX_ESTIMATED_STEPS_PER_MINUTE = 240

    data class TeacherMinute(val epochMinute: Long, val steps: Int)

    data class MotionMinute(
        val epochMinute: Long,
        val sampleCount: Int,
        val motion: Double,
    )

    enum class Confidence { INSUFFICIENT, LOW, MEDIUM, HIGH }

    data class Fit(
        val stepsPerMotionUnit: Double,
        val labelledMinutes: Int,
        val activeMinutes: Int,
        val coverageFraction: Double,
        val medianAbsoluteError: Double,
        val medianAbsolutePercentageError: Double?,
        val confidence: Confidence,
    )

    data class MinuteComparison(
        val epochMinute: Long,
        val teacherSteps: Int,
        val estimatedSteps: Int,
        val motion: Double,
    )

    data class ShadowReport(
        val fit: Fit?,
        val comparisons: List<MinuteComparison>,
        val totalTeacherSteps: Int,
        val totalEstimatedSteps: Int,
        val signedErrorSteps: Int,
        val absolutePercentageError: Double?,
    )

    /** Sum orientation-vector deltas within each minute. No delta crosses a minute boundary. */
    fun motionByMinute(samples: List<GravitySample>): List<MotionMinute> {
        if (samples.isEmpty()) return emptyList()
        return samples.asSequence()
            .filter { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
            .groupBy { Math.floorDiv(it.ts, 60L) }
            .toSortedMap()
            .map { (minute, rows) ->
                val sorted = rows.sortedBy { it.ts }
                var motion = 0.0
                for (i in 1 until sorted.size) {
                    val a = sorted[i - 1]
                    val b = sorted[i]
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val dz = b.z - a.z
                    motion += sqrt(dx * dx + dy * dy + dz * dz)
                }
                MotionMinute(minute, sorted.size, motion)
            }
    }

    /**
     * Robust through-origin fit. Each labelled active minute contributes steps / motion; the median
     * prevents one wrist-motion outlier from dragging the model. Invalid and impossible teacher rows
     * are excluded. Unlabelled minutes are not introduced by this join and therefore cannot become zero.
     */
    fun fit(motion: List<MotionMinute>, teacher: List<TeacherMinute>): Fit? {
        val motionByMinute = motion.associateBy { it.epochMinute }
        val validTeacher = teacher
            .filter { it.steps in 0..MAX_TEACHER_STEPS_PER_MINUTE }
            .distinctBy { it.epochMinute }
        val paired = validTeacher.mapNotNull { label ->
            motionByMinute[label.epochMinute]?.let { it to label }
        }
        val active = paired.filter { (m, t) ->
            t.steps >= MIN_ACTIVE_TEACHER_STEPS && m.motion > 0.0
        }
        if (paired.size < MIN_LABELLED_MINUTES || active.size < MIN_LABELLED_MINUTES) return null

        val ratios = active.map { (m, t) -> t.steps / m.motion }.sorted()
        val coefficient = median(ratios)
        if (!coefficient.isFinite() || coefficient <= 0.0) return null

        val errors = active.map { (m, t) ->
            abs(estimateMinute(m.motion, coefficient) - t.steps).toDouble()
        }
        val percentageErrors = active.mapNotNull { (m, t) ->
            if (t.steps <= 0) null else abs(estimateMinute(m.motion, coefficient) - t.steps).toDouble() / t.steps
        }
        val coverage = paired.size.toDouble() / validTeacher.size.coerceAtLeast(1).toDouble()
        val mape = percentageErrors.takeIf { it.isNotEmpty() }?.let(::median)
        val confidence = confidence(active.size, coverage, mape)
        return Fit(
            stepsPerMotionUnit = coefficient,
            labelledMinutes = paired.size,
            activeMinutes = active.size,
            coverageFraction = coverage.coerceIn(0.0, 1.0),
            medianAbsoluteError = median(errors),
            medianAbsolutePercentageError = mape,
            confidence = confidence,
        )
    }

    fun evaluate(
        motion: List<MotionMinute>,
        teacher: List<TeacherMinute>,
        fit: Fit,
    ): ShadowReport {
        val teacherByMinute = teacher.asSequence()
            .filter { it.steps in 0..MAX_TEACHER_STEPS_PER_MINUTE }
            .distinctBy { it.epochMinute }
            .associateBy { it.epochMinute }
        val comparisons = motion.mapNotNull { m ->
            val label = teacherByMinute[m.epochMinute] ?: return@mapNotNull null
            MinuteComparison(
                epochMinute = m.epochMinute,
                teacherSteps = label.steps,
                estimatedSteps = estimateMinute(m.motion, fit.stepsPerMotionUnit),
                motion = m.motion,
            )
        }
        val teacherTotal = comparisons.sumOf { it.teacherSteps }
        val estimateTotal = comparisons.sumOf { it.estimatedSteps }
        val signed = estimateTotal - teacherTotal
        val ape = if (teacherTotal > 0) abs(signed).toDouble() / teacherTotal else null
        return ShadowReport(fit, comparisons, teacherTotal, estimateTotal, signed, ape)
    }

    fun estimateMinute(motion: Double, coefficient: Double): Int {
        if (!motion.isFinite() || !coefficient.isFinite() || motion <= 0.0 || coefficient <= 0.0) return 0
        return (motion * coefficient).roundToInt().coerceIn(0, MAX_ESTIMATED_STEPS_PER_MINUTE)
    }

    private fun confidence(activeMinutes: Int, coverage: Double, mape: Double?): Confidence {
        if (activeMinutes < MIN_LABELLED_MINUTES || mape == null) return Confidence.INSUFFICIENT
        return when {
            activeMinutes >= 300 && coverage >= 0.80 && mape <= 0.15 -> Confidence.HIGH
            activeMinutes >= 120 && coverage >= 0.60 && mape <= 0.30 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
