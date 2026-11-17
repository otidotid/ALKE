package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * User smoke-test regression contract carried into ALKE alpha.6.
 * These checks complement compilation and full numerical parity tests; they
 * explicitly guard the intended navigation and no-fabricated-HR behavior.
 */
class AlkeAlpha5ExperienceContractTest {
    private fun source(path: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return listOf(
            File(cwd, path),
            File(cwd, "app/$path"),
            File(cwd, "android/app/$path"),
        ).firstOrNull { it.isFile }?.readText()
            ?: error("Missing ALKE source $path")
    }

    @Test fun todayShowsRecordedOrConnectedHeartRateNotAnInvented24HourSubtitle() {
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        assertTrue(ui.contains("private fun AlkeHeartRatePreview("))
        assertTrue(ui.contains("vm.repo.latestHr(activeId)"))
        assertTrue(ui.contains("live.heartRate?.takeIf"))
        assertTrue(ui.contains("if (live.connected)"))
        assertTrue(ui.contains("LAST RECORDED"))
        assertTrue(ui.contains("AWAITING HEART-RATE DATA"))
        assertTrue(ui.contains("AlkeHeartRatePreview(vm, onHeartRateTimeline)"))
        assertFalse(ui.contains("24-hour HR chart with workout markers and sleep periods"))
    }

    @Test fun todayStillNavigatesToOriginalDetailedTimeline() {
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("onHeartRateTimeline = { nav.navigate(Destination.AlkeHeartRateTimeline.route) }"))
        assertTrue(nav.contains("composable(Destination.AlkeHeartRateTimeline.route)"))
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        assertTrue(ui.contains("HeartRateTrendCard("))
        assertTrue(ui.contains("alkeStyle = true"))
    }

    @Test fun trainOnlyShowsFourRequestedPrimarySurfacesAndRetainsOriginalHistory() {
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        val begin = ui.indexOf("fun AlkeTrainingExperience(")
        val end = ui.indexOf("/** ALKE native presentation of the EXISTING", begin)
        assertTrue(begin > 0 && end > begin)
        val train = ui.substring(begin, end)
        assertTrue(train.contains("vm.workouts.collectAsStateWithLifecycle()"))
        assertTrue(train.contains("vm.loadWorkouts()"))
        assertTrue(train.contains("CALORIES"))
        assertTrue(train.contains("MOST ACTIVE SPORTS"))
        assertTrue(train.contains("Text(\"Add Workout\""))
        assertTrue(train.contains("AlkeActiveCaloriesCalendar(vm, days)"))
        assertTrue(train.contains("ManualWorkoutDialog(vm, editing = null"))
        assertTrue(train.contains("vm.saveManualWorkout(row, replacing)"))
        assertTrue(train.contains("Text(\"History\""))
        assertTrue(train.contains("onWorkouts"))
        assertFalse(train.contains("StartWorkoutSheet(vm"))
        assertFalse(train.contains("LiveWorkoutScreen(vm"))
        assertFalse(train.contains("Text(\"Sessions\""))
        assertFalse(train.contains("Show older sessions"))
        assertFalse(train.contains("selectedSession"))
        assertFalse(train.contains("AlkeLinkRow(\"Live HR\""))
        assertFalse(train.contains("AlkeLinkRow(\"Recovery\""))
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("composable(Destination.AlkeWorkoutHistory.route) { WorkoutsScreen(viewModel) }"))
        val history = source("src/main/java/com/otidotid/alke/ui/WorkoutsScreen.kt")
        assertTrue(history.contains("WorkoutStartSection(vm, onAdd = { dialog = DialogTarget(null) })"))
        assertTrue(history.contains("WorkoutDetailSheet("))
    }

    @Test fun todayActivityTilesAreDayStrainStepsActiveTimeInOrder() {
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        val today = ui.substring(ui.indexOf("fun AlkeTodayExperience("),
            ui.indexOf("fun AlkeTrainingExperience("))
        assertTrue(today.contains("AlkeSectionTitle(\"Today's Activity\")"))
        assertFalse(today.contains("AlkeSectionTitle(\"Today's Activity\", \"Train\""))
        val activity = today.substring(today.indexOf("AlkeSectionTitle(\"Today's Activity\")"),
            today.indexOf("AlkeHeartRatePreview(vm, onHeartRateTimeline)"))
        val strainPos = activity.indexOf("AlkeMetricTile(\"Day Strain\"")
        val stepsPos = activity.indexOf("AlkeMetricTile(\"Steps\"")
        val timePos = activity.indexOf("AlkeMetricTile(\"Active Time\"")
        assertTrue(strainPos >= 0 && stepsPos > strainPos && timePos > stepsPos)
        assertTrue(today.contains("buildSeriesVitalDetail(vm, \"steps_est\")"))
        assertTrue(today.contains("it.day == LocalDate.now().toString()"))
        assertTrue(today.contains("A.cyan, onSteps"))
        assertFalse(today.contains("Text(\"Skin Temperature\""))
        val progress = ui.substring(ui.indexOf("fun AlkeProgressExperience("),
            ui.indexOf("fun AlkeInsightExperience("))
        assertTrue(progress.contains("buildSeriesVitalDetail(vm, \"steps_est\")"))
        assertTrue(progress.contains("onClick = onSteps"))
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("onSteps = { nav.navigate(\"vital_detail/steps_est\") }"))
        val health = source("src/main/java/com/otidotid/alke/ui/HealthScreen.kt")
        assertTrue(health.contains("mergeStepsReadings(real, imported, est)"))
    }

    @Test fun skinTemperatureLivesInInsightBelowRestingHrAndKeepsOriginalHistory() {
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        val today = ui.substring(ui.indexOf("fun AlkeTodayExperience("),
            ui.indexOf("fun AlkeTrainingExperience("))
        assertFalse(today.contains("Text(\"Skin Temperature\""))
        assertFalse(today.contains("onMetric(\"skin\")"))
        val insight = ui.substring(ui.indexOf("fun AlkeInsightExperience("),
            ui.indexOf("ALKE-exclusive Heart Rate Timeline"))
        val rest = insight.indexOf("Triple(\"Resting HR\"")
        val skin = insight.indexOf("Text(\"Skin Temperature\"")
        assertTrue(rest >= 0 && skin > rest)
        assertTrue(insight.contains("onClick = onSkinTemp"))
        assertTrue(insight.contains("absoluteSkinC"))
        assertTrue(insight.contains("deviationSkinC"))
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("onSkinTemp = { nav.navigate(\"vital_detail/skin\") }"))
        val health = source("src/main/java/com/otidotid/alke/ui/HealthScreen.kt")
        assertTrue(health.contains("SkinTempSuiteSection("))
    }

    @Test fun calendarCombinesImportedCaloriesAndOriginalHeatmapCore() {
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        assertTrue(ui.contains("vm.repo.appleDaily(\"health-connect\", from, today)"))
        assertTrue(ui.contains("vm.repo.appleDaily(\"apple-health\", from, today)"))
        assertTrue(ui.contains("byDay.putAll(imported)"))
        assertTrue(ui.contains("ActivityHeatmap.build(values, today)"))
    }

    @Test fun fullAndDemoStaySeparateWithExistingSyntheticData() {
        val build = source("build.gradle.kts")
        assertTrue(build.contains("versionName = \"1.0.0-alpha.7\""))
        assertTrue(build.contains("applicationIdSuffix = \".demo\""))
        assertTrue(build.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"false\")"))
        assertTrue(build.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"true\")"))
        val demo = source("src/main/java/com/otidotid/alke/data/DemoSeeder.kt")
        assertTrue(demo.contains("private const val DAYS = 120"))
        assertTrue(demo.contains("seedRecentHeartRateIfMissing(repo)"))
    }
}
