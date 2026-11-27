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
    @Test fun meStartsWithProfileAndKeepsApprovedPremiumGroups() {
        val me = source("AlkeExperience.kt").substringAfter("fun AlkeMeExperience(")
            .substringBefore("fun AlkeMeGroupExperience(")
        val profile = me.indexOf("Your Profile")
        val wearable = me.indexOf("Wearable")
        val preferences = me.indexOf("Health & Fitness")
        assertTrue(profile >= 0 && profile < wearable && wearable < preferences)
        assertTrue(me.contains("onGroup(MeGroup.PROFILE)"))
        assertTrue(me.contains("onGroup(MeGroup.DATA)"))
        assertFalse(me.contains("Smart Settings"))
        assertFalse(me.contains("Change photo"))
        assertFalse(me.contains("Choose photo"))
        val nav = source("AppRoot.kt")
        assertTrue(nav.contains("nav.navigate(MeGroup.PROFILE.route)"))
    }

    @Test fun meGroupsPreserveCanonicalControlsAndSleepCardIdentifiesRiskWithoutEvidence() {
        val experience = source("AlkeExperience.kt")
        val groups = experience.substringAfter("fun AlkeMeGroupExperience(").substringBefore("fun MeDockSettings(")
        for (target in listOf("MeSettingsPage.PROFILE", "MeSettingsPage.ACCOUNT", "Destination.BackupSync",
            "Destination.Devices", "Destination.DataSources", "MeSettingsPage.ACTIVITY",
            "MeSettingsPage.SLEEP", "MeSettingsPage.DOCK")) {
            assertTrue("Missing route $target", groups.contains(target))
        }
        val account = source("GoogleAccountCard.kt")
        assertTrue(account.contains("accountOnly"))
        assertTrue(account.contains("backupOnly"))
        val premium = source("PremiumModules.kt")
        assertTrue(premium.contains("Icon(icon, contentDescription=null"))
        assertTrue(premium.contains("PremiumLink(\"Wearable devices\",status,onClick, icon = Icons.Filled.Sensors)"))
        val sleep = source("SleepHypopneaCard.kt")
        val missing = sleep.substringAfter("if (!compact && !assessment.isReady) {").substringBefore("Icon(")
        assertTrue(missing.contains("R.string.sleep_hypopnea_risk_title"))
        assertFalse(missing.contains("riskLabel(risk)"))
    }
}
