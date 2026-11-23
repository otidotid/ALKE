package com.otidotid.alke.widget

/**
 * The dedicated HR widget is fed by two independent producers:
 * - Glance's direct BLE sample, updated without waiting for the snapshot gate
 * - WidgetSnapshotStore, which remains available after a process restart
 *
 * v613 always chose the Glance sample when one had ever been saved, regardless of its age.
 * A dead 3-hour-old Glance preference could therefore mask a CURRENT snapshot reading.
 * Resolve strictly by the newest valid timestamp; neither path is trusted as LIVE after 15s.
 */
internal object HrWidgetReading {
    fun resolve(
        snapshot: WidgetSnapshot,
        streamBpm: Int?,
        streamAtMs: Long,
        streamActive: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        liveTrace: List<HrPoint> = emptyList(),
    ): WidgetSnapshot {
        val storedBpm = snapshot.heartRate?.takeIf { it in 30..220 }
        val storedAt = snapshot.heartRateAtMs
            .takeIf { it in 1..nowMs } ?: 0L
        val directBpm = streamBpm?.takeIf { it in 30..220 }
        val directAt = streamAtMs.takeIf { it in 1..nowMs } ?: 0L
        val directWins = directBpm != null && directAt > 0L &&
            (storedBpm == null || storedAt == 0L || directAt >= storedAt)

        val bpm = if (directWins) directBpm else storedBpm
        val at = if (directWins) directAt else storedAt
        val active = if (directWins) streamActive else
            (!snapshot.heartRateStale && snapshot.connected)
        val isLive = bpm != null && active && at > 0L &&
            nowMs - at in 0..HrDisplay.LIVE_MS

        // Merge the regular minute snapshot with the independent BLE minute-bucket
        // store. The latter wins when both include the same minute, and each trace
        // is pruned to the real three-hour window. A fresh first sample becomes a
        // visible dot until the next minute is recorded.
        val byMinute = LinkedHashMap<Long, HrPoint>()
        for (p in snapshot.hrSeries) byMinute[p.ts] = p
        for (p in liveTrace) byMinute[p.ts] = p
        if (byMinute.isEmpty() && bpm != null && at > 0L &&
            nowMs - at in 0..HrDisplay.LIVE_MS
        ) {
            val point = HrTrace.append(emptyList(), at / 1000L, bpm, nowMs / 1000L)
            for (p in point) byMinute[p.ts] = p
        }
        val series = HrTrace.prune(byMinute.values.toList(), nowMs / 1000L)

        return snapshot.copy(
            heartRate = bpm,
            heartRateAtMs = at,
            // Only HR timestamps are allowed to advance the HR widget's freshness label.
            updatedAtMs = at.takeIf { it > 0L } ?: snapshot.updatedAtMs,
            heartRateStale = bpm != null && !isLive,
            hrSeries = HrTrace.prune(series, nowMs / 1000L),
        )
    }
}
