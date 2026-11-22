package com.otidotid.alke.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.otidotid.alke.R

/**
 * ALKE Compact · proper 2x2. All four headline readings are preserved.
 * Adds the strap battery as a tiny visual meter instead of sacrificing a metric card.
 */
class AlkeCompactGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val snapshot = runCatching { WidgetSnapshotStore.load(context) }.getOrDefault(WidgetSnapshot())
            val state = PremiumWidgetStateEngine.readLive(
                snapshot,
                streamBpm = prefs[LiveHrWidgetFeed.bpm],
                streamAtMs = prefs[LiveHrWidgetFeed.at] ?: 0L,
                streamActive = prefs[LiveHrWidgetFeed.live] == true,
            )
            CompactContent(state)
        }
    }
}

@Composable
private fun CompactContent(state: PremiumWidgetState) {
    PremiumCard {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text("ALKE", style = TextStyle(
                color = PremiumWidgetPalette.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            ))
            Spacer(GlanceModifier.defaultWeight())
            WidgetBatteryGauge(state.battery, compact = true)
        }
        Spacer(GlanceModifier.height(7.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetValueTile("CHARGE", state.recovery, R.drawable.ic_widget_charge,
                PremiumWidgetPalette.Cyan, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            WidgetValueTile("REST", state.rest, R.drawable.ic_widget_rest,
                PremiumWidgetPalette.Violet, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetValueTile("EFFORT", state.effort, R.drawable.ic_widget_effort,
                PremiumWidgetPalette.Blue, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            WidgetValueTile(
                "HR · BPM", state.heartRate, R.drawable.ic_stat_heart,
                hrAccentForStatus(state.heartRateStatus), GlanceModifier.defaultWeight(),
            )
        }
    }
}
