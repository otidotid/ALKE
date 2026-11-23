package com.otidotid.alke.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Launcher-independent HR card. All visible glyphs, badges and trace geometry are drawn in
 * ONE bounded RGB_565 bitmap. Unlike Glance's weighted nested Columns/Rows in v613/v614,
 * the launcher cannot remeasure child text into the same vertical slot as the chart.
 *
 * Glance provides only the image + tap target. The renderer itself does NOT schedule BLE,
 * query databases, or change any scoring/HR algorithms.
 */
internal object HrWidgetCanvasRenderer {
    private const val MAX_PIXELS = 190_000f // <= 380 kB RGB_565 under Binder's 1 MB limit

    data class Layout(
        val width: Float,
        val height: Float,
        val padding: Float,
        val headerBaseline: Float,
        val valueBaseline: Float,
        val detailBaseline: Float,
        val graphTop: Float,
        val graphBottom: Float,
        val headlineSize: Float,
    ) {
        val graphHeight: Float get() = graphBottom - graphTop
        val showGraph: Boolean get() = graphHeight >= 18f
    }

    fun layout(widthDp: Float, heightDp: Float): Layout {
        val w = widthDp.takeIf { it.isFinite() && it >= 96f }?.coerceIn(120f, 540f) ?: 300f
        val h = heightDp.takeIf { it.isFinite() && it >= 80f }?.coerceIn(86f, 360f) ?: 150f
        val small = h < 132f
        val padding = if (w < 190f) 10f else 14f
        val header = if (small) 18f else 22f
        val value = if (small) 59f else 74f
        val detail = if (small) 76f else 93f
        val graphTop = if (small) 84f else 102f
        return Layout(
            width = w, height = h, padding = padding,
            headerBaseline = header, valueBaseline = value,
            detailBaseline = detail,
            graphTop = graphTop,
            graphBottom = h - 11f,
            headlineSize = if (small) 36f else 44f,
        )
    }

    fun render(
        snapshot: WidgetSnapshot,
        dark: Boolean,
        widthDp: Float,
        heightDp: Float,
        density: Float,
        nowMs: Long = System.currentTimeMillis(),
    ): Bitmap {
        val geo = layout(widthDp, heightDp)
        val maxScaleByBudget = sqrt(MAX_PIXELS / (geo.width * geo.height))
        val scale = minOf(
            density.takeIf { it.isFinite() && it > 0f } ?: 2f,
            3.0f, 680f / geo.width, 360f / geo.height, maxScaleByBudget,
        ).coerceAtLeast(0.6f)
        val bmpW = (geo.width * scale).roundToInt().coerceAtLeast(2)
        val bmpH = (geo.height * scale).roundToInt().coerceAtLeast(2)
        // Stable, predictable Binder payload independent of unusual launcher density.
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.scale(bmpW / geo.width, bmpH / geo.height)
        val fg = if (dark) Color.rgb(245, 248, 254) else Color.rgb(22, 39, 59)
        val muted = if (dark) Color.rgb(168, 186, 207) else Color.rgb(86, 100, 117)
        val surface = if (dark) Color.rgb(7, 16, 31) else Color.rgb(244, 248, 252)
        val plot = if (dark) Color.rgb(17, 31, 51) else Color.rgb(230, 237, 246)
        val accent = if (dark) Color.rgb(240, 113, 76) else Color.rgb(196, 76, 50)
        val cyan = if (dark) Color.rgb(67, 215, 255) else Color.rgb(0, 124, 156)
        val green = Color.rgb(62, 199, 153)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        fun color(value: Int) { paint.color = value; paint.style = Paint.Style.FILL; paint.shader = null }
        fun text(
            value: String,
            left: Float,
            baseline: Float,
            size: Float,
            tint: Int,
            bold: Boolean = false,
            maxWidth: Float = geo.width - geo.padding - left,
        ): Float {
            if (maxWidth <= 4f) return left
            color(tint)
            paint.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            paint.textSize = size
            var safe = value
            if (paint.measureText(safe) > maxWidth) {
                while (safe.isNotEmpty() && paint.measureText(safe + "…") > maxWidth) {
                    safe = safe.dropLast(1)
                }
                safe += "…"
            }
            canvas.drawText(safe, left, baseline, paint)
            return left + paint.measureText(safe)
        }

        color(surface)
        canvas.drawRoundRect(0f, 0f, geo.width, geo.height, 16f, 16f, paint)

        // Header: fixed left and right slots. "Recorded" is shown ONCE as a badge,
        // never again underneath the graph where it overlapped in v614.
        val live = snapshot.heartRate != null && !snapshot.heartRateStale
        val badge = when {
            snapshot.heartRate == null -> "WAITING"
            live -> "LIVE"
            else -> "RECORDED"
        }
        color(if (live) green else muted)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        val badgeW = paint.measureText(badge) + 18f
        val badgeLeft = (geo.width - geo.padding - badgeW).coerceAtLeast(geo.padding + 52f)
        color(plot)
        canvas.drawRoundRect(
            badgeLeft, geo.headerBaseline - 12.5f,
            geo.width - geo.padding, geo.headerBaseline + 4f,
            8f, 8f, paint,
        )
        text(badge, badgeLeft + 9f, geo.headerBaseline, 9.5f,
            if (live) green else muted, bold = true,
            maxWidth = geo.width - geo.padding - badgeLeft - 12f)

        val labelRoom = badgeLeft - geo.padding - 8f
        text("ALKE", geo.padding, geo.headerBaseline, 12.5f, cyan, bold = true, maxWidth = labelRoom)
        if (labelRoom >= 112f) {
            text("· HEART RATE", geo.padding + 42f, geo.headerBaseline, 10.5f, muted,
                maxWidth = labelRoom - 42f)
        }

        // Headline is never dependent on the chart's measured height.
        val bpm = snapshot.heartRate?.takeIf { it > 0 }
        val valueRight = text(bpm?.toString() ?: "--", geo.padding, geo.valueBaseline,
            geo.headlineSize, fg, bold = true, maxWidth = geo.width * .55f)
        if (bpm != null) {
            text("BPM", valueRight + 6f, geo.valueBaseline - 2f, 11f, muted,
                bold = true, maxWidth = geo.width - geo.padding - valueRight - 8f)
        }

        val detail = when {
            bpm == null -> "No heart-rate signal"
            !snapshot.heartRateStale -> "Receiving heart rate"
            snapshot.heartRateAtMs <= 0 -> "Last reading · time unavailable"
            else -> HrWidgetAge.label(snapshot.heartRateAtMs, nowMs)
        }
        text(detail, geo.padding, geo.detailBaseline, 10.5f, muted,
            maxWidth = geo.width - 2f * geo.padding)

        // A compact launcher resize cannot steal space from the headline. Simply suppress
        // the trace if there's no room, instead of letting a Glance defaultWeight Spacer
        // force the BPM and status over one another.
        if (!geo.showGraph) return bitmap
        color(plot)
        canvas.drawRoundRect(geo.padding, geo.graphTop,
            geo.width - geo.padding, geo.graphBottom, 9f, 9f, paint)
        val graphLeft = geo.padding + 7f
        val graphRight = geo.width - geo.padding - 7f
        val graphTop = geo.graphTop + 7f
        val graphBottom = geo.graphBottom - 6f
        val graphH = graphBottom - graphTop

        val valid = snapshot.hrSeries
            .filter { it.bpm in 30..220 && it.ts * 1000L <= nowMs &&
                it.ts * 1000L >= nowMs - HrTrace.WINDOW_SEC * 1000L }
            .distinctBy { it.ts }
            .sortedBy { it.ts }
        if (valid.isEmpty()) {
            text(
                if (live) "Building your HR trend…" else "No HR graph for the last 3h",
                graphLeft + 4f, geo.graphTop + geo.graphHeight / 2f + 4f,
                10f, muted, maxWidth = graphRight - graphLeft - 8f,
            )
            return bitmap
        }

        // Keep a minimum visible range for nearly constant resting HR.
        val low = valid.minOf { it.bpm }.toFloat()
        val high = valid.maxOf { it.bpm }.toFloat()
        val middle = (high + low) / 2f
        val range = (high - low).coerceAtLeast(12f)
        val first = valid.first().ts
        val last = valid.last().ts
        val span = (last - first).coerceAtLeast(60L).toFloat()
        fun graphX(p: HrPoint) = graphLeft +
            if (valid.size == 1) (graphRight - graphLeft) * 0.85f
            else (p.ts - first) / span * (graphRight - graphLeft)
        fun graphY(p: HrPoint) = (graphTop + graphBottom) / 2f -
            (p.bpm - middle) / range * graphH * .72f

        color(if (dark) Color.rgb(40, 55, 74) else Color.rgb(203, 213, 225))
        paint.strokeWidth = 0.8f
        for (fraction in listOf(0.33f, 0.66f)) {
            val y = graphTop + graphH * fraction
            canvas.drawLine(graphLeft, y, graphRight, y, paint)
        }

        val path = Path()
        valid.forEachIndexed { i, point ->
            if (i == 0 || point.ts - valid[i - 1].ts > HrTrace.GAP_SEC) {
                path.moveTo(graphX(point), graphY(point))
            } else {
                path.lineTo(graphX(point), graphY(point))
            }
        }
        color(accent)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, paint)
        color(accent)
        canvas.drawCircle(graphX(valid.last()), graphY(valid.last()), 2.9f, paint)
        return bitmap
    }
}

/** Formatter used in Canvas and unit tests: one short age label, no duplicated status. */
internal object HrWidgetAge {
    fun label(atMs: Long, nowMs: Long): String {
        if (atMs <= 0L) return "Last reading"
        val seconds = ((nowMs - atMs).coerceAtLeast(0L) / 1000L)
        return when {
            seconds < 60 -> "Last reading · just now"
            seconds < 3600 -> "Last reading · ${seconds / 60}m ago"
            seconds < 86_400 -> "Last reading · ${seconds / 3600}h ago"
            else -> "Last reading · ${seconds / 86_400}d ago"
        }
    }
}
