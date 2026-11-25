package com.otidotid.alke.ui

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class PremiumV625DesignLockTest {
    private fun root():File {
        var f=File(System.getProperty("user.dir")?:".").canonicalFile
        repeat(7) {
            if(File(f,"android/app/src/main/java/com/otidotid/alke/ui/AppRoot.kt").exists()) return f
            f=f.parentFile?:f
        }
        error("Repository root not found")
    }
    private fun ui(name:String)=File(root(),"android/app/src/main/java/com/otidotid/alke/ui/$name").readText()
    private fun gitBlob(file:File):String {
        val bytes=file.readBytes()
        val digest=MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    @Test fun approvedV624SurfacesRemainByteIdentical() {
        val expected=mapOf("WorkoutsScreen.kt" to "60e0b648ca8a93b774eb934e020a32dfca8bc650",
            "TodayScreen.kt" to "08b1abd4adb3e53a36a364726a07c0de5bebc17c",
            "HrEventVisuals.kt" to "d0ded42f01dddf9ad342384e7a6b25f908d8ad26")
        expected.forEach { (name,sha) ->
            assertEquals(name,sha,gitBlob(File(root(),"android/app/src/main/java/com/otidotid/alke/ui/$name")))
        }
        assertEquals("cc895c51ac5a375f8cf06135463d4a52f1e694fd",gitBlob(File(root(),"cloud-coach/worker.js")))
    }
    @Test fun consolidatedRoutesAndExistingPinIds() {
        val nav=ui("AppRoot.kt")
        assertTrue(nav.contains("onSleep = { nav.navigate(Destination.Sleep.route) }"))
        assertTrue(nav.contains("PremiumTrendsStudio(viewModel, \"Trends\")"))
        assertTrue(nav.contains("PremiumStressBreathe(viewModel, initialMode = \"Stress\")"))
        assertTrue(nav.contains("PremiumHeartRateCenter(viewModel, initialMode = \"Timeline\""))
        val pins=listOf(Destination.Health,Destination.Explore,Destination.Live)
        assertEquals(pins,BottomBarModuleStore.decode(BottomBarModuleStore.encode(pins)))
    }
    @Test fun realDataSummaryAndJournalOrder() {
        val experience=ui("AlkeExperience.kt")
        assertTrue(experience.contains("history = ageHistory.takeLast(12).map { it.fitnessAge }"))
        val insight=ui("InsightsScreen.kt")
        val keys=listOf("ConsolidatedInsightSignals(insightDay)","Text(\"Daily Journal\"","Text(\"Personal Analytics\"","ConsolidatedInsightAiSummary(aiSummary")
        val indexes=keys.map { insight.indexOf(it) }
        assertTrue(indexes.all { it>=0 } && indexes.zipWithNext().all { it.first<it.second })
        assertTrue(ui("SleepScreen.kt").contains("Sleep Balance"))
    }
}
