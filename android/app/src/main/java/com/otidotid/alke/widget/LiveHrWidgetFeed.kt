package com.otidotid.alke.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One feed owned by the process-wide BLE client. Never suspend a Bluetooth callback. */
internal class LiveHrWidgetFeed(private val context: Context) {
    private data class Sample(val bpm: Int, val at: Long)
    private val samples = Channel<Sample>(Channel.CONFLATED)

    fun offer(value: Int) {
        samples.trySend(Sample(value, System.currentTimeMillis()))
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var previous: Sample? = null
            while (true) {
                val last = previous
                val fresh = if (last == null) samples.receive()
                    else withTimeoutOrNull(HrDisplay.LIVE_MS) { samples.receive() }
                val sample = fresh ?: last ?: continue
                if (fresh != null && last?.bpm == sample.bpm && last.at / 60_000 == sample.at / 60_000) continue
                try {
                    val ids = GlanceAppWidgetManager(context).getGlanceIds(HrGlanceWidget::class.java)
                    for (id in ids) {
                        updateAppWidgetState(context, id) { prefs ->
                            prefs[bpm] = sample.bpm
                            prefs[at] = sample.at
                            prefs[live] = fresh != null
                        }
                        HrGlanceWidget().update(context, id)
                    }
                    previous = fresh
                    delay(if (ids.isEmpty()) 5_000L else 1_000L)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("LiveHrWidgetFeed", "Widget update failed", e)
                    delay(1_000)
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
