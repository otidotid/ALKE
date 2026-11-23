package com.otidotid.alke.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.otidotid.alke.R

/**
 * ALKE Daily 3x2 · primary score triptych + live heart rate + graphical strap battery.
 * Never hides Charge / Rest / Effort behind the battery and HR tiles.
 */
class AlkeGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val snapshot = runCatching { WidgetSnapshotStore.load(context) }.getOrDefault(WidgetSnapshot())
            val state = PremiumWidgetStateEngine.readLive(
                snapshot,
                streamBpm = prefs[LiveHrWidgetFeed.bpm],
                streamAtMs = prefs[LiveHrWidgetFeed.at] ?: 0L,
                streamActive = prefs[LiveHrWidgetFeed.live] == true,
            )
            DailyContent(state)
        }
    }
}

@Composable
private fun DailyContent(state: PremiumWidgetState) {
    PremiumCard {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text("ALKE", style = TextStyle(
                color = PremiumWidgetPalette.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            ))
            Spacer(GlanceModifier.width(7.dp))
            Text("DAILY", style = TextStyle(
                color = PremiumWidgetPalette.Secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            ))
            Spacer(GlanceModifier.defaultWeight())
            WidgetBatteryGauge(state.battery)
        }
        Spacer(GlanceModifier.height(9.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetValueTile("CHARGE", state.recovery, R.drawable.ic_widget_charge,
                PremiumWidgetPalette.Cyan, GlanceModifier.defaultWeight(), large = true)
            Spacer(GlanceModifier.width(5.dp))
            WidgetValueTile("REST", state.rest, R.drawable.ic_widget_rest,
                PremiumWidgetPalette.Violet, GlanceModifier.defaultWeight(), large = true)
            Spacer(GlanceModifier.width(5.dp))
            WidgetValueTile("EFFORT", state.effort, R.drawable.ic_widget_effort,
                PremiumWidgetPalette.Blue, GlanceModifier.defaultWeight(), large = true)
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .background(PremiumWidgetPalette.SurfaceRaised)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            val accent = hrAccentForStatus(state.heartRateStatus)
            WidgetGlyph(R.drawable.ic_stat_heart, accent, 16)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = state.heartRate?.toString() ?: "--",
                style = TextStyle(color = PremiumWidgetPalette.Primary,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text("BPM", style = TextStyle(
                color = PremiumWidgetPalette.Secondary, fontSize = 10.sp,
            ))
            Spacer(GlanceModifier.defaultWeight())
            val source = when (state.heartRateStatus) {
                PremiumHrStatus.LIVE -> "LIVE"
                PremiumHrStatus.LAST_RECORDED -> "LAST RECORDED"
                PremiumHrStatus.DISCONNECTED -> "OFFLINE"
                PremiumHrStatus.UNAVAILABLE -> "NO HR YET"
            }
            Text(source, style = TextStyle(
                color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            ))
        }
    }
}
