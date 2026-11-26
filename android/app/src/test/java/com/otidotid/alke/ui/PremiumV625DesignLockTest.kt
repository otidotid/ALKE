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
            // v627 owner-approved daily Rest resolver and Charge contributor data wiring only.
            "TodayScreen.kt" to "23fa0a65baaec61083b81e0f6a03f0888fb80140",
            "HrEventVisuals.kt" to "d0ded42f01dddf9ad342384e7a6b25f908d8ad26")
        expected.forEach { (name,sha) ->
            assertEquals(name,sha,gitBlob(File(root(),"android/app/src/main/java/com/otidotid/alke/ui/$name")))
        }
        // v627: user approved only the daily Rest resolver change in Today and new Sleep Worker route.
        // Full WorkoutsScreen and HrEventVisuals hashes above remain unchanged.
        assertEquals("656aa13cb08f5da2121f31066d65ca34a3096a97",gitBlob(File(root(),"cloud-coach/worker.js")))
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
        val sleep=ui("SleepScreen.kt")
        assertTrue(sleep.contains("Sleep Details"))
        assertTrue(sleep.contains("TOTAL DAILY SLEEP"))
        assertTrue(sleep.contains("model?.selectedNightEfficiency"))
        assertTrue(sleep.contains("model?.selectedNightDebtMin"))
        assertTrue(ui("SleepModelLogic.kt").contains("localDebtByDay[effectiveDay]"))
        assertTrue(insight.contains("InsightSkinTemperatureDetails(vm)"))
        assertTrue(ui("HealthScreen.kt").contains("if (key == \"recovery\")"))
        assertTrue(ui("HealthScreen.kt").contains("HealthContributorsSection(today)"))
    }
}
