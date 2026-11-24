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
        assertTrue(source.contains("R.drawable.alke_header_wearable_outline"))
        assertTrue(source.contains("if (live.connected)"))
        assertTrue(source.contains("ringBattery"))
        assertTrue(source.contains("live.batteryPct"))
        assertTrue(source.contains("battery?.let {") && source.contains("?: \"—\""))
        assertTrue(source.contains("battery <= 20"))
        assertTrue(source.contains("stateDescription"))
        assertFalse(source.contains("BatteryChargingFull"))
        assertFalse(source.contains("persistent-battery-breathe"))
        val icon = File(androidRoot(), "app/src/main/res/drawable/alke_header_wearable_outline.xml")
        assertTrue(icon.isFile)
    }
}
