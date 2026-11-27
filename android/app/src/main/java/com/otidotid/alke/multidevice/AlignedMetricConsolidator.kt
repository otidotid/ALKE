package com.otidotid.alke.multidevice

/** Consolidates already normalized, aligned intervals without inventing partial values. */
internal object AlignedMetricConsolidator {
    data class Interval(val start: Long, val endExclusive: Long) {
        init { require(start < endExclusive) }
    }

    data class Observation<T>(
        val interval: Interval,
        val candidate: WearableSourcePolicy.Candidate<T>,
    )

    data class Selected<T>(
        val interval: Interval,
        val candidate: WearableSourcePolicy.Candidate<T>?,
    )

    /**
     * Caller supplies exact disjoint canonical windows. Records with different boundaries
     * are rejected rather than proportionally spreading counters or inventing samples.
     * A null selection explicitly preserves missing coverage. Raw input stays unchanged.
     * Re-running affected windows after delayed sync yields deterministic replacements.
     */
    fun <T> reconcile(
        metric: WearableSourcePolicy.Metric,
        windows: List<Interval>,
        observations: Iterable<Observation<T>>,
    ): List<Selected<T>> {
        require(windows.zipWithNext().all { (a, b) -> a.endExclusive <= b.start })
        val allowed = windows.toHashSet()
        val grouped = observations.groupBy { it.interval }
        require(grouped.keys.all { it in allowed }) { "Normalize intervals before consolidation" }
        return windows.map { interval ->
            Selected(interval, WearableSourcePolicy.select(metric,
                grouped[interval].orEmpty().map { it.candidate }))
        }
    }
}
