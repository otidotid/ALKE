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

    @Test fun trainUnifiesWorkoutActionsBreakdownAndSessionsWithoutHistoryRoute() {
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
        assertTrue(train.contains("Text(\"Start Workout\""))
        assertTrue(train.contains("AlkeTrainWorkoutActions(vm, onAdd = { showAddDialog = true })"))
        val calendar = train.indexOf("AlkeActiveCaloriesCalendar(vm, days)")
        val sections = train.indexOf("AlkeTrainHistorySections(vm)")
        assertTrue("Activity Breakdown and All Sessions must be placed below Active Calories",
            calendar >= 0 && sections > calendar)
        assertTrue(train.contains("StartWorkoutSheet(vm = vm"))
        assertTrue(train.contains("WorkoutStartSection(vm, onAdd = onAdd)"))
        assertTrue(train.contains("ManualWorkoutDialog(vm, editing = null"))
        assertTrue(train.contains("vm.saveManualWorkout(row, replacing)"))
        assertFalse(train.contains("Text(\"History\""))
        assertFalse(train.contains("onWorkouts: () -> Unit"))
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertFalse(nav.contains("Destination.AlkeWorkoutHistory"))
        assertTrue(nav.contains("composable(Destination.Workouts.route)"))
        val original = source("src/main/java/com/otidotid/alke/ui/WorkoutsScreen.kt")
        assertTrue(original.contains("fun AlkeTrainHistorySections("))
        assertTrue(original.contains("BreakdownSection(groups = sportGroups(allRows), rows = allRows)"))
        assertTrue(original.contains("SessionsSection("))
        assertTrue(original.contains("WorkoutDetailSheet("))
        for (operation in listOf("vm.mergeWorkouts(", "vm.bulkDeleteWorkouts(", "vm.relabelDetected(",
                "vm.dismissDetected(", "vm.deleteWorkout(", "ManualWorkoutDialog(")) {
            assertTrue("Workout action must survive inline move: $operation", original.contains(operation))
        }
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

    @Test fun progressStartsWithFitnessAgeAndContinuousPremiumMotion() {
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        val progress = ui.substring(ui.indexOf("fun AlkeProgressExperience("),
            ui.indexOf("fun AlkeInsightExperience("))
        val fitnessPos = progress.indexOf("AlkeFitnessAgeHero(")
        val vo2Pos = progress.indexOf("onClick = onVo2")
        val bodyAgePos = progress.indexOf("onClick = onBodyAge")
        assertTrue("Fitness Age must be the first Progress metric", fitnessPos >= 0 && vo2Pos > fitnessPos)
        assertTrue("Body Age must remain accessible below", bodyAgePos > fitnessPos)
        assertTrue(progress.contains("vm.repo.latestMetricComputedUnion(vm.activeStrapId, \"fitness_age\")"))
        assertTrue(progress.contains("onClick = onFitnessAge"))
        assertTrue(ui.contains("rememberReduceMotion()"))
        assertTrue(ui.contains("animateFloatAsState("))
        assertTrue(ui.contains("fitnessAgeBoundSymbol(it)"))
        assertTrue(ui.contains("AGE SCALE 20–80"))
        val hero = ui.substring(ui.indexOf("private fun AlkeFitnessAgeHero("),
            ui.indexOf("fun AlkeProgressExperience("))
        assertFalse("Fitness Age hero must not contain a menu chevron", hero.contains("ChevronRight"))
        assertTrue("Fitness Age hero must breathe continuously", hero.contains("rememberInfiniteTransition"))
        assertTrue("Age number must have subtle continuous scale", hero.contains("alke_fitness_age_breathe"))
        assertTrue("Age scale position must stay grounded in recorded data", hero.contains("270f * animatedPosition"))
        assertTrue("All factor cards must glow continuously", hero.contains("alke_factor_${index}_glow"))
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("onFitnessAge = { nav.navigate(\"vital_detail/fitness_age\") }"))
        val detailed = source("src/main/java/com/otidotid/alke/ui/HealthScreen.kt")
        assertTrue(detailed.contains("FitnessAgeDetailExperience("))
        assertTrue(detailed.contains("metricSeriesComputedUnion(vm.activeStrapId, \"fitness_age\""))
    }

    @Test fun healthModuleIsFitnessAgeWithHeroAndAnimatedFourFactors() {
        val nav = source("src/main/java/com/otidotid/alke/ui/AppRoot.kt")
        assertTrue(nav.contains("Destination.Health -> \"Fitness Age\""))
        assertTrue(nav.contains("Text(titleOverride ?: alkeModuleLabel(dest)"))
        assertTrue(nav.contains("onRestingHr = { nav.navigate(\"vital_detail/rhr\") }"))
        val ui = source("src/main/java/com/otidotid/alke/ui/AlkeExperience.kt")
        val progress = ui.substring(ui.indexOf("fun AlkeProgressExperience("),
            ui.indexOf("fun AlkeInsightExperience("))
        assertTrue(progress.indexOf("AlkeFitnessAgeHero(") < progress.indexOf("AlkeSectionTitle(\"Fitness Factors\")"))
        assertTrue(progress.indexOf("AlkeSectionTitle(\"Fitness Factors\")") < progress.indexOf("onClick = onBodyAge"))
        for (factor in listOf("VO₂ MAX", "RESTING HR", "WEEKLY ACTIVITY", "METABOLIC")) {
            assertTrue("Missing animated Progress factor: $factor", progress.contains("label = \"$factor\""))
        }
        assertTrue(ui.contains("private fun AlkeFitnessFactorCard("))
        assertTrue(ui.contains("modifier.staggeredAppear(index)"))
        assertTrue(ui.contains("animateFloatAsState("))
        assertTrue(ui.contains("currentFitnessAgeMetabolicFactor("))
        assertTrue(ui.contains("No eligible inputs yet"))
        val module = source("src/main/java/com/otidotid/alke/ui/HealthScreen.kt")
        assertTrue(module.contains("title = \"Fitness Age\""))
        val first = module.indexOf("item { FitnessAgeSection(vm = vm, days = days, profile = profile")
        val sync = module.indexOf("item { SyncStatusSection(vm = vm")
        assertTrue("Fitness Age should appear before synchronisation/vitals", first >= 0 && sync > first)
        assertTrue(module.contains("label = \"METABOLIC\""))
        assertTrue(module.contains("value = \"Not available\""))
        assertTrue(module.contains("withMetabolicCalculationTransaction {"))
        assertTrue(module.contains("fitness_age_detail_factor_"))
        assertTrue(module.contains("fitness_age_detail_factor_${index}_ambient"))
        assertTrue(module.contains("fitness_age_factor_${index}_ambient"))
        assertTrue(module.contains("alke_health_fitness_age_breathe"))
        assertTrue(module.contains("reduceAgeMotion"))
        assertTrue(module.contains("fitness_age_factor_"))
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
        assertTrue(build.contains("versionName = \"1.0.0-alpha.13\""))
        assertTrue(build.contains("applicationIdSuffix = \".demo\""))
        assertTrue(build.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"false\")"))
        assertTrue(build.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"true\")"))
        val demo = source("src/main/java/com/otidotid/alke/data/DemoSeeder.kt")
        assertTrue(demo.contains("private const val DAYS = 120"))
        assertTrue(demo.contains("seedRecentHeartRateIfMissing(repo)"))
    }
}
