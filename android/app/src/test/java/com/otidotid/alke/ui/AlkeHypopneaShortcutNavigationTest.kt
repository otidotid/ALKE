package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Native Today -> Sleep deep-scroll must not create a false hypopnea risk result. */
class AlkeHypopneaShortcutNavigationTest {
    private fun source(name: String): String {
        val path = "src/main/java/com/otidotid/alke/ui/$name"
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return listOf(File(cwd, path), File(cwd, "app/$path"), File(cwd, "android/app/$path"))
            .firstOrNull { it.isFile }?.readText()
            ?: error("Missing source: $path")
    }

    @Test fun nativeTodayHasCompactClickableShortcutWithNoFakeLowRisk() {
        val ui = source("AlkeExperience.kt")
        val block = ui.substring(ui.indexOf("private fun AlkeHypopneaRiskShortcut("),
            ui.indexOf("private fun niceMinutes(", ui.indexOf("private fun AlkeHypopneaRiskShortcut(")))
        assertTrue(block.contains("AlkePanel(modifier = Modifier.fillMaxWidth(), onClick = onClick)"))
        assertTrue(block.contains("\"HYPOPNEA RISK\""))
        assertTrue(block.contains("\"NOT ASSESSED\""))
        assertFalse(block.contains("\"LOW\""))
        assertTrue(ui.contains("AlkeHypopneaRiskShortcut(onClick = onSleepBreathing)"))
    }

    @Test fun shortcutEntersRealSleepTabAndTargetsReorderableBreathingSection() {
        val nav = source("AppRoot.kt")
        assertTrue(nav.contains("onSleepBreathing = {"))
        assertTrue(nav.contains("sleepBreathingJumpRequested = true"))
        assertTrue(nav.contains("nav.navigate(Destination.Sleep.route) { launchSingleTop = true }"))
        assertTrue(nav.contains("focusBreathingSection = sleepBreathingJumpRequested"))
        assertTrue(nav.contains("onBreathingFocusConsumed = { sleepBreathingJumpRequested = false }"))

        val sleep = source("SleepScreen.kt")
        assertTrue(sleep.contains("val targetKey = SLEEP_SECTION_KEY_PREFIX + SleepSection.SLEEP_BREATHING.raw"))
        assertTrue(sleep.contains("sleepListState.animateScrollToItem(match.index)"))
        assertTrue(sleep.contains("openDetailsOnEntry = focusBreathingSection"))
    }

    @Test fun detailDoesNotMislabelRespiratoryRateAsClinicalHypopneaRisk() {
        val ui = source("SleepBreathingQualityCard.kt")
        assertTrue(ui.contains("HYPOPNEA RISK: NOT ASSESSED"))
        assertTrue(ui.contains("ALKE is not a medical device."))
        assertTrue(ui.contains("consult a qualified healthcare professional."))
        assertFalse(ui.contains("HYPOPNEA RISK: LOW"))
        val detailIndex = ui.indexOf("if (expanded) {")
        assertTrue(detailIndex >= 0)
        assertTrue(ui.indexOf("formatRate(report)", detailIndex) > detailIndex)
    }
}
