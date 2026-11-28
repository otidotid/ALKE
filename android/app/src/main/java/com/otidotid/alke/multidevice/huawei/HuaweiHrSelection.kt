package com.otidotid.alke.multidevice.huawei

import com.otidotid.alke.multidevice.WearableSourcePolicy

/**
 * Preview only: compare HR at exact minute-aligned windows, with WHOOP fallback.
 * Does not feed WHOOP-native scoring, synthetic second samples, or daily counters.
 */
internal object HuaweiHrSelection {
    data class WhoopMinute(val start: Long, val meanBpm: Double)
    data class Point(val start: Long, val bpm: Double, val source: WearableSourcePolicy.Source)

    fun select(huawei: List<HuaweiFitnessRead.Minute>, whoop: List<WhoopMinute>): List<Point> {
        val candidates = mutableMapOf<Long, MutableList<WearableSourcePolicy.Candidate<Double>>>()
        fun add(start: Long, bpm: Double, source: WearableSourcePolicy.Source) {
            if (start % 60L != 0L || !bpm.isFinite() || bpm !in 25.0..250.0) return
            candidates.getOrPut(start) { mutableListOf() }.add(
                WearableSourcePolicy.Candidate(source, bpm, true, true, 0, source.name))
        }
        require(huawei.map { it.epochSeconds }.distinct().size == huawei.size)
        require(whoop.map { it.start }.distinct().size == whoop.size)
        huawei.forEach { row -> row.heartRate?.let { add(row.epochSeconds, it.toDouble(), WearableSourcePolicy.Source.HUAWEI) } }
        whoop.forEach { add(it.start, it.meanBpm, WearableSourcePolicy.Source.WHOOP) }
        return candidates.toSortedMap().mapNotNull { (start, rows) ->
            WearableSourcePolicy.select(WearableSourcePolicy.Metric.HEART_RATE, rows)?.let {
                Point(start, it.value, it.source)
            }
        }
    }
}
