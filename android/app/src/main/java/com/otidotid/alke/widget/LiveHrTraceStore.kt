package com.otidotid.alke.widget

import android.content.Context

/**
 * A small, durable 3-hour trace for the dedicated HR widget.
 *
 * The dashboard snapshot is deliberately throttled to once a minute and may be paused
 * when the UI process is backgrounded. A separate BLE-owned 1-minute bucket store
 * prevents a still-streaming strap from leaving the HR widget with an empty chart.
 *
 * Dedicated prefs intentionally do not share WidgetSnapshotStore's read/modify/write:
 * its daily-score and HR writes originate from two producers on different dispatchers.
 */
internal object LiveHrTraceStore {
    private const val FILE = "alke_hr_widget_trace"
    private const val KEY = "last_three_hours"
    private var lastRecordedBucket: Long = Long.MIN_VALUE

    @Synchronized
    fun record(context: Context, bpm: Int, sampleAtMs: Long) {
        if (bpm !in 30..220 || sampleAtMs <= 0L) return
        val bucket = sampleAtMs / (HrTrace.BUCKET_SEC * 1000L)
        if (bucket == lastRecordedBucket) return
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val next = HrTrace.append(
            HrTrace.decode(prefs.getString(KEY, null)),
            ts = sampleAtMs / 1000L,
            bpm = bpm,
            nowSec = sampleAtMs / 1000L,
        )
        prefs.edit().putString(KEY, HrTrace.encode(next)).apply()
        lastRecordedBucket = bucket
    }

    fun load(context: Context, nowMs: Long = System.currentTimeMillis()): List<HrPoint> {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return HrTrace.prune(
            HrTrace.decode(prefs.getString(KEY, null)),
            nowSec = nowMs / 1000L,
        )
    }
}
