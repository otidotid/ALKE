package com.otidotid.alke.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.otidotid.alke.R

/** Shared 2x2 / 3x2 visual language. Iconography is vector-backed, never emoji or text substitutes. */
@Composable
internal fun WidgetGlyph(@DrawableRes icon: Int, tint: ColorProvider, size: Int = 15) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = GlanceModifier.width(size.dp).height(size.dp),
    )
}

internal fun batteryAccent(pct: Int?): ColorProvider = when (pct) {
    null -> PremiumWidgetPalette.Secondary
    in 0..20 -> PremiumWidgetPalette.Red
    in 21..40 -> PremiumWidgetPalette.Amber
    else -> PremiumWidgetPalette.Green
}

/** True 5-segment battery gauge, colored by remaining wearable battery (not phone battery). */
@Composable
internal fun WidgetBatteryGauge(
    battery: Int?,
    compact: Boolean = false,
) {
    val color = batteryAccent(battery)
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        WidgetGlyph(R.drawable.ic_widget_strap_battery, color, if (compact) 12 else 16)
        Spacer(GlanceModifier.width(4.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            repeat(5) { index ->
                val filled = battery != null && battery > index * 20
                Box(
                    modifier = GlanceModifier
                        .width(if (compact) 5.dp else 8.dp)
                        .height(6.dp)
                        .background(if (filled) color else PremiumWidgetPalette.SurfaceRaised)
                        .cornerRadius(2.dp),
                ) {}
                if (index < 4) Spacer(GlanceModifier.width(2.dp))
            }
        }
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = battery?.let { "${it}%" } ?: "--",
            style = TextStyle(
                color = color, fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Compact icon + readable value; empty metrics are honest dashes, never invented zeroes. */
@Composable
internal fun WidgetValueTile(
    label: String,
    value: Int?,
    @DrawableRes icon: Int,
    accent: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    large: Boolean = false,
    suffix: String = "",
) {
    Column(
        modifier = modifier
            .background(PremiumWidgetPalette.Surface)
            .cornerRadius(12.dp)
            .padding(horizontal = if (large) 9.dp else 6.dp, vertical = if (large) 8.dp else 6.dp),
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetGlyph(icon, accent, if (large) 16 else 13)
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = value?.let { "$it$suffix" } ?: "--",
                style = TextStyle(
                    color = PremiumWidgetPalette.Primary,
                    fontSize = if (large) 24.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                color = PremiumWidgetPalette.Secondary,
                fontSize = if (large) 10.sp else 9.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

internal fun hrAccentForStatus(status: PremiumHrStatus): ColorProvider = when (status) {
    PremiumHrStatus.LIVE -> PremiumWidgetPalette.Green
    PremiumHrStatus.LAST_RECORDED -> PremiumWidgetPalette.Amber
    PremiumHrStatus.DISCONNECTED -> PremiumWidgetPalette.Red
    PremiumHrStatus.UNAVAILABLE -> PremiumWidgetPalette.Secondary
}
