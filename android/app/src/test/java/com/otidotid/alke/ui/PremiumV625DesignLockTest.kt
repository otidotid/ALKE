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
        return gitBlobBytes(file.readBytes())
    }
    private fun gitBlobBytes(bytes:ByteArray):String {
        val digest=MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    @Test fun approvedV624SurfacesRemainByteIdentical() {
        val expected=mapOf("WorkoutsScreen.kt" to "60e0b648ca8a93b774eb934e020a32dfca8bc650",
            // v646 native Huawei HR timeline data selection and source disclosure only;
            // the original v627 surface is independently locked below.
            "TodayScreen.kt" to "56b345ed4044a7aa1cde96a440cd751ddd1b8433",
            "HrEventVisuals.kt" to "d0ded42f01dddf9ad342384e7a6b25f908d8ad26")
        expected.forEach { (name,sha) ->
            // v662: reverse only approved data/source-label edits before comparing the locked blobs.
            val current=ui(name)
            val prior=when(name) {
                "TodayScreen.kt" -> undoHuaweiWorkout(current)
                "WorkoutsScreen.kt" -> current.replace(
                    "    row.source == com.otidotid.alke.multidevice.huawei.HuaweiWorkoutIntegration.SOURCE -> \"Huawei GT5\"\n","")
                    .replace("        src == com.otidotid.alke.multidevice.huawei.HuaweiWorkoutIntegration.SOURCE -> \"GT5\"\n","")
                else -> current
            }
            assertEquals(name,sha,gitBlobBytes(prior.toByteArray(Charsets.UTF_8)))
        }
        // v627: user approved only the daily Rest resolver change in Today and new Sleep Worker route.
        // Full WorkoutsScreen and HrEventVisuals hashes above remain unchanged.
        // v629: owner-approved post-sync cloud activity evidence and classification update.
        // Pin the reviewed Worker blob; workouts and event visuals remain byte-identical.
        assertEquals("5a34dda12c4220283c026b8b46176d78081c634b",gitBlob(File(root(),"cloud-coach/worker.js")))
    }
    private fun undoHuaweiWorkout(source: String): String = source.replace(
        "            val canonical = com.otidotid.alke.multidevice.huawei.HuaweiWorkoutIntegration.merge(\n" +
            "                timelineContext,WorkoutEditing.dedupCrossSource(workouts),correctionFrom,correctionTo)",
        "            val canonical = WorkoutEditing.dedupCrossSource(workouts)")

    @Test fun huaweiTimelineOnlyChangesReviewedDataWiringAndSourceLabel() {
        // Do not simply bless a new TodayScreen hash: reverse exactly the four
        // reviewed edits, and require the entire rest of the file to match v627.
        var original = undoHuaweiWorkout(ui("TodayScreen.kt"))
        fun undo(added: String, prior: String) {
            assertEquals("Expected exactly one reviewed edit", 1, original.split(added).size - 1)
            original = original.replace(added, prior)
        }
        undo(
            "    var huaweiBucketStarts by remember { mutableStateOf<Set<Long>>(emptySet()) }\n" +
                "    val timelineContext = LocalContext.current\n" +
                "    val huaweiRevision by com.otidotid.alke.multidevice.huawei.HuaweiTimelineSource.revision.collectAsStateWithLifecycle()\n",
            "",
        )
        undo(
            "dayCycleMode, activityRevision, if (alkeStyle) huaweiRevision else 0L) {",
            "dayCycleMode, activityRevision) {",
        )
        undo(
            """        if (alkeStyle) {
            val timeline = com.otidotid.alke.multidevice.huawei.HuaweiTimelineSource.read(
                timelineContext, viewModel.repo, viewModel.activeStrapId, start, end, HR_CARD_BUCKET_SECONDS)
            buckets = timeline.buckets
            huaweiBucketStarts = timeline.huaweiBucketStarts
        } else {
            buckets = viewModel.repo.hrBucketsUnion(
                viewModel.activeStrapId, start, end, HR_CARD_BUCKET_SECONDS,
            )
            huaweiBucketStarts = emptySet()
        }""",
            """        buckets = viewModel.repo.hrBucketsUnion(
            viewModel.activeStrapId, start, end, HR_CARD_BUCKET_SECONDS,
        )""",
        )
        undo(
            "                        if (winBuckets.any { it.bucket in huaweiBucketStarts })\n" +
                "                            \"\$subtitle · Huawei priority / WHOOP fallback (minute data)\" else subtitle,",
            "                        subtitle,",
        )
        assertEquals("Unchanged approved Today layout and chart", "23fa0a65baaec61083b81e0f6a03f0888fb80140",
            gitBlobBytes(original.toByteArray(Charsets.UTF_8)))
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
