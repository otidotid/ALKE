package com.otidotid.alke.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * ALKE Device replaces the duplicate Balance widget.
 * Shows strap battery, connection health and the latest HR sample.
 */
class AlkeGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching { WidgetSnapshotStore.load(context) }
            .getOrDefault(WidgetSnapshot())
        val state = PremiumWidgetStateEngine.read(snapshot)
        provideContent { DeviceContent(state) }
    }
}

@Composable
private fun DeviceContent(state: PremiumWidgetState) {
    PremiumCard {
        DeviceHeader(state)
        Spacer(GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            BatteryPanel(state, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(10.dp))
            HeartRatePanel(state, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(9.dp))
        Text(
            text = state.hrTime(),
            style = TextStyle(
                color = PremiumWidgetPalette.Secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun DeviceHeader(state: PremiumWidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = "ALKE DEVICE",
            style = TextStyle(
                color = PremiumWidgetPalette.Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = if (state.connected) "CONNECTED" else "OFFLINE",
            style = TextStyle(
                color = if (state.connected) PremiumWidgetPalette.Green else PremiumWidgetPalette.Red,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun BatteryPanel(state: PremiumWidgetState, modifier: GlanceModifier) {
    val batteryColor = when (state.battery) {
        null -> PremiumWidgetPalette.Secondary
        in 80..100 -> PremiumWidgetPalette.Green
        in 26..79 -> PremiumWidgetPalette.Amber
        else -> PremiumWidgetPalette.Red
    }
    Column(
        modifier = modifier
            .background(PremiumWidgetPalette.Surface)
            .cornerRadius(18.dp)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = state.battery?.let { "$it%" } ?: "--",
            style = TextStyle(color = batteryColor, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "WHOOP BATTERY",
            style = TextStyle(
                color = PremiumWidgetPalette.Secondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun HeartRatePanel(state: PremiumWidgetState, modifier: GlanceModifier) {
    val hrColor = when (state.heartRateStatus) {
        PremiumHrStatus.LIVE -> PremiumWidgetPalette.Green
        PremiumHrStatus.LAST_RECORDED -> PremiumWidgetPalette.Amber
        PremiumHrStatus.DISCONNECTED -> PremiumWidgetPalette.Red
        PremiumHrStatus.UNAVAILABLE -> PremiumWidgetPalette.Secondary
    }
    val hrStatus = when (state.heartRateStatus) {
        PremiumHrStatus.LIVE -> "LIVE HR"
        PremiumHrStatus.LAST_RECORDED -> "RECORDED"
        PremiumHrStatus.DISCONNECTED -> "NO SIGNAL"
        PremiumHrStatus.UNAVAILABLE -> "WAITING"
    }
    Column(
        modifier = modifier
            .background(PremiumWidgetPalette.SurfaceRaised)
            .cornerRadius(18.dp)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = state.heartRate?.toString() ?: "--",
            style = TextStyle(color = hrColor, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "$hrStatus | BPM",
            style = TextStyle(
                color = PremiumWidgetPalette.Secondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
