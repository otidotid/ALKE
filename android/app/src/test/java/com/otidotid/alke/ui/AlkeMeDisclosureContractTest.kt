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

    @Test fun moreToolsAndSettingsUseTheSameIconAccordion() {
        val source = experience()
        val me = source.substringAfter("fun AlkeMeExperience(")
            .substringBefore("/** The real ALKE dock editor")
        assertTrue(me.contains("AlkeMeDisclosureHeader(\n                title = \"More Tools\""))
        assertTrue(me.contains("icon = Icons.Filled.Apps"))
        assertTrue(me.contains("AlkeMeDisclosureHeader(\n                title = \"Settings\""))
        assertTrue(me.contains("icon = Icons.Filled.Settings"))
        assertTrue(me.contains("expanded = moreToolsExpanded"))
        assertTrue(me.contains("expanded = personalSettingsExpanded"))
        assertTrue(me.contains("showSettingsBody = personalSettingsExpanded"))
        assertTrue(me.contains("AlkeLinkRow(\"All Modules\""))
        assertTrue(me.contains("AlkeLinkRow(\"Notifications\""))
        assertFalse(me.contains("AlkeExpandableSection(\n                \"More tools\""))
        assertFalse(me.contains("AlkeLinkRow(\n                \"Settings\""))
    }

    @Test fun sharedAccordionMatchesMeLinkRowIconAndCardDimensions() {
        val source = experience()
        val header = source.substringAfter("private fun AlkeMeDisclosureHeader(")
            .substringBefore("private fun AlkeLinkRow(")
        assertTrue(header.contains("Modifier.size(43.dp)"))
        assertTrue(header.contains("RoundedCornerShape(17.dp)"))
        assertTrue(header.contains("stateDescription = if (expanded)"))
        assertTrue(header.contains("onToggle"))
    }
}
