package com.otidotid.alke.analytics

import com.otidotid.alke.data.WorkoutRow
import kotlin.math.abs

/**
 * Conservative, on-device outdoor-running pace and submaximal VO2max estimator.
 *
 * The workout table already stores GPS distance, active duration, route and average HR.
 * No new database columns, cloud dependency or synthetic pace are needed.
 *
 * The ACSM level-running oxygen cost (3.5 + 0.2 * metres/minute) is extrapolated
 * using heart-rate reserve (HRR). This is a wellness estimate, NOT a lab VO2max.
 * Only independently recorded GPS routes + plausible exercise HR qualify. A
 * multi-run median gently calibrates the unchanged Nes/Uth baseline.
 */
object WorkoutPaceVo2Engine {
    const val ALGORITHM_VERSION = "vo2max-outdoor-run-pace-hrr-v1"
    const val REQUIRED_RUNS = 3
    const val LOOKBACK_DAYS = 42L

    data class Run(
        val startTs: Long,
        val paceSecPerKm: Double,
        val predictedVo2max: Double,
    )

    data class Estimate(
        val value: Double,
        val baseline: Double,
        val runsUsed: Int,
        val medianPaceSecPerKm: Double,
        val medianRunVo2max: Double,
    )

    /** Stored active duration wins over wall time (the latter includes workout pauses). */
    fun paceSecPerKm(row: WorkoutRow): Double? {
        val distance = row.distanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val wallSeconds = (row.endTs - row.startTs).toDouble()
        val activeSeconds = row.durationS?.takeIf { it.isFinite() && it > 0.0 } ?: wallSeconds
        if (activeSeconds <= 0.0 || wallSeconds <= 0.0 || activeSeconds > wallSeconds + 2.0) return null
        return RouteMath.paceSecPerKm(distance, activeSeconds)
            ?.takeIf { it.isFinite() && it in 150.0..3600.0 }
    }

    /** Require real outdoor GPS geometry; hand-entered distance/HR is insufficient. */
    fun qualifiedRun(row: WorkoutRow, restingHr: Double, hrMax: Double): Run? {
        if (!row.sport.equals("Running", ignoreCase = true)) return null
        if (restingHr !in 35.0..105.0 || hrMax !in 140.0..230.0 || hrMax - restingHr < 60.0) return null
        val elapsed = row.durationS?.takeIf { it.isFinite() && it > 0 } ?: (row.endTs - row.startTs).toDouble()
        if (elapsed !in 1200.0..7200.0) return null
        val distance = row.distanceM?.takeIf { it.isFinite() && it in 2500.0..42000.0 } ?: return null
        val pace = paceSecPerKm(row) ?: return null
        val speedKmh = 3600.0 / pace
        if (speedKmh !in 7.0..18.0) return null
        val encoded = row.routePolyline?.takeIf { it.length in 24..100000 } ?: return null
        val route = runCatching { RouteMath.decode(encoded) }.getOrNull() ?: return null
        if (route.size < 12 || route.any { !it.lat.isFinite() || !it.lon.isFinite() || it.lat !in -90.0..90.0 || it.lon !in -180.0..180.0 }) return null
        val routeDistance = RouteMath.totalMeters(route)
        if (!routeDistance.isFinite() || abs(routeDistance - distance) / distance > 0.20) return null

        val avgHr = row.avgHr?.toDouble() ?: return null
        val peakHr = row.maxHr?.toDouble()
        if (peakHr != null && (peakHr < avgHr || peakHr > hrMax + 12.0)) return null
        val reserveFraction = (avgHr - restingHr) / (hrMax - restingHr)
        if (avgHr / hrMax < 0.72 || reserveFraction !in 0.60..0.90) return null

        // ACSM flat-running gross VO2 estimate; extrapolate the exercise component by HRR.
        val metabolicCost = 3.5 + (0.2 * (distance / elapsed * 60.0)) / reserveFraction
        if (!metabolicCost.isFinite() || metabolicCost !in 25.0..80.0) return null
        return Run(row.startTs, pace, metabolicCost)
    }

    /** Historical snapshots never consume workouts recorded after the snapshot's end. */
    fun estimate(
        workouts: List<WorkoutRow>,
        baseline: Double,
        restingHr: Double,
        hrMax: Double,
        asOfEpochSeconds: Long,
    ): Estimate? {
        if (!baseline.isFinite() || baseline !in 15.0..85.0 || asOfEpochSeconds <= 0L) return null
        val from = asOfEpochSeconds - LOOKBACK_DAYS * 86400L
        val eligible = workouts.asSequence()
            .filter { it.startTs >= from && it.endTs <= asOfEpochSeconds && it.endTs > it.startTs }
            .distinctBy { it.deviceId to it.startTs }
            .mapNotNull { qualifiedRun(it, restingHr, hrMax) }
            .sortedByDescending { it.startTs }
            .take(8)
            .toList()
        if (eligible.size < REQUIRED_RUNS) return null
        // At least two separate UTC days, so duplicate or overlapping laps do not masquerade as training history.
        if (eligible.map { it.startTs / 86400L }.distinct().size < 2) return null

        fun median(xs: List<Double>): Double {
            val sorted = xs.sorted()
            return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        val roughMedian = median(eligible.map { it.predictedVo2max })
        val consistent = eligible.filter { abs(it.predictedVo2max - roughMedian) <= 7.0 }
        if (consistent.size < REQUIRED_RUNS) return null

        val runMedian = median(consistent.map { it.predictedVo2max })
        val paceMedian = median(consistent.map { it.paceSecPerKm })
        // Keep Nes/Uth as a stable anchor. Even three matching runs cannot cause a sudden multi-point jump.
        val correction = (runMedian - baseline).coerceIn(-6.0, 6.0) * 0.35
        return Estimate(
            value = (baseline + correction).coerceIn(15.0, 85.0),
            baseline = baseline,
            runsUsed = consistent.size,
            medianPaceSecPerKm = paceMedian,
            medianRunVo2max = runMedian,
        )
    }
}
