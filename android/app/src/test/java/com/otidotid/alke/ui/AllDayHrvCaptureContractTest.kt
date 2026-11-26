package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protect the new all-day HRV runtime path against legacy preferences and restored settings. */
class AllDayHrvCaptureContractTest {
    private fun source(relative:String):String {
        var dir=File(System.getProperty("user.dir")?:".").canonicalFile
        repeat(7) {
            val f=File(dir,"android/app/src/main/java/com/otidotid/alke/$relative")
            if(f.isFile) return f.readText()
            dir=dir.parentFile?:dir
        }
        error("Missing $relative")
    }
    @Test fun backgroundIntentCannotBeNarrowedByClockOrBattery() {
        val vm=source("ui/AppViewModel.kt")
        val ble=source("ble/WhoopBleClient.kt")
        assertTrue(vm.contains("private fun continuousHrvEffective(): Boolean =\n        AlkePrefs.backgroundConnection(appContext)"))
        assertTrue(ble.contains("private fun continuousCaptureWantsNow(): Boolean = keepStreamForData"))
        assertFalse(vm.contains("AlkePrefs.pauseHrvOnPowerSave(appContext)"))
        assertFalse(vm.contains("AlkePrefs.continuousHrv(appContext)"))
    }
    @Test fun settingsNoLongerOfferTheRetiredCaptureSwitches() {
        val settings=source("ui/SettingsScreen.kt")
        val power=source("ui/PowerSavingScreen.kt")
        assertFalse(settings.contains("checked = continuousHrv"))
        assertFalse(settings.contains("setContinuousHrvOvernight("))
        assertFalse(power.contains("checked = pauseHrvOnPowerSave"))
        assertTrue(settings.contains("Continuous HRV capture · 24/7"))
    }
    @Test fun legacyPreferenceValuesCannotLimitCapture() {
        val prefs=source("ui/MainActivity.kt")
        assertTrue(prefs.contains("fun continuousHrv(context: Context): Boolean =\n        true"))
        assertTrue(prefs.contains("fun continuousHrvOvernight(context: Context): Boolean =\n        false"))
        assertTrue(prefs.contains("fun pauseHrvOnPowerSave(context: Context): Boolean =\n        false"))
    }
}
