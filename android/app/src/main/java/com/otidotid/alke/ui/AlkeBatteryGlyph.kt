package com.otidotid.alke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** One Option A glyph for global and detailed Today headers. Fill encodes actual charge. */
@Composable
internal fun AlkeBatteryGlyph(battery: Int?, charging: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val x = size.width / 36f
        val y = size.height / 20f
        val outline = if (battery == null) Color(0xFF9BB9C5) else Color(0xFFF5FCFF)
        val fill = if (battery != null && battery <= 20) Color(0xFFF0BF60) else Color(0xFF26DEEB)
        drawRoundRect(
            color = outline, topLeft = Offset(1.5f*x, 3f*y),
            size = Size(30f*x, 14f*y), cornerRadius = CornerRadius(3f*x, 3f*y),
            style = Stroke(width = 1.8f*x),
        )
        drawRoundRect(
            color = outline, topLeft = Offset(33f*x, 7f*y),
            size = Size(2f*x, 6f*y), cornerRadius = CornerRadius(x, y),
        )
        val fraction = (battery ?: 0).coerceIn(0, 100) / 100f
        if (fraction > 0f) {
            drawRoundRect(
                color = fill, topLeft = Offset(4.5f*x, 6f*y),
                size = Size(24f*x*fraction, 8f*y), cornerRadius = CornerRadius(x, y),
            )
        }
        if (charging && battery != null) {
            val bolt = Path().apply {
                moveTo(18f*x, 4.5f*y); lineTo(12.5f*x, 10.5f*y)
                lineTo(16f*x, 10.5f*y); lineTo(14.5f*x, 15.5f*y)
                lineTo(21f*x, 8.5f*y); lineTo(17.5f*x, 8.5f*y); close()
            }
            drawPath(bolt, Color(0xFF1B222D))
        }
    }
}
