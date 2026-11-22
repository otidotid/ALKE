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

        // Keep the historical 3-hour curve. It is persisted by the 60-second snapshot
        // lane; a 1Hz live readout must not force a large bitmap redraw on every beat.
        // A brand-new placement with no stored trace gets one dot for its first sample.
        val series = if (snapshot.hrSeries.isEmpty() && bpm != null &&
            at > 0L && nowMs - at in 0..HrDisplay.LIVE_MS
        ) {
            HrTrace.append(emptyList(), at / 1000L, bpm, nowMs / 1000L)
        } else {
            snapshot.hrSeries
        }

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
