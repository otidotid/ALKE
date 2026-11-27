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

    @Test fun meRoutesToOriginalSettingsControlsWithoutRootAccordion() {
        val me = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        val segment = me.substringAfter("fun AlkeMeExperience(").substringBefore("fun AlkeMeGroupExperience(")
        assertFalse(segment.contains("SettingsScreen("))
        assertFalse(segment.contains("personalSettingsExpanded"))
        val groups = me.substringAfter("fun AlkeMeGroupExperience(").substringBefore("fun MeDockSettings(")
        for (page in MeSettingsPage.entries.filter { it != MeSettingsPage.ALL }) {
            assertTrue("Unreachable settings page ${page.name}", groups.contains("MeSettingsPage.${page.name}"))
        }
        assertTrue(me.contains("AlkeDockEditorDialog(onDismiss = { showDockEditor = false })"))
        org.junit.Assert.assertEquals(MeGroup.entries.size, MeGroup.entries.map { it.route }.distinct().size)
        org.junit.Assert.assertEquals(MeSettingsPage.entries.size, MeSettingsPage.entries.map { it.route }.distinct().size)
    }

    @Test fun settingsKeepExistingBindingsAndScopedDetailCards() {
        val settings = source("src/main/java/com/otidotid/alke/ui/SettingsScreen.kt")
        assertTrue(settings.contains("page: MeSettingsPage"))
        assertTrue(settings.contains("SettingsCard("))
        assertTrue(settings.contains("onClick = onOpenTestCentre"))
        assertTrue(settings.contains("forceOpen = page == MeSettingsPage.ADVANCED"))
        assertFalse(settings.contains("title = \"Backup & History\""))
        val components = source("src/main/java/com/otidotid/alke/ui/SettingsComponents.kt")
        assertTrue(components.contains("if (!detailStyle) Icon("))
        assertTrue(components.contains("if (forceOpen)"))
        val scaffolds = source("src/main/java/com/otidotid/alke/ui/Components.kt")
        assertTrue(scaffolds.contains("LocalMeDetailStyle.current"))
        assertTrue(scaffolds.contains("MeDetailHeader(title, subtitle)"))
    }

    @Test fun settingsModuleIsRetiredButLegacyRouteStillResolves() {
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("composable(Destination.Settings.route)"))
        assertTrue(nav.contains("popUpTo(Destination.Settings.route) { inclusive = true }"))
        assertTrue(nav.contains("popUpTo(Destination.Profile.route) { inclusive = true }"))
        val dock = nav.substringAfter("internal val configurableBottomBarDestinations")
            .substringBefore("internal object BottomBarModuleStore")
        assertFalse(dock.contains("Destination.Settings,"))
        assertFalse(dock.contains("Destination.MeDetailPage,"))
        assertTrue(nav.contains("page = page,"))
        assertTrue(nav.contains("onOpenSelfHostedPush ="))
        assertTrue(nav.contains("GoogleAccountCard(accountOnly = true"))
        assertTrue(nav.contains("BackupSyncScreen(onOpenAccount ="))
    }
}
