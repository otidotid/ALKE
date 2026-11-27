package com.otidotid.alke.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stable group and page keys; navigation changes do not migrate any persisted setting. */
internal enum class MeGroup(val title: String) {
    PROFILE("Your Profile"), WEARABLE("Wearable"), DATA("Data Management"),
    HEALTH("Health & Fitness"), INTELLIGENCE("Intelligence"),
    NOTIFICATIONS("Notifications & Haptics"), APPEARANCE("Appearance"), GENERAL("General");
    val route get() = "me_group/" + name
    companion object {
        fun from(key: String?) = entries.firstOrNull { it.name == key }
    }
}

enum class MeSettingsPage(val title: String) {
    ALL("Me"), PROFILE("Personal Information"), HEART("Heart Rate & Effort"),
    DISPLAY("Measurement Display"), GENERAL("Language & Time"), COACH("AI Coach"),
    STRAP("Connection & Sync"), CALIBRATION("Steps & Daily Cycle"),
    WELLNESS("Wellness & Training"), REMINDERS("Personal Reminders"),
    ADVANCED("Advanced & Diagnostics"), CHARGE("Charge Baseline"), ABOUT("About & Privacy"),
    ACTIVITY("Activity Detection"), SLEEP("Sleep Intelligence"), HAPTICS("Wrist Feedback"),
    ALERTS("Health & Battery Alerts"), ACCOUNT("Google Account"), PROVENANCE("Data Provenance"),
    DOCK("Customize Dock"), ALARMS("Alarms");
    val route get() = "me_detail/" + name
    companion object {
        fun from(key: String?) = entries.firstOrNull { it.name == key && it != ALL }
    }
}

enum class MeAutomationPage(val title: String) {
    ALL("Automations"), HAPTICS("Wrist Feedback"), SLEEP("Sleep Intelligence"), ALERTS("Health & Battery Alerts")
}

/** Scope the new visual contract to Me details only, never to the analytics tabs. */
internal val LocalMeDetailStyle = staticCompositionLocalOf { false }
internal val LocalMeBack = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun MeDetailStyle(onBack: () -> Unit, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMeDetailStyle provides true, LocalMeBack provides onBack, content = content)
}

/** The title, subtitle and optional Back action share a single card. */
@Composable
internal fun MeDetailHeader(title: String?, subtitle: String?) {
    val back = LocalMeBack.current
    AlkeCard(padding = 16.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (back != null) TextButton(onClick = back) { Text("Back") }
            if (title != null) Text(title, style = AlkeType.title2, color = Palette.textPrimary)
            if (subtitle != null) Text(subtitle, style = AlkeType.subhead, color = Palette.textSecondary)
        }
    }
}
