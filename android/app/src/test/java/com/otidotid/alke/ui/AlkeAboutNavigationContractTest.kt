package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The About page is compact; education logic and legal notices remain intact. */
class AlkeAboutNavigationContractTest {
    private fun read(relative: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return listOf(
            File(cwd, "src/main/java/com/otidotid/alke/ui/$relative"),
            File(cwd, "app/src/main/java/com/otidotid/alke/ui/$relative"),
            File(cwd, "android/app/src/main/java/com/otidotid/alke/ui/$relative"),
        ).firstOrNull { it.isFile }?.readText()
            ?: error("Source is missing: $relative")
    }

    @Test fun aboutHidesBothEducationEntriesWithoutRemovingTheirImplementations() {
        val settings = read("SettingsScreen.kt")
        val aboutStart = settings.indexOf("// --- About ---")
        val aboutEnd = settings.indexOf("// What's new sheet", aboutStart)
        assertTrue(aboutStart >= 0 && aboutEnd > aboutStart)
        val about = settings.substring(aboutStart, aboutEnd)
        assertFalse(about.contains("How your scores work"))
        assertFalse(about.contains("How ALKE works"))
        assertFalse(about.contains("showScoringGuide"))
        assertFalse(about.contains("showHowAlkeWorks"))
        assertTrue(about.contains("showLegalNotice"))
        assertTrue(settings.contains("if (showWhatsNew)"))
        assertTrue(File("src/main/java/com/otidotid/alke/ui/ScoringGuideScreen.kt").exists() ||
                   File("app/src/main/java/com/otidotid/alke/ui/ScoringGuideScreen.kt").exists())
        assertTrue(File("src/main/java/com/otidotid/alke/ui/HowAlkeWorksScreen.kt").exists() ||
                   File("app/src/main/java/com/otidotid/alke/ui/HowAlkeWorksScreen.kt").exists())
    }
}
