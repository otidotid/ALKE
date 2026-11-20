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

    @Test fun meStartsWithProfileAndKeepsAllApprovedGroups() {
        val source = source("AlkeExperience.kt")
        val profile = source.indexOf("AlkeSectionTitle(\"Profile\")")
        val devices = source.indexOf("AlkeSectionTitle(\"Devices & Sources\")")
        val backup = source.indexOf("AlkeSectionTitle(\"Data & Backup\")")
        val app = source.indexOf("AlkeSectionTitle(\"App\")")
        assertTrue(profile >= 0 && profile < devices && devices < backup && backup < app)
        assertTrue(source.contains("Choose photo"))
        assertTrue(source.contains("Change photo"))
        assertTrue(source.contains("ProfileAvatarStore.clearAvatar(context)"))
    }

    @Test fun fixedMeDockSlotUsesTheSharedProfileAvatarStore() {
        val source = source("AppRoot.kt")
        assertTrue(source.contains("useProfileAvatar = dest == Destination.More"))
        assertTrue(source.contains("ProfileAvatar("))
        assertTrue(source.contains("contentDescription = label"))
        assertTrue(source.contains("dest == Destination.More"))
    }
}
