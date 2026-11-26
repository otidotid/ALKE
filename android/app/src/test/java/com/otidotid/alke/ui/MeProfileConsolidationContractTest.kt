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
        val backupLink = me.substringAfter("PremiumLink(\"Google Drive / Local Folder\"")
            .substringBefore("PremiumLink(\"Fused Record\"")
        assertTrue("Backup shortcut must preserve the original callback and display its icon",
            backupLink.contains("onBackupRestore, icon = Icons.Filled.CloudSync)"))
        assertTrue(me.contains("PremiumLink(\"Smart Settings\""))

        assertFalse(
            source.contains("AlkeSectionTitle(\"Profile\")")
        )

        assertFalse(source.contains("Change photo"))
        assertFalse(source.contains("Choose photo"))
        assertFalse(source.contains("Text(\"Remove\""))
    }

    @Test
    fun premiumMeShortcutsRenderLeadingVectorsAndSleepCardIdentifiesRiskWithoutEvidence() {
        val experience = source("AlkeExperience.kt")
        val me = experience.substringAfter("fun AlkeMeExperience(")
            .substringBefore("/** The real ALKE dock editor")
        assertTrue(me.contains("icon = Icons.Filled.MonitorHeart"))
        assertTrue(me.contains("icon = Icons.Filled.CloudSync"))
        assertTrue(me.contains("icon = Icons.Filled.Storage"))
        assertTrue(me.contains("icon = Icons.Filled.Sensors"))
        assertTrue(me.contains("icon = Icons.Filled.Apps"))
        assertTrue(me.contains("icon = Icons.Filled.Settings"))
        assertTrue(me.contains("icon = Icons.Filled.AutoAwesome"))
        assertTrue(me.contains("icon = Icons.Filled.BatterySaver"))
        assertTrue(me.contains("icon = Icons.Filled.NotificationsNone"))
        val premium = source("PremiumModules.kt")
        assertTrue(premium.contains("Icon(icon, contentDescription=null"))
        assertTrue(premium.contains("PremiumLink(\"Wearable devices\",status,onClick, icon = Icons.Filled.Sensors)"))
        val sleep = source("SleepHypopneaCard.kt")
        val missing = sleep.substringAfter("if (!compact && !assessment.isReady) {").substringBefore("Icon(")
        assertTrue(missing.contains("R.string.sleep_hypopnea_risk_title"))
        assertFalse(missing.contains("riskLabel(risk)"))
    }
}
