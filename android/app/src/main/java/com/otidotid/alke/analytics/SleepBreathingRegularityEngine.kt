package com.otidotid.alke.analytics

import com.otidotid.alke.data.RespSample
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * EXPERIMENTAL wellness-only description of directly recorded overnight respiratory rhythm.
 *
 * Does NOT detect apnea/hypopnea, measure airflow, estimate AHI/ODI, assess clinical risk, or
 * substitute for a sleep study. In particular, never feed PersonalOxygenEngine's daily estimated
 * SpO2 into this algorithm. WHOOP 5/MG may have NO direct respiration channel; return insufficient.
 *
 * Read-only: never writes a Room row or changes SleepStager, Rest, Fitness Age or history retention.
 */
internal object SleepBreathingRegularityEngine {
    const val WINDOW_SECONDS = 300L
    const val MIN_SLEEP_MINUTES = 240.0
    const val MIN_WINDOW_SAMPLES = 240
    const val MIN_VALID_FRACTION = 0.60
    const val MAX_GAP_SECONDS = 5L
    const val STEADY_MAD_PERCENT = 15.0 // exploratory descriptive split, NOT a clinical threshold

    enum class State { RELATIVELY_STEADY, MORE_VARIABLE, INSUFFICIENT_DATA }
    enum class Reason { NONE, SLEEP_TOO_SHORT, NO_DIRECT_RESPIRATION, LOW_COVERAGE, UNUSABLE_SIGNAL }

    data class Window(val offsetMinutes: Int, val rateBpm: Double)

    data class Report(
        val state: State,
        val reason: Reason,
        val medianBpm: Double?,
        val variabilityPercent: Double?,
        val coveragePercent: Int,
        val measuredMinutes: Int,
        val windows: List<Window>,
    ) {
        val hasEvidence: Boolean get() = state != State.INSUFFICIENT_DATA
    }

    private fun insufficient(reason: Reason, coverage: Int = 0, windows: List<Window> = emptyList()) =
        Report(State.INSUFFICIENT_DATA, reason, null, null, coverage, windows.size * 5, windows)

    /**
     * Assesses up to a 16-hour selected-night window using ONLY actual timestamped 1 Hz raw
     * respiration ADC samples. The asleepMinutes argument must come from recorded sleep,
     * not elapsed bed time. Missing sleep duration is not treated as a full night's sleep.
     */
    fun assess(
        startTs: Long,
        endTs: Long,
        asleepMinutes: Double?,
        samples: List<RespSample>,
    ): Report {
        if (endTs <= startTs || endTs - startTs > 16L * 3600L ||
            asleepMinutes == null || !asleepMinutes.isFinite() || asleepMinutes < MIN_SLEEP_MINUTES
        ) return insufficient(Reason.SLEEP_TOO_SHORT)

        val windowCount = ((endTs - startTs) / WINDOW_SECONDS).toInt()
        if (windowCount <= 0) return insufficient(Reason.SLEEP_TOO_SHORT)
        if (samples.isEmpty()) return insufficient(Reason.NO_DIRECT_RESPIRATION)

        val buckets = Array(windowCount) { ArrayList<RespSample>(300) }
        for (sample in samples) {
            if (sample.ts < startTs || sample.ts >= startTs + windowCount * WINDOW_SECONDS) continue
            val idx = ((sample.ts - startTs) / WINDOW_SECONDS).toInt()
            if (idx in buckets.indices) buckets[idx].add(sample)
        }

        val measurements = ArrayList<Window>(windowCount)
        for ((idx, bucket) in buckets.withIndex()) {
            if (bucket.size < MIN_WINDOW_SAMPLES) continue
            val ordered = bucket.sortedBy { it.ts }.distinctBy { it.ts }
            if (ordered.size < MIN_WINDOW_SAMPLES ||
                ordered.last().ts - ordered.first().ts < MIN_WINDOW_SAMPLES - 1 ||
                ordered.zipWithNext().any { (a, b) -> b.ts - a.ts > MAX_GAP_SECONDS }
            ) continue
            val raw = ordered.map { it.raw.toDouble() }
            // Reuse the existing signal extractor from ALKE's SleepStager.
            val (rate, rrv) = SleepStager.respRateAndRRV(raw)
            if (rate.isFinite() && rate in 8.0..25.0 && rrv.isFinite() && rrv in 0.0..4.0) {
                measurements += Window(idx * 5, rate)
            }
        }

        val coverage = (100.0 * measurements.size / windowCount).roundToInt().coerceIn(0, 100)
        if (measurements.isEmpty()) return insufficient(Reason.UNUSABLE_SIGNAL)
        if (measurements.size.toDouble() / windowCount < MIN_VALID_FRACTION ||
            measurements.size < 24
        ) return insufficient(Reason.LOW_COVERAGE, coverage, measurements)

        val median = median(measurements.map { it.rateBpm })
        val mad = median(measurements.map { abs(it.rateBpm - median) })
        val variation = (mad / median * 100.0).coerceIn(0.0, 100.0)
        val status = if (variation <= STEADY_MAD_PERCENT) State.RELATIVELY_STEADY else State.MORE_VARIABLE
        return Report(status, Reason.NONE, median, variation, coverage, measurements.size * 5, measurements)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
}
