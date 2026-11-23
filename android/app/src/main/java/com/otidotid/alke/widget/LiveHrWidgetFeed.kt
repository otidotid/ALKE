package com.otidotid.alke.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-owned, conflated BLE -> Glance lane. The wearable callback never blocks on the launcher.
 *
 * Dedicated HR widget refreshes immediately when the number changes and has a five-second
 * provenance heartbeat when HR is steady. Daily (compact / 3x2) cards share the live reading
 * at a cheaper five-second cadence instead of waiting for the 60-second snapshot gate.
 * The last sample expires after 15 seconds; no phantom "LIVE" after disconnection.
 */
internal class LiveHrWidgetFeed(context: Context) {
    private val app = context.applicationContext
    private data class Sample(val bpm: Int, val at: Long)
    private val samples = Channel<Sample>(Channel.CONFLATED)

    fun offer(value: Int) {
        if (value in 30..220) samples.trySend(Sample(value, System.currentTimeMillis()))
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var previous: Sample? = null
            var lastHrPush = 0L
            var lastDailyPush = 0L
            var lastStale = false
            while (true) {
                val fresh = if (previous == null) samples.receive()
                    else withTimeoutOrNull(HrDisplay.LIVE_MS) { samples.receive() }
                val sample = fresh ?: previous ?: continue
                // Persist one tiny HR trace point per minute on this IO collector.
                // Never write prefs on the BLE notification callback thread.
                if (fresh != null) {
                    runCatching { LiveHrTraceStore.record(app, sample.bpm, sample.at) }
                        .onFailure { android.util.Log.w("LiveHrWidgetFeed", "HR trace save failed", it) }
                }
                val now = System.currentTimeMillis()
                val becameStale = fresh == null || now - sample.at !in 0..HrDisplay.LIVE_MS
                // One-minute snapshots must not control the live widgets. Equal BPMs still
                // refresh their source timestamp every five seconds, without wasteful bitmap redraws.
                // v613 attempted a full Glance recomposition + bitmap transfer on EVERY
                // changing BPM (~1 Hz). Some launchers fall behind or reject the resulting
                // large RemoteViews; a clogged widget can look frozen for hours.
                // Cap these expensive renders at 3 seconds, but propagate a live->stale
                // transition immediately and refresh steady BPM provenance within 5 seconds.
                val hrDue = becameStale != lastStale ||
                    (previous?.bpm != sample.bpm && now - lastHrPush >= 3_000L) ||
                    now - lastHrPush >= 5_000L
                val dailyDue = becameStale != lastStale ||
                    now - lastDailyPush >= 5_000L
                try {
                    val manager = GlanceAppWidgetManager(app)
                    if (hrDue) {
                        val ids = manager.getGlanceIds(HrGlanceWidget::class.java)
                        for (id in ids) {
                            updateAppWidgetState(app, id) { prefs ->
                                prefs[bpm] = sample.bpm
                                prefs[at] = sample.at
                                prefs[live] = !becameStale
                            }
                            HrGlanceWidget().update(app, id)
                        }
                        lastHrPush = now
                    }
                    if (dailyDue) {
                        val compactIds = manager.getGlanceIds(AlkeCompactGlanceWidget::class.java)
                        for (id in compactIds) {
                            updateAppWidgetState(app, id) { prefs ->
                                prefs[bpm] = sample.bpm
                                prefs[at] = sample.at
                                prefs[live] = !becameStale
                            }
                            AlkeCompactGlanceWidget().update(app, id)
                        }
                        val deviceIds = manager.getGlanceIds(AlkeGlanceWidget::class.java)
                        for (id in deviceIds) {
                            updateAppWidgetState(app, id) { prefs ->
                                prefs[bpm] = sample.bpm
                                prefs[at] = sample.at
                                prefs[live] = !becameStale
                            }
                            AlkeGlanceWidget().update(app, id)
                        }
                        lastDailyPush = now
                    }
                    previous = sample
                    lastStale = becameStale
                    delay(1_000L)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("LiveHrWidgetFeed", "Widget update failed", e)
                    // Do not mark a failed render as successful; retry from the same sample.
                    lastHrPush = 0L
                    lastDailyPush = 0L
                    delay(1_000L)
                }
            }
        }
    }

    companion object {
        val bpm = intPreferencesKey("live_bpm")
        val at = longPreferencesKey("live_sample_at")
        val live = booleanPreferencesKey("stream_active")
    }
}
