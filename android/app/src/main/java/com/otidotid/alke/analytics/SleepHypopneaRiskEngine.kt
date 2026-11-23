package com.otidotid.alke.analytics

import com.otidotid.alke.data.GravitySample
import com.otidotid.alke.data.HrBucket
import com.otidotid.alke.data.RespSample
import com.otidotid.alke.data.RrInterval
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Exploratory screening for cyclic pulse-interval patterns during a FINISHED sleep window.
 *
 * Scientific basis: Hayano et al., Circulation: Arrhythmia and Electrophysiology (2011);
 * cyclic variation of heart rate (CVHR) / ACAT. This is a *simplified* autocorrelated
 * dip detector for wrist PPG-derived R-R or HR, not the published ECG ACAT implementation,
 * and has NOT been clinically validated for ALKE sensors. Never call this AHI/ODI,
 * a count of hypopneas, or a diagnosis.
 *
 * The strap respiration stream is an uncalibrated ADC field, NOT airflow/SpO2: its
 * availability can support provenance but its raw magnitude is never interpreted as
 * a breathing pause. Motion suppresses obvious artifacts. Missing signals stay missing.
 * No cloud, network, score mutation or database schema change.
 */
internal object SleepHypopneaRiskEngine {
    enum class Risk { WAITING, UNAVAILABLE, LOW, MEDIUM, HIGH }

    data class Assessment(
        val risk: Risk,
        val cvhrPatternsPerHour: Double? = null,
        val validMinutes: Int = 0,
        val usedRr: Boolean = false,
    ) {
        val isReady: Boolean get() = risk == Risk.LOW || risk == Risk.MEDIUM || risk == Risk.HIGH
    }

    private const val BIN_SECONDS = 5L
    private const val MIN_SLEEP_SECONDS = 3L * 3600L
    private const val MAX_SLEEP_SECONDS = 16L * 3600L
    private const val SETTLE_AFTER_WAKE_SECONDS = 10L * 60L
    private const val MIN_OBSERVED_SECONDS = 2L * 3600L
    private const val MIN_RR_COVERAGE = 0.60
    private const val MIN_HR_COVERAGE = 0.80

    /** Requires final sleep bounds, adequate wearable samples and no active sleep. */
    fun assess(
        startTs: Long,
        endTs: Long,
        nowTs: Long,
        rr: List<RrInterval>,
        hr: List<HrBucket>,
        respiration: List<RespSample> = emptyList(),
        motion: List<GravitySample> = emptyList(),
    ): Assessment {
        if (endTs > nowTs - SETTLE_AFTER_WAKE_SECONDS) return Assessment(Risk.WAITING)
        val duration = endTs - startTs
        if (duration !in MIN_SLEEP_SECONDS..MAX_SLEEP_SECONDS) return Assessment(Risk.UNAVAILABLE)
        val bins = ((duration + BIN_SECONDS - 1L) / BIN_SECONDS).toInt()
        val rrBins = rrIntervalsInBins(startTs, endTs, rr)
        val hrBins = heartRateInBins(startTs, endTs, hr)
        val rrFraction = rrBins.size.toDouble() / bins
        val hrFraction = hrBins.size.toDouble() / bins
        val useRr = rrFraction >= MIN_RR_COVERAGE
        // A pulse-derived fallback is allowed only at HIGHER coverage and with a second
        // wear/respiration signal. It cannot be described as Hayano ECG-ACAT.
        val rawRespPresent = respiration.any { it.ts in startTs until endTs }
        val motionPresent = motion.any { it.ts in startTs until endTs }
        if (!useRr && !(hrFraction >= MIN_HR_COVERAGE && (rawRespPresent || motionPresent))) {
            return Assessment(Risk.UNAVAILABLE)
        }
        val selected = if (useRr) rrBins else hrBins
        val observed = selected.size * BIN_SECONDS
        if (observed < MIN_OBSERVED_SECONDS || observed.toDouble() / duration < MIN_RR_COVERAGE) {
            return Assessment(Risk.UNAVAILABLE)
        }

        val smooth = smoothBins(bins, selected)
        val motionSecond = motion.asSequence()
            .filter { it.ts in startTs until endTs && (it.dynAccel ?: 0.0) >= 0.30 }
            .map { it.ts }
            .toHashSet()
        val candidates = dips(smooth, startTs, motionSecond)
        val accepted = autocorrelatedGroups(candidates)
        // CVHR events / *observed* time, not hypopnea events / sleep hour.
        val cvhrPerHour = accepted.size * 3600.0 / observed
        val risk = when {
            cvhrPerHour >= 15.0 -> Risk.HIGH
            cvhrPerHour >= 5.0 -> Risk.MEDIUM
            else -> Risk.LOW
        }
        return Assessment(risk, cvhrPerHour, (observed / 60L).toInt(), useRr)
    }

    private fun rrIntervalsInBins(
        from: Long, to: Long, rr: List<RrInterval>,
    ): Map<Int, Double> {
        val values = HashMap<Int, MutableList<Int>>()
        for (beat in rr) {
            if (beat.ts !in from until to || beat.tsSuspect == 1 || beat.rrMs !in 350..1500) continue
            val key = ((beat.ts - from) / BIN_SECONDS).toInt()
            val bucket = values.getOrPut(key) { ArrayList(8) }
            if (bucket.size < 16) bucket.add(beat.rrMs)
        }
        return values.mapNotNull { (key, samples) ->
            // At least three genuine beats in five seconds. Single outliers cannot define a dip.
            if (samples.size < 3) null
            else key to samples.sorted()[samples.size / 2].toDouble()
        }.toMap()
    }

    private fun heartRateInBins(
        from: Long, to: Long, hr: List<HrBucket>,
    ): Map<Int, Double> = hr.asSequence()
        .filter { it.bucket in from until to && it.avgBpm in 40.0..140.0 &&
            it.minBpm in 35.0..150.0 && it.maxBpm in 35.0..160.0 &&
            (it.maxBpm - it.minBpm) <= 35.0 }
        // Convert bpm to beat interval in ms; no interpolation across missing buckets.
        .associate { ((it.bucket - from) / BIN_SECONDS).toInt() to (60_000.0 / it.avgBpm) }

    private fun smoothBins(total: Int, input: Map<Int, Double>): DoubleArray {
        val out = DoubleArray(total) { Double.NaN }
        // Three-bin triangular smoothing, preserving gaps instead of filling missing samples.
        for (i in 1 until total - 1) {
            val a = input[i - 1] ?: continue
            val b = input[i] ?: continue
            val c = input[i + 1] ?: continue
            out[i] = (a + 2.0 * b + c) / 4.0
        }
        return out
    }

    private data class Dip(val bin: Int, val depth: Double, val shape: DoubleArray)

    private fun dips(
        rrMs: DoubleArray,
        startTs: Long,
        motionSeconds: Set<Long>,
    ): List<Dip> {
        val found = ArrayList<Dip>()
        // A CVHR dip is a temporary *reduction* of beat interval (pulse acceleration).
        // The broad ~60s shoulders, refractory period, and adaptive envelope reject
        // ordinary beat-to-beat noise. This is not the full polynomial-fit ACAT.
        for (i in 9 until rrMs.size - 9) {
            val center = rrMs[i]
            if (!center.isFinite()) continue
            val near = (i - 2..i + 2).map { rrMs[it] }
            if (near.any { !it.isFinite() } || near.any { it < center - 1.0 }) continue
            val left = (i - 9..i - 4).map { rrMs[it] }.filter { it.isFinite() }
            val right = (i + 4..i + 9).map { rrMs[it] }.filter { it.isFinite() }
            if (left.size < 4 || right.size < 4) continue
            val shoulders = min(left.max(), right.max())
            val depth = shoulders - center
            val envelope = max(left.max(), right.max()) - min(center, min(left.min(), right.min()))
            if (depth < 55.0 || envelope <= 0 || depth / envelope <= 0.40) continue
            // The window below spans 45s from centre to shoulder and approximates
            // the published dip depth/width acceptance (0.7ms/s) conservatively.
            if (depth / 90.0 <= 0.7) continue
            val ts = startTs + i * BIN_SECONDS
            if ((-10L..10L).any { motionSeconds.contains(ts + it) }) continue
            val shape = (i - 6..i + 6).map { rrMs[it] }.toDoubleArray()
            if (shape.any { !it.isFinite() }) continue
            val dip = Dip(i, depth, shape)
            if (found.isNotEmpty() && (i - found.last().bin) * BIN_SECONDS < 25L) {
                if (depth > found.last().depth) found[found.lastIndex] = dip
            } else {
                found.add(dip)
            }
        }
        return found
    }

    /** Four-dip groups with period 25–130s, similarity and cycle equivalence. */
    private fun autocorrelatedGroups(candidates: List<Dip>): Set<Int> {
        val accepted = HashSet<Int>()
        if (candidates.size < 4) return accepted
        for (i in 0 until candidates.size - 3) {
            val group = candidates.subList(i, i + 4)
            val gaps = (0..2).map { j ->
                (group[j + 1].bin - group[j].bin) * BIN_SECONDS.toDouble()
            }
            if (gaps.any { it !in 25.0..130.0 }) continue
            val mean = gaps.average()
            val equivalence = gaps.fold(1.0) { product, gap ->
                product * (3.0 - 2.0 * gap / mean)
            }
            if (equivalence <= 0.80) continue
            val similarities = (0..2).map { j -> correlation(group[j].shape, group[j + 1].shape) }
            if (similarities.average() <= 0.40 || similarities.any { it <= 0.20 }) continue
            group.forEach { accepted.add(it.bin) }
        }
        return accepted
    }

    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        val aa = a.average()
        val bb = b.average()
        var numerator = 0.0
        var denA = 0.0
        var denB = 0.0
        for (i in a.indices) {
            val x = a[i] - aa
            val y = b[i] - bb
            numerator += x * y
            denA += x * x
            denB += y * y
        }
        return if (denA < 0.001 || denB < 0.001) 0.0
        else numerator / kotlin.math.sqrt(denA * denB)
    }
}
