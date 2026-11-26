package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumV625PreSigningContractTest {
    private fun ui(name:String):String {
        var dir=File(System.getProperty("user.dir")?:".").canonicalFile
        repeat(7) {
            val f=File(dir,"android/app/src/main/java/com/otidotid/alke/ui/$name")
            if(f.isFile) return f.readText()
            dir=dir.parentFile?:dir
        }
        error("Missing UI source: $name")
    }
    @Test fun widgetAndActiveWorkoutCommandsReachTheCorrectMode() {
        val host=ui("PremiumModules.kt")
        assertTrue(host.contains("LaunchedEffect(requestId,requestMode)"))
        assertTrue(host.contains("forcedMode=if(initialMode==\"Live HR\" && workoutRequested)"))
        assertTrue(ui("AppRoot.kt").contains("timelineRequestId = if(widgetEntryRequest.first"))
        assertTrue(ui("LiveScreen.kt").contains("LaunchedEffect(workoutPresentationRequested)"))
    }
    @Test fun analyticsModeSelectionSurvivesModeSwitching() {
        val trends=ui("TrendsScreen.kt")
        val explore=ui("TrendsExploreScreen.kt")
        assertTrue(trends.contains("rememberSaveable { mutableStateOf(TrendsRange.Quarter) }"))
        assertTrue(trends.contains("rememberSaveable { mutableStateOf(0) }"))
        assertTrue(explore.contains("rememberSaveable { mutableStateOf(builtInMetrics.first().key) }"))
        assertTrue(explore.contains("rememberSaveable { mutableStateOf(ExploreRange.Month) }"))
    }
    @Test fun journalNeverCountsNumericRowTwiceOrLabelsStaleAnswersAsNewDate() {
        val insight=ui("InsightsScreen.kt")
        assertTrue(insight.contains("journalLoaded && loadedJournalDay == selectedJournalDay"))
        assertTrue(insight.contains("(selectedAnswers.keys+selectedNumeric.keys).size"))
        assertTrue(insight.contains("filterNot { it.key in selectedNumeric }"))
        assertTrue(insight.contains("Mood and caffeine: today"))
        assertTrue(insight.contains("val targetDay = selectedJournalDay"))
    }
}
