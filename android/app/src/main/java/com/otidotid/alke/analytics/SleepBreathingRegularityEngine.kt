package com.otidotid.alke.analytics

import com.otidotid.alke.data.RespSample
import com.otidotid.alke.data.RrInterval
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * WHOOP 4-oriented wellness estimate. Prefer actual direct respiration ADC. Fill missing
 * windows with independently quality-gated RR/RSA estimates; lastly show a daily respiratory
 * aggregate with LOW trust and no within-night pattern classification.
 *
 * This is NOT hypopnea/AHI/ODI, airflow measurement, or medical risk scoring. Estimated
 * SpO2 (PersonalOxygenEngine) and uncalibrated red/IR ADC are deliberately excluded.
 * All results are derived on demand without changing scoring, Room or retention.
 */
internal object SleepBreathingRegularityEngine {
    const val WINDOW_SECONDS = 300L
    const val MIN_SLEEP_MINUTES = 240.0
    const val MIN_WINDOW_SAMPLES = 240
    const val MIN_VALID_FRACTION = 0.60
    const val MAX_GAP_SECONDS = 5L
    const val STEADY_MAD_PERCENT = 15.0 // experimental display-only split, NOT medical
    private const val HIGH_VARIATION_PERCENT = 25.0 // experimental display-only split

    enum class State { RELATIVELY_STEADY, MORE_VARIABLE, INSUFFICIENT_DATA }
    enum class Reason { NONE, SLEEP_TOO_SHORT, NO_DIRECT_RESPIRATION, LOW_COVERAGE, UNUSABLE_SIGNAL }
    enum class Trust { HIGH, MEDIUM, LOW, NONE }
    enum class Source { DIRECT_RESPIRATION, MIXED_DIRECT_AND_RR, RR_ESTIMATE, DAILY_AGGREGATE, NONE }
    enum class Pattern { LOW_VARIATION, MODERATE_VARIATION, HIGH_VARIATION, NOT_ASSESSABLE }

    data class Window(val offsetMinutes: Int, val rateBpm: Double, val derivedFromRr: Boolean = false)

    data class Report(
        val state: State,
        val reason: Reason,
        val medianBpm: Double?,
        val variabilityPercent: Double?,
        val coveragePercent: Int,
        val measuredMinutes: Int,
        val windows: List<Window>,
        val trust: Trust = Trust.NONE,
        val source: Source = Source.NONE,
        val pattern: Pattern = Pattern.NOT_ASSESSABLE,
        val directCoveragePercent: Int = 0,
        val rrCoveragePercent: Int = 0,
        val hrCoveragePercent: Int? = null,
        val movementCoveragePercent: Int? = null,
    ) {
        // Numeric evidence can be partial even when the interval coverage is insufficient
        // to classify the nightly pattern. Never invent a zero or a normal classification.
        val hasEvidence: Boolean get() = medianBpm?.isFinite() == true
        val approximate: Boolean get() = trust != Trust.HIGH
    }

    /**
     * from/to are the selected night's clock window. Daily respiratory aggregates are only
     * fallback display values; they cannot establish event counts or within-night variability.
     * HR and motion coverage are quality/context indicators, not substitute respiration signals.
     */
    fun assess(
        startTs: Long,
        endTs: Long,
        asleepMinutes: Double?,
        samples: List<RespSample>,
        rr: List<RrInterval> = emptyList(),
        dailyRateBpm: Double? = null,
        hrCoveragePercent: Int? = null,
        movementCoveragePercent: Int? = null,
    ): Report {
        val length = endTs - startTs
        val validDuration = length in WINDOW_SECONDS..(16L * 3600L) &&
            asleepMinutes != null && asleepMinutes.isFinite() && asleepMinutes >= MIN_SLEEP_MINUTES
        val dailyFallback = dailyRateBpm?.takeIf { it.isFinite() && it in 8.0..25.0 }
        if (!validDuration) {
            return fallback(
                Reason.SLEEP_TOO_SHORT, dailyFallback, hrCoveragePercent, movementCoveragePercent,
            )
        }
        val count = (length / WINDOW_SECONDS).toInt()
        if (count <= 0) {
            return fallback(
                Reason.SLEEP_TOO_SHORT, dailyFallback, hrCoveragePercent, movementCoveragePercent,
            )
        }

        val direct = arrayOfNulls<Window>(count)
        if (samples.isNotEmpty()) {
            val buckets = Array(count) { ArrayList<RespSample>(300) }
            for (sample in samples) {
                if (sample.ts < startTs || sample.ts >= startTs + count * WINDOW_SECONDS) continue
                buckets[((sample.ts - startTs) / WINDOW_SECONDS).toInt()].add(sample)
            }
            for ((index, bucket) in buckets.withIndex()) {
                if (bucket.size < MIN_WINDOW_SAMPLES) continue
                val ordered = bucket.sortedBy { it.ts }.distinctBy { it.ts }
                if (ordered.size < MIN_WINDOW_SAMPLES ||
                    ordered.last().ts - ordered.first().ts < MIN_WINDOW_SAMPLES - 1 ||
                    ordered.zipWithNext().any { (a, b) -> b.ts - a.ts > MAX_GAP_SECONDS }
                ) continue
                val (rate, rrv) = SleepStager.respRateAndRRV(ordered.map { it.raw.toDouble() })
                if (rate.isFinite() && rate in 8.0..25.0 && rrv.isFinite() && rrv in 0.0..4.0) {
                    direct[index] = Window(index * 5, rate)
                }
            }
        }

        val directCount = direct.count { it != null }
        // Only pay for RR/RSA reconstruction in gaps the direct sensor did not cover.
        // The repository's rrIntervalsUnion already chooses an eligible WHOOP 4 source and
        // de-duplicates overlapping transport copies. Its estimator rejects missing beats.
        val rrRate = arrayOfNulls<Window>(count)
        if (rr.isNotEmpty()) {
            for (index in 0 until count) {
                if (direct[index] != null) continue
                val from = startTs + index * WINDOW_SECONDS
                val until = from + WINDOW_SECONDS
                val beatRows = rr.filter { it.ts >= from && it.ts < until }
                if (beatRows.size < 120) continue // 2.5 minutes at >=48 beats/min
                val rate = SleepStager.respRateFromRR(beatRows, from, until - 1)
                if (rate.isFinite() && rate in 8.0..25.0) {
                    rrRate[index] = Window(index * 5, rate, derivedFromRr = true)
                }
            }
        }
        val rrCount = rrRate.count { it != null }
        val measurements = (0 until count).mapNotNull { direct[it] ?: rrRate[it] }
        val directPct = (100.0 * directCount / count).roundToInt().coerceIn(0, 100)
        val rrPct = (100.0 * rrCount / count).roundToInt().coerceIn(0, 100)
        val totalPct = (100.0 * measurements.size / count).roundToInt().coerceIn(0, 100)

        if (measurements.isEmpty()) {
            val reason = if (samples.isEmpty() && rr.isEmpty()) Reason.NO_DIRECT_RESPIRATION
            else Reason.UNUSABLE_SIGNAL
            return fallback(reason, dailyFallback, hrCoveragePercent, movementCoveragePercent)
        }

        val median = median(measurements.map { it.rateBpm })
        val enoughForPattern = measurements.size >= 6 && totalPct >= 30
        val variation = if (enoughForPattern) {
            median(measurements.map { abs(it.rateBpm - median) }) / median * 100.0
        } else null
        val pattern = when {
            variation == null -> Pattern.NOT_ASSESSABLE
            variation <= STEADY_MAD_PERCENT -> Pattern.LOW_VARIATION
            variation <= HIGH_VARIATION_PERCENT -> Pattern.MODERATE_VARIATION
            else -> Pattern.HIGH_VARIATION
        }
        val trust = when {
            directPct >= 70 && rrCount == 0 && measurements.size >= 24 -> Trust.HIGH
            directCount > 0 && totalPct >= 50 && measurements.size >= 12 -> Trust.MEDIUM
            rrCount > 0 && totalPct >= 50 && measurements.size >= 12 -> Trust.MEDIUM
            else -> Trust.LOW
        }
        val source = when {
            directCount > 0 && rrCount > 0 -> Source.MIXED_DIRECT_AND_RR
            directCount > 0 -> Source.DIRECT_RESPIRATION
            else -> Source.RR_ESTIMATE
        }
        val state = when {
            variation == null || totalPct < MIN_VALID_FRACTION -> State.INSUFFICIENT_DATA
            pattern == Pattern.LOW_VARIATION -> State.RELATIVELY_STEADY
            else -> State.MORE_VARIABLE
        }
        return Report(
            state = state,
            reason = if (state == State.INSUFFICIENT_DATA) Reason.LOW_COVERAGE else Reason.NONE,
            medianBpm = median,
            variabilityPercent = variation,
            coveragePercent = totalPct,
            measuredMinutes = measurements.size * 5,
            windows = measurements,
            trust = trust,
            source = source,
            pattern = pattern,
            directCoveragePercent = directPct,
            rrCoveragePercent = rrPct,
            hrCoveragePercent = hrCoveragePercent?.coerceIn(0, 100),
            movementCoveragePercent = movementCoveragePercent?.coerceIn(0, 100),
        )
    }

    private fun fallback(
        reason: Reason,
        dailyRateBpm: Double?,
        hrCoveragePercent: Int?,
        movementCoveragePercent: Int?,
    ): Report = Report(
        state = State.INSUFFICIENT_DATA,
        reason = reason,
        medianBpm = dailyRateBpm,
        variabilityPercent = null,
        coveragePercent = 0,
        measuredMinutes = 0,
        windows = emptyList(),
        trust = if (dailyRateBpm == null) Trust.NONE else Trust.LOW,
        source = if (dailyRateBpm == null) Source.NONE else Source.DAILY_AGGREGATE,
        hrCoveragePercent = hrCoveragePercent,
        movementCoveragePercent = movementCoveragePercent,
    )

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
}
