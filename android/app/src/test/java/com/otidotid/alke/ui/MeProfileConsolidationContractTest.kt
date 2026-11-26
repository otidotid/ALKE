package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MeProfileConsolidationContractTest {
    private fun source(name: String): String {
        var root = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(5) {
            val candidate = File(root, "app/src/main/java/com/otidotid/alke/ui/$name")
            if (candidate.isFile) return candidate.readText()
            root = root.parentFile ?: return@repeat
        }
        error("Source not found: $name")
    }

    @Test fun topRightAvatarShortcutIsRemovedButProfileRouteRemains() {
        val experience = source("AlkeExperience.kt")
        val root = source("AppRoot.kt")
        assertFalse(experience.contains("Open my profile"))
        assertTrue(root.contains("composable(Destination.Profile.route)"))
    }
    @Test
    fun meStartsWithProfileAndKeepsApprovedPremiumGroups() {
        val source = source("AlkeExperience.kt")

        val me = source.substringAfter("fun AlkeMeExperience(")
            .substringBefore("/** The real ALKE dock editor")
        val profile = me.indexOf("Text(\"Your Profile\"")
        val sources = me.indexOf("AlkeSectionTitle(\"Connected Sources\")")
        val prefs = me.indexOf("AlkeSectionTitle(\"Preferences\")")
        assertTrue(profile >= 0 && profile < sources && sources < prefs)
        assertTrue(me.contains("onNavigate(Destination.Profile)"))
        assertTrue(me.contains("PremiumWearableLink(vm,onDevices)"))
        assertTrue(me.contains("PremiumLink(\"Health Connect & imports\""))
        assertTrue(me.contains("PremiumLink(\"Google Drive / Local Folder\""))
        assertTrue(me.contains("onBackupRestore)"))
        assertTrue(me.contains("PremiumLink(\"Smart Settings\""))

        assertFalse(
            source.contains("AlkeSectionTitle(\"Profile\")")
        )

        assertFalse(source.contains("Change photo"))
        assertFalse(source.contains("Choose photo"))
        assertFalse(source.contains("Text(\"Remove\""))
    }
}
