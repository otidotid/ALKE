package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Protect the approved v620 global chrome without changing any health algorithm. */
class AlkePremiumHeaderContractTest {
    private fun androidRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(6) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Android root not found")
    }

    private fun screen(): String =
        File(androidRoot(), "app/src/main/java/com/otidotid/alke/ui/AlkeExperience.kt").readText()

    @Test fun headerIsSingleGlobalSurfaceAcrossAllModules() {
        val root = File(androidRoot(), "app/src/main/java/com/otidotid/alke/ui/AppRoot.kt").readText()
        assertTrue(root.contains("AlkeGlobalHeader("))
        assertTrue(root.contains("topBar = {"))
        assertTrue(screen().contains("internal fun AlkeGlobalHeader("))
    }

    @Test fun logoIsMinimalVectorWithLiveSyncHalo() {
        val source = screen().substringAfter("private fun AlkeHeader(").substringBefore("private fun AlkeHeading(")
        assertTrue(source.contains("R.drawable.alke_header_minimal_mark"))
        assertTrue(source.contains("if (syncing)"))
        assertTrue(source.contains("alke-global-sync"))
        assertTrue(source.contains("showSyncHub = true"))
        assertFalse(source.contains("\"SYNCING DEVICE\""))
        assertFalse(source.contains("\"THE INTELLIGENCE WITHIN\""))
        val mark = File(androidRoot(), "app/src/main/res/drawable/alke_header_minimal_mark.xml")
        assertTrue(mark.isFile)
        assertTrue(mark.readText().contains("#26DEEB"))
    }

    @Test fun wearableChipUsesFreshDeviceBatteryWithoutSecondShortcut() {
        val source = screen().substringAfter("private fun AlkeHeader(").substringBefore("private fun AlkeHeading(")
        assertTrue(source.contains("AlkeBatteryGlyph("))
        assertFalse(source.contains("R.drawable.alke_header_wearable_outline"))
        assertTrue(source.contains("if (live.connected)"))
        assertTrue(source.contains("ringBattery"))
        assertTrue(source.contains("live.batteryPct"))
        assertTrue(source.contains("battery?.let {") && source.contains("?: \"—\""))
        assertTrue(source.contains("AlkeBatteryGlyph("))
        assertTrue(source.contains("stateDescription"))
        assertFalse(source.contains("BatteryChargingFull"))
        assertFalse(source.contains("persistent-battery-breathe"))
        val glyph = File(androidRoot(), "app/src/main/java/com/otidotid/alke/ui/AlkeBatteryGlyph.kt").readText()
        assertTrue(glyph.contains("0xFF26DEEB"))
        assertTrue(glyph.contains("battery <= 20"))
        assertTrue(glyph.contains("24f*x*fraction"))
    }
    @Test fun brandMarkIsConsistentInSplashAndLauncher() {
        val res = File(androidRoot(), "app/src/main/res")
        val paths = listOf("M18,78 L48,21", "M56,47 L80,78")
        listOf("alke_header_minimal_mark", "alke_brand_mark", "ic_launcher_foreground",
            "ic_launcher_monochrome", "ic_launcher_metal", "ic_launcher_metal_navy").forEach { name ->
            val vector = File(res, "drawable/$name.xml").readText()
            paths.forEach { assertTrue("$name must use the canonical mark", vector.contains(it)) }
        }
        assertTrue(File(res, "values/alke_brand.xml").readText().contains("Your Strength and Rest Companion"))
        assertTrue(File(res, "values-v31/themes.xml").readText().contains("@drawable/ic_launcher_foreground"))
        val ui = File(androidRoot(), "app/src/main/java/com/otidotid/alke/ui")
        assertTrue(File(ui, "AlkeBrandLaunch.kt").readText().contains("R.string.alke_brand_tagline"))
        assertTrue(File(ui, "SettingsScreen.kt").readText().contains("R.string.alke_brand_tagline"))
        assertTrue(File(ui, "TodayScreen.kt").readText().contains("AlkeBatteryGlyph("))
    }
}
