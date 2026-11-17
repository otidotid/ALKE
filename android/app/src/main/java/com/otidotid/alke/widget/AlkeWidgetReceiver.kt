package com.otidotid.alke.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Manifest entry point for the home-screen widget — all rendering lives in [AlkeGlanceWidget]. */
class AlkeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlkeGlanceWidget()
}
