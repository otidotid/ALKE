package com.otidotid.alke.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.currentState
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.padding
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.otidotid.alke.R
import com.otidotid.alke.ui.MainActivity

/**
 * Dedicated HR widget, v615.
 *
 * v613/v614 used a deep Column with weighted Bitmap chart, bottom-aligned BPM/axis
 * and two long last-recorded labels. Some launchers measured its children into
 * overlapping slots and showed only "Last recorded 3h ago" over a blank region.
 *
 * Glance now owns ONE bounded bitmap and a typed tap action. The bitmap contains
 * the BPM, status, one freshness label and the graph in deterministic non-overlapping
 * coordinates. This deliberately leaves the 2x2 and 3x2 widgets untouched.
 */
class HrGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dark = WidgetTheme.isDark(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val nowMs = System.currentTimeMillis()
            val snapshot = runCatching { WidgetSnapshotStore.load(context) }
                .getOrDefault(WidgetSnapshot())
            val stream = runCatching { LiveHrTraceStore.load(context, nowMs) }
                .getOrDefault(emptyList())
            val display = HrWidgetReading.resolve(
                snapshot = snapshot,
                streamBpm = prefs[LiveHrWidgetFeed.bpm],
                streamAtMs = prefs[LiveHrWidgetFeed.at] ?: 0L,
                streamActive = prefs[LiveHrWidgetFeed.live] == true,
                nowMs = nowMs,
                liveTrace = stream,
            )
            val size = LocalSize.current
            val bitmap = runCatching {
                HrWidgetCanvasRenderer.render(
                    snapshot = display,
                    dark = dark,
                    widthDp = size.width.value,
                    heightDp = size.height.value,
                    density = context.resources.displayMetrics.density,
                    nowMs = nowMs,
                )
            }.onFailure {
                android.util.Log.e("HrGlanceWidget", "Bitmap render failed; showing text fallback", it)
            }.getOrNull()
            val action = actionStartActivity<MainActivity>(
                parameters = actionParametersOf(
                    ActionParameters.Key<String>(WidgetNavigation.EXTRA_WIDGET_ACTION) to
                        WidgetNavigation.ACTION_HEART_RATE_TIMELINE,
                ),
            )
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = when {
                        display.heartRate == null -> "Heart rate: no signal. Open timeline."
                        display.heartRateStale -> "Heart rate ${display.heartRate} beats per minute, last recorded. Open timeline."
                        else -> "Live heart rate ${display.heartRate} beats per minute. Open timeline."
                    },
                    modifier = GlanceModifier.fillMaxSize().clickable(action),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                // Never fall back to an EMPTY widget if Android rejects the bitmap.
                androidx.glance.layout.Column(
                    modifier = GlanceModifier.fillMaxSize()
                        .clickable(action)
                        .background(ColorProvider(androidx.compose.ui.graphics.Color(0xFF07101F)))
                        .padding(14.dp),
                ) {
                    Text(
                        "ALKE · HEART RATE",
                        style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color.White)),
                    )
                    Text(
                        display.heartRate?.let { "$it BPM" } ?: "Waiting for HR",
                        style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color.White)),
                    )
                }
            }
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        android.util.Log.e("HrGlanceWidget", "Glance composition failed", throwable)
        runCatching {
            val rv = android.widget.RemoteViews(context.packageName, R.layout.alke_widget_error)
            android.appwidget.AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, rv)
        }
    }
}

/** Kept for existing JVM contract tests and any callers describing persisted HR samples. */
internal object HrLastRecordedLabel {
    fun text(lastHrAtMs: Long, nowMs: Long): String {
        if (lastHrAtMs <= 0L) return "Last recorded"
        val ageMinutes = ((nowMs - lastHrAtMs).coerceAtLeast(0L) / 60_000L)
        return when {
            ageMinutes < 1L -> "Last recorded · just now"
            ageMinutes < 60L -> "Last recorded · ${ageMinutes}m ago"
            ageMinutes < 24L * 60L -> "Last recorded · ${ageMinutes / 60L}h ago"
            else -> "Last recorded · ${ageMinutes / (24L * 60L)}d ago"
        }
    }
}
