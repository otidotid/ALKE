package com.otidotid.alke.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.otidotid.alke.ui.MainActivity
import java.text.DateFormat
import java.util.Date

internal object PremiumWidgetPalette {
    val Background = ColorProvider(Color(0xFF07101F))
    val Surface = ColorProvider(Color(0xFF101B2E))
    val SurfaceRaised = ColorProvider(Color(0xFF16243A))
    val Primary = ColorProvider(Color(0xFFF4F8FF))
    val Secondary = ColorProvider(Color(0xFFA9B8CD))
    val Cyan = ColorProvider(Color(0xFF43D7FF))
    val Blue = ColorProvider(Color(0xFF3C73FF))
    val Violet = ColorProvider(Color(0xFF8A78FF))
    val Green = ColorProvider(Color(0xFF46D39A))
    val Amber = ColorProvider(Color(0xFFFFBF5A))
    val Red = ColorProvider(Color(0xFFFF667A))
}

@Composable
internal fun PremiumCard(content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(PremiumWidgetPalette.Background)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        content()
    }
}

@Composable
internal fun BrandRow(
    label: String,
    state: PremiumWidgetState,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = "ALKE",
            style = TextStyle(
                color = PremiumWidgetPalette.Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(GlanceModifier.defaultWeight())

        val statusText = when (state.heartRateStatus) {
            PremiumHrStatus.LIVE -> "LIVE"
            PremiumHrStatus.LAST_RECORDED -> "RECORDED"
            PremiumHrStatus.DISCONNECTED -> "OFFLINE"
            PremiumHrStatus.UNAVAILABLE -> "WAITING"
        }

        val statusColor = when (state.heartRateStatus) {
            PremiumHrStatus.LIVE -> PremiumWidgetPalette.Green
            PremiumHrStatus.LAST_RECORDED -> PremiumWidgetPalette.Amber
            PremiumHrStatus.DISCONNECTED -> PremiumWidgetPalette.Red
            PremiumHrStatus.UNAVAILABLE -> PremiumWidgetPalette.Secondary
        }

        Text(
            text = "$label | $statusText",
            style = TextStyle(
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
internal fun MetricTile(
    label: String,
    value: Int?,
    accent: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    large: Boolean = false,
) {
    Column(
        modifier = modifier
            .background(PremiumWidgetPalette.Surface)
            .cornerRadius(18.dp)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = value?.toString() ?: "--",
            style = TextStyle(
                color = accent,
                fontSize = if (large) 28.sp else 21.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(GlanceModifier.height(2.dp))

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

internal fun PremiumWidgetState.hrTime(): String {
    if (heartRateAtMs <= 0L) {
        return "Waiting for heart rate"
    }

    val formattedTime = DateFormat
        .getTimeInstance(DateFormat.SHORT)
        .format(Date(heartRateAtMs))

    return "Last sample $formattedTime"
}
