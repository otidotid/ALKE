package com.otidotid.alke.multidevice.huawei

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HuaweiRecoveryArchiveTest {
    @Test fun encryptedRoundTripMergesMissingRowsWithoutReplacingExisting() {
        val context = RuntimeEnvironment.getApplication()
        val device = HuaweiFitnessStore.deviceKey("AA:BB:CC:DD:EE:FF")
        val password = "test-only-passphrase".toCharArray()
        val encrypted = File(context.cacheDir,"huawei-test.alkeenc")
        try {
            HuaweiFitnessStore(context).use {
                it.saveMinutes(device,60,180,listOf(HuaweiFitnessRead.Minute(60,7,70,null),
                    HuaweiFitnessRead.Minute(120,8,75,98)))
            }
            HuaweiRecoveryArchive.export(context,encrypted,password)
            HuaweiFitnessStore(context).use {
                it.writableDatabase.delete("minute","ts=?",arrayOf("120"))
                it.saveMinutes(device,60,120,listOf(HuaweiFitnessRead.Minute(60,9,72,null)))
            }
            assertEquals(1L,HuaweiRecoveryArchive.restoreMissing(context,encrypted,password))
            assertEquals(0L,HuaweiRecoveryArchive.restoreMissing(context,encrypted,password))
            HuaweiFitnessStore(context).use {
                assertEquals(listOf(HuaweiFitnessRead.Minute(60,9,72,null),
                    HuaweiFitnessRead.Minute(120,8,75,98)),it.minutes(device,60,180))
            }
        } finally { encrypted.delete(); password.fill('\u0000') }
    }
}
