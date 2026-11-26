package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Me tab owns the full Settings contents; old Settings navigation
 * is a compatibility redirect and must not spawn a second settings page.
 */
class MeSettingsConsolidationTest {
    private fun source(path: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val options = listOf(File(cwd, path), File(cwd, "app/$path"),
            File(cwd, "android/app/$path"), File(cwd, "../$path"))
        return (options.firstOrNull(File::isFile)
            ?: error("Me source missing: $path")).readText()
    }

    @Test fun meRendersOriginalSettingsWithAllCallbacks() {
        val me = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        val segment = me.substringAfter("fun AlkeMeExperience(")
            .substringBefore("/** The real ALKE dock editor")
        assertTrue(segment.contains("SettingsScreen("))
        assertTrue(segment.contains("onOpenBackupSync = onBackupRestore"))
        assertTrue(segment.contains("onOpenTestCentre = onOpenTestCentre"))
        assertTrue(segment.contains("onOpenSelfHostedPush = onOpenSelfHostedPush"))
        // SettingsScreen owns Steps controls internally; there is no separate
        // onOpenStepsCalibration callback in the production API.
        assertTrue(segment.contains("showSettingsBody = personalSettingsExpanded"))
        assertFalse(segment.contains("AlkeLinkRow(\"My profile\""))
        assertFalse(segment.contains("AlkeLinkRow(\"Settings\""))
        assertTrue(segment.contains("PremiumLink(\"Customize Dock\""))
        assertTrue(segment.contains("{ showDockEditor = true }"))
        assertTrue(segment.contains("AlkeDockEditorDialog(onDismiss = { showDockEditor = false })"))
    }

    @Test fun settingsScreenPreservesControlsInsideOneScrollColumn() {
        val settings = source("src/main/java/com/otidotid/alke/ui/SettingsScreen.kt")
        assertTrue(settings.contains("title = \"Me\""))
        assertTrue(settings.contains("meHeader()"))
        assertTrue(settings.contains("SettingsCard("))
        assertTrue(settings.contains("onClick = onOpenTestCentre"))
        // Backup & History has one visible home in Me:
        // Google Drive / Local Folder. The callback remains wired there.
        assertFalse(settings.contains("title = \"Backup & History\""))
        assertFalse(settings.contains("text = \"Open Backup & History\""))
    }

    @Test fun settingsModuleIsRetiredButLegacyRouteStillResolves() {
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("composable(Destination.Settings.route)"))
        assertTrue(nav.contains("popUpTo(Destination.Settings.route) { inclusive = true }"))
        assertTrue(nav.contains("ProfileScreen(onOpenSettings = { nav.navigateTopLevel(Destination.More.route) })"))
        val dock = nav.substringAfter("internal val configurableBottomBarDestinations")
            .substringBefore("internal object BottomBarModuleStore")
        assertFalse(dock.contains("Destination.Settings,"))
        val meEntry = nav.substringAfter("composable(Destination.More.route)")
            .substringBefore("composable(Destination.AlkeAllModules.route)")
        assertFalse(meEntry.contains("onSettings ="))
        assertTrue(meEntry.contains("onOpenSelfHostedPush ="))
    }
}
