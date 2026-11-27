package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Me's two accordions are visually identical, accessible, and preserve navigation. */
class AlkeMeDisclosureContractTest {
    private fun experience(): String {
        var dir = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(6) {
            val file = File(dir, "app/src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
            if (file.isFile) return file.readText()
            dir = dir.parentFile ?: return@repeat
        }
        error("ALKE Experience source missing")
    }

    @Test fun firstTwoLayersAreNavigationOnly() {
        val source = experience()
        val me = source.substringAfter("fun AlkeMeExperience(").substringBefore("fun AlkeMeGroupExperience(")
        val groups = source.substringAfter("fun AlkeMeGroupExperience(").substringBefore("fun MeDockSettings(")
        for (title in listOf("Your Profile", "Wearable", "Data Management", "Health & Fitness",
            "Intelligence", "Notifications & Haptics", "Appearance", "All Modules", "General")) {
            assertTrue("Missing approved Me entry: $title", me.contains(title))
        }
        for (section in listOf(me, groups)) {
            assertFalse(section.contains("AlkeMeDisclosureHeader("))
            assertFalse(section.contains("collapsible = true"))
            assertFalse(section.contains("var expanded"))
        }
        assertTrue(me.contains("MeNavigationCard("))
        assertTrue(groups.contains("MeNavigationRow("))
        assertTrue(groups.contains("onDetail(MeSettingsPage.ACTIVITY)"))
        assertTrue(groups.contains("onDetail(MeSettingsPage.SLEEP)"))
    }

    @Test fun navigationRowsUseOneIconAndCardContract() {
        val source = experience()
        val row = source.substringAfter("private fun MeNavigationRow(").substringBefore("private fun MeNavigationCard(")
        assertTrue(row.contains("Icon(icon,"))
        assertTrue(row.contains("Icons.Filled.ChevronRight"))
        assertTrue(row.contains(".clickable(onClick = onClick)"))
        val dock = source.substringAfter("private fun AlkeDockEditorDialog(")
        assertFalse(dock.contains("Icon(destination.icon"))
        val modules = source.substringAfter("fun AlkeAllModulesExperience(").substringBefore("/** Layer 1")
        assertFalse(modules.contains("var expanded"))
        assertTrue(modules.contains("MeNavigationCard("))
    }
}
