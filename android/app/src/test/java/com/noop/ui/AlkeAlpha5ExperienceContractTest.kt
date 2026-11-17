package com.noop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * User smoke-test regression contract for ALKE alpha.5.
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
        val ui = source("src/main/java/com/noop/ui/AlkeExperience.kt")
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
        val nav = source("src/main/java/com/noop/ui/AppRoot.kt")
        assertTrue(nav.contains("onHeartRateTimeline = { nav.navigate(Destination.AlkeHeartRateTimeline.route) }"))
        assertTrue(nav.contains("composable(Destination.AlkeHeartRateTimeline.route)"))
        val ui = source("src/main/java/com/noop/ui/AlkeExperience.kt")
        assertTrue(ui.contains("HeartRateTrendCard("))
        assertTrue(ui.contains("alkeStyle = true"))
    }

    @Test fun trainEmbedsActualAllSourceWorkoutsWithoutRedundantLiveOrRecoveryModules() {
        val ui = source("src/main/java/com/noop/ui/AlkeExperience.kt")
        val begin = ui.indexOf("fun AlkeTrainingExperience(")
        val end = ui.indexOf("\n@Composable\nprivate fun AlkeMiniBars(", begin)
        assertTrue(begin > 0 && end > begin)
        val train = ui.substring(begin, end)
        assertTrue(train.contains("vm.workouts.collectAsStateWithLifecycle()"))
        assertTrue(train.contains("vm.loadWorkouts()"))
        assertTrue(train.contains("sortedByDescending { it.startTs }"))
        assertTrue(train.contains("selectedSession = row"))
        assertTrue(train.contains("WorkoutDetailSheet(vm, row)"))
        assertTrue(train.contains("Show older sessions"))
        assertFalse(train.contains("AlkeLinkRow(\"Live HR\""))
        assertFalse(train.contains("AlkeLinkRow(\"Recovery\""))
        assertFalse(train.contains("All workouts"))
        assertTrue(train.contains("onWorkouts"))
        val nav = source("src/main/java/com/noop/ui/AppRoot.kt")
        assertTrue(nav.contains("composable(Destination.AlkeWorkoutHistory.route) { WorkoutsScreen(viewModel) }"))
    }

    @Test fun fullAndDemoStaySeparateWithExistingSyntheticData() {
        val build = source("build.gradle.kts")
        assertTrue(build.contains("versionName = \"1.0.0-alpha.5\""))
        assertTrue(build.contains("applicationIdSuffix = \".demo\""))
        assertTrue(build.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"false\")"))
        assertTrue(build.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"true\")"))
        val demo = source("src/main/java/com/noop/data/DemoSeeder.kt")
        assertTrue(demo.contains("private const val DAYS = 120"))
        assertTrue(demo.contains("seedRecentHeartRateIfMissing(repo)"))
    }
}
