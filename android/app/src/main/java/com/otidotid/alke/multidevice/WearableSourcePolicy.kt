package com.otidotid.alke.multidevice

/**
 * Source-selection foundation for the native WHOOP + Huawei integration.
 * No Bluetooth, persistence or existing engine wiring is implemented here.
 * Callers must normalize measurement units, validate sensor quality, and align
 * intervals/sessions before selecting a candidate. Never compare overlapping
 * raw counters or incompatible HRV modalities using this selector alone.
 */
internal object WearableSourcePolicy {
    enum class Source { HUAWEI, WHOOP, PHONE_GPS }
    enum class Metric {
        HEART_RATE, HRV, RR_INTERVAL, SLEEP, STEPS, GPS, WORKOUT_DETAIL, SPO2
    }

    data class Candidate<T>(
        val source: Source,
        val value: T,
        val valid: Boolean,
        val compatible: Boolean,
        val revision: Long,
        val recordId: String,
    )

    fun priority(metric: Metric): List<Source> = when (metric) {
        Metric.HEART_RATE, Metric.STEPS, Metric.WORKOUT_DETAIL ->
            listOf(Source.HUAWEI, Source.WHOOP)
        Metric.HRV, Metric.RR_INTERVAL, Metric.SLEEP, Metric.SPO2 ->
            listOf(Source.WHOOP, Source.HUAWEI)
        Metric.GPS -> listOf(Source.HUAWEI, Source.PHONE_GPS)
    }

    /**
     * Select one observation for one already aligned metric interval/session.
     * A newly synced primary replaces a fallback when the same interval is
     * reconsidered. Unknown/incompatible readings must fail validation upstream.
     * Null means missing; never synthesize a reading or add overlapping totals.
     * Connection state is deliberately not used to invalidate historical data.
     */
    fun <T> select(
        metric: Metric,
        candidates: Iterable<Candidate<T>>,
    ): Candidate<T>? {
        val ranked = priority(metric)
        return candidates.asSequence()
            .filter { it.valid && it.compatible && it.source in ranked }
            .sortedWith(
                compareBy<Candidate<T>> { ranked.indexOf(it.source) }
                    .thenByDescending { it.revision }
                    .thenBy { it.recordId }
            )
            .firstOrNull()
    }
}
