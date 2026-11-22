package com.otidotid.alke.widget

import android.content.Context
import android.content.Intent
import com.otidotid.alke.ui.MainActivity

/**
 * Typed, allowlisted entry from home-screen widgets. Never accepts arbitrary destinations.
 * CLEAR_TOP + SINGLE_TOP returns to an already running MainActivity rather than stacking another
 * activity. MainActivity handles both cold launch and onNewIntent.
 */
object WidgetNavigation {
    const val ACTION_HEART_RATE_TIMELINE = "com.otidotid.alke.action.OPEN_HR_TIMELINE"
    const val ROUTE_HEART_RATE_TIMELINE = "alke_hr_timeline"

    fun heartRateTimelineIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_HEART_RATE_TIMELINE
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    fun routeForAction(action: String?): String? = when (action) {
        ACTION_HEART_RATE_TIMELINE -> ROUTE_HEART_RATE_TIMELINE
        else -> null
    }
}
