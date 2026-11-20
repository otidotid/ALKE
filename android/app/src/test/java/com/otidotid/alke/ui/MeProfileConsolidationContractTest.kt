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
    fun meStartsWithDevicesAndKeepsApprovedGroups() {
        val source = source("AlkeExperience.kt")

        val devices =
            source.indexOf("AlkeSectionTitle(\"Devices & Sources\")")
        val backup =
            source.indexOf("AlkeSectionTitle(\"Data & Backup\")")
        val app =
            source.indexOf("AlkeSectionTitle(\"App\")")

        assertTrue(
            devices >= 0 &&
                devices < backup &&
                backup < app
        )

        assertFalse(
            source.contains("AlkeSectionTitle(\"Profile\")")
        )

        assertFalse(source.contains("Change photo"))
        assertFalse(source.contains("Choose photo"))
        assertFalse(source.contains("Text(\"Remove\""))
    }
}
