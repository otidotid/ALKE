package com.otidotid.alke.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** ALKE Daily: four equal-priority metrics in a launcher-safe 2x2 grid. */
class AlkeCompactGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching { WidgetSnapshotStore.load(context) }
            .getOrDefault(WidgetSnapshot())
        val state = PremiumWidgetStateEngine.read(snapshot)
        provideContent { DailyGrid(state) }
    }
}

@Composable
private fun DailyGrid(state: PremiumWidgetState) {
    PremiumCard {
        BrandRow(label = "DAILY", state = state)
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            DailyMetric("REST", state.rest, PremiumWidgetPalette.Violet, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            DailyMetric("CHARGE", state.recovery, PremiumWidgetPalette.Cyan, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            DailyMetric("EFFORT", state.effort, PremiumWidgetPalette.Blue, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            DailyMetric(
                label = "HR BPM",
                value = state.heartRate,
                color = when (state.heartRateStatus) {
                    PremiumHrStatus.LIVE -> PremiumWidgetPalette.Green
                    PremiumHrStatus.LAST_RECORDED -> PremiumWidgetPalette.Amber
                    PremiumHrStatus.DISCONNECTED -> PremiumWidgetPalette.Red
                    PremiumHrStatus.UNAVAILABLE -> PremiumWidgetPalette.Secondary
                },
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun DailyMetric(
    label: String,
    value: Int?,
    color: ColorProvider,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Text(
            text = value?.toString() ?: "--",
            style = TextStyle(color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = label,
            style = TextStyle(
                color = PremiumWidgetPalette.Secondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
