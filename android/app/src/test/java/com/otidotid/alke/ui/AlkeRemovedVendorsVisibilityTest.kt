package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ALKE regression: removed vendor UI must not return during a rebrand. */
class AlkeRemovedVendorsVisibilityTest {
    private fun source(path: String): String {
        val base = File(System.getProperty("user.dir") ?: ".")
        return listOf(File(base, path), File(base, "app/$path"), File(base, "android/app/$path"))
            .firstOrNull { it.isFile }?.readText() ?: error("Missing source $path")
    }

    @Test fun deprecatedPolarIsNotSelectableFromGenericBluetoothPicker() {
        val picker = source("src/main/java/com/otidotid/alke/ui/AddDeviceWizard.kt")
        assertTrue(picker.contains(
            "val visibleStraps = discovered.filterNot { com.otidotid.alke.polar.PolarModel.isPolar(it.name) }"
        ))
        assertTrue(picker.contains("isEmpty = visibleStraps.isEmpty()"))
        assertTrue(picker.contains("visibleStraps.sortedByDescending { it.rssi }"))
        // Keep the BLE driver and the device registry - hiding a device does
        // not authorize deleting historical readings or changing protocols.
        val device = source("src/main/java/com/otidotid/alke/ui/DevicesScreen.kt")
        assertTrue(device.contains("alkeHiddenDeviceVendor(device)"))
        assertTrue(device.contains("Heart-rate strap"))
    }

    @Test fun removedAppleHealthImporterIsStillAbsentFromUi() {
        val root = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertFalse(root.contains("AppleHealthScreen("))
        val dataSources = source("src/main/java/com/otidotid/alke/ui/DataSourcesScreen.kt")
        assertFalse(dataSources.contains("Wahoo, Polar, Strava, Apple)"))
        assertTrue(dataSources.contains("Wahoo and Strava)"))
        val app = File(System.getProperty("user.dir") ?: ".")
        val paths = listOf(
            "src/main/java/com/otidotid/alke/ui/AppleHealthScreen.kt",
            "src/main/java/com/otidotid/alke/data/AppleHealthImporter.kt",
        )
        for (path in paths) {
            assertFalse("Apple Health importer must not reappear: $path",
                listOf(File(app,path),File(app,"app/$path"),File(app,"android/app/$path")).any { it.exists() })
        }
    }
}
