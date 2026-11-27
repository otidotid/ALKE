package com.otidotid.alke.ai

import org.junit.Assert.*
import org.junit.Test
import com.otidotid.alke.data.HrSample
import com.otidotid.alke.data.GravitySample
import com.otidotid.alke.data.StepSample
import java.io.File

class CloudActivityContractTest {
    @Test fun retryWindowsAreBoundedAndPersistable() {
        val start=900L
        val now=1800L
        var item=ActivityRetryPolicy.fail(null,start,now)
        assertEquals(2700L,item.nextAt)
        assertNull(ActivityRetryPolicy.due(listOf(item),now,now))
        assertEquals(item,ActivityRetryPolicy.due(listOf(item),2700L,2700L))
        repeat(5) { item=ActivityRetryPolicy.fail(item,start,now) }
        assertEquals(ActivityRetryPolicy.MAX_FAILURES,item.failures)
        assertNull(ActivityRetryPolicy.due(listOf(item),now,now))
        assertEquals(listOf(item),ActivityRetryPolicy.decode(ActivityRetryPolicy.encode(listOf(item)),now))
        assertTrue(ActivityRetryPolicy.decode(ActivityRetryPolicy.encode(listOf(item)),now+7*86400L).isEmpty())
        assertEquals(now,ActivityRetryPolicy.retryParked(listOf(item),now).single().nextAt)
        assertTrue(ActivityRetryPolicy.isWindowFailure(502))
        assertTrue(ActivityRetryPolicy.isWindowFailure(200))
        listOf(400,401,403,429,503).forEach { assertFalse(ActivityRetryPolicy.isWindowFailure(it)) }
    }
    @Test fun sparseWindowsCanReachCloudWithoutWholeWindowCoverage() {
        assertFalse(ActivityRetryPolicy.canSubmit(0))
        assertFalse(ActivityRetryPolicy.canSubmit(1))
        assertTrue(ActivityRetryPolicy.canSubmit(2))
        assertTrue(ActivityRetryPolicy.canSubmit(30))
        assertTrue(ActivityRetryPolicy.awaitLateSensor(8999L,1800L))
        assertTrue(ActivityRetryPolicy.awaitLateSensor(9000L,1800L))
        assertTrue(ActivityRetryPolicy.awaitLateSensor(1800L+6*86400L-1,1800L))
        assertFalse(ActivityRetryPolicy.awaitLateSensor(1800L+6*86400L,1800L))
        val waiting = ActivityRetryPolicy.waitForSensor(900L,1800L)
        assertEquals(1,waiting.failures)
        assertEquals(2700L,waiting.nextAt)
        assertNull(ActivityRetryPolicy.due(listOf(waiting),1800L,1800L))
        assertEquals(waiting,ActivityRetryPolicy.due(listOf(waiting),2700L,2700L))
        assertEquals(waiting,ActivityRetryPolicy.decode(ActivityRetryPolicy.encode(listOf(waiting)),2700L).single())
    }
    @Test fun postSyncRearmsOnlySensorRetriesAndIdenticalEvidenceIsNotResubmitted() {
        val hash = CloudActivityProtocol.sha256("observed evidence")
        val waiting = ActivityRetryPolicy.waitForSensor(900L,1800L,hash,21600L)
        val provider = ActivityRetryPolicy.fail(null,0L,1800L)
        val restored = ActivityRetryPolicy.decode(ActivityRetryPolicy.encode(listOf(waiting,provider)),1800L)
        val rearmed = ActivityRetryPolicy.wakeSensor(restored,1800L)
        assertEquals(provider,rearmed.first())
        assertEquals(1800L,rearmed.last().nextAt)
        assertTrue(ActivityRetryPolicy.sameEvidence(rearmed.last(),hash))
        assertFalse(ActivityRetryPolicy.sameEvidence(rearmed.last(),CloudActivityProtocol.sha256("late offload")))
    }
    @Test fun historicalCadenceUsesObservedIntervalsRatherThanSampleCount() {
        val steps = (900L..925L step 5L).map { StepSample("strap",it,((it-900L)*2).toInt(),2) }
        val bins = CloudActivityProtocol.bins(900L,emptyList(),emptyList(),steps,1.0)
        assertEquals(50,bins[0][2])
        val mixed = steps.mapIndexed { index,row -> row.copy(deviceId=if (index%2==0) "strap" else "other") }
        assertNull(CloudActivityProtocol.bins(900L,emptyList(),emptyList(),mixed,1.0)[0][2])
        val classed = (900L until 930L).map { StepSample("strap",it,((it-900L)*2).toInt(),2) }
        val gait = CloudActivityProtocol.gaitEvidence(900L,classed)
        assertEquals(listOf(0,30,30),gait?.get(0))
        assertNull(CloudActivityProtocol.gaitEvidence(900L,classed.map { it.copy(activityClass=null) }))
    }
    @Test fun publicStatusDoesNotExposeTechnicalDetails() {
        assertEquals("AI Detection Active",ActivityRetryPolicy.displayStatus(true,true,"Cloud analysis complete · 0 new activities"))
        assertEquals("AI Detection Offline",ActivityRetryPolicy.displayStatus(true,false,"Cloud analysis complete · 0 new activities"))
        assertEquals("AI Detection Off",ActivityRetryPolicy.displayStatus(false,true,"Cloud analysis complete"))
        assertEquals("AI Detection Unavailable",ActivityRetryPolicy.displayStatus(true,true,"Cloud response unavailable"))
        assertEquals("AI Detection Waiting",ActivityRetryPolicy.displayStatus(true,true,"Waiting for wearable data"))
    }
    @Test fun sensitivityChangesOnlyDurationPolicy() {
        val modes = ActivitySensitivity.entries
        assertEquals(listOf(6,3,2), modes.map { it.minBins("WALKING") })
        assertEquals(listOf(6,3,2), modes.map { it.minBins("RUNNING") })
        assertEquals(listOf(20,10,6), modes.map { it.minBins("WORKOUT") })
        assertTrue(modes.all { it.minBins("CLIMBING_STAIRS") == 2 })
        assertEquals(ActivitySensitivity.MEDIUM, ActivitySensitivity.fromStored(null))
        assertEquals(ActivitySensitivity.MEDIUM, ActivitySensitivity.fromStored("invalid"))
        assertEquals(ActivitySensitivity.HIGH, ActivitySensitivity.fromStored("HIGH"))
    }
    @Test fun absentSamplesStayUnknown() {
        val bins = CloudActivityProtocol.bins(900L,emptyList(),emptyList(),emptyList(),1.0)
        assertEquals(30,bins.size)
        assertTrue(bins.all { it[0]==null && it[1]==null && it[2]==null && it[3]==null && it[4]==0.0 && it[5]==0.0 })
        assertTrue(bins.none(CloudActivityProtocol::usable))
    }
    @Test fun exclusionsAreHalfOpenAndMerged() {
        assertEquals(listOf(100L to 140L,150L to 160L),
            CloudActivityProtocol.excludes(listOf(90L to 110L,110L to 140L,150L to 170L,70L to 100L),100L,160L))
        assertFalse(CloudActivityProtocol.overlap(100,110,110,120))
    }
    @Test fun sensorBinsDoNotClassifyOrInventElevation() {
        val h=(900L until 930L).map{HrSample("strap",it,110)}
        val g=(900L until 930L).map{GravitySample("strap",it,0.0,0.0,1.0,dynAccel=0.2)}
        val b=CloudActivityProtocol.bins(900L,h,g,emptyList(),1.0)
        assertEquals(110.0,b[0][0])
        assertEquals(0.2,b[0][1])
        assertNull(b[0][2]);assertNull(b[0][3])
        assertTrue(CloudActivityProtocol.usable(b[0]))
    }
    @Test fun hrBadgeTracksFinalSourceNotSport() {
        assertTrue(com.otidotid.alke.ui.isCloudAiHrEvent(CloudActivityProtocol.WORKOUT_SOURCE))
        assertTrue(com.otidotid.alke.ui.isCloudAiHrEvent(CloudActivityProtocol.DAILY_SOURCE))
        listOf("manual", "health-connect", "whoop", "my-whoop", "auto-detected", "my-whoop-alke", "apple-health").forEach {
            assertFalse(com.otidotid.alke.ui.isCloudAiHrEvent(it))
        }
    }
    @Test fun sessionCorrectionPreservesSourceAndInvalidatesMetricsWhenTimeChanges() {
        val row=com.otidotid.alke.data.WorkoutRow("strap",900L,1200L,"Walking","alke-cloud-activity",durationS=300.0,energyKcal=20.0,avgHr=100)
        val correction=com.otidotid.alke.data.SessionCorrection("Running",960L,1260L)
        assertTrue(correction.validFor(row,2000L,java.time.ZoneOffset.UTC))
        val result=correction.applyTo(row)
        assertEquals(row.source,result.source)
        assertEquals("Running",result.sport)
        assertEquals(960L,result.startTs)
        assertNull(result.avgHr); assertNull(result.energyKcal)
        assertEquals(100,row.avgHr)
        assertFalse(com.otidotid.alke.data.SessionCorrection("Walking",960L,3000L).validFor(row,2000L,java.time.ZoneOffset.UTC))
    }
    @Test fun sourceDesignLock() {
        var root=File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(5){if(!File(root,"settings.gradle.kts").exists())root=root.parentFile ?: root}
        val ui=File(root,"app/src/main/java/com/otidotid/alke/ui")
        val today=File(ui,"TodayScreen.kt").readText()
        assertFalse(today.contains("AutoWorkoutNudgeCard("))
        assertTrue(today.contains("cloudDailyActivityRows(correctionFrom,correctionTo)"))
        assertTrue(today.contains("hrEventVisual(workout.sport)"))
        assertTrue(today.contains("detectedWorkoutsUnion(viewModel.activeStrapId"))
        assertTrue(today.contains("applySessionCorrections("))
        val history=File(ui,"WorkoutsScreen.kt").readText()
        assertTrue(history.contains("UnifiedAllSessions("))
        assertTrue(history.contains("Avg. heart rate"))
        assertTrue(history.contains("Calories burned"))
        assertTrue(history.contains("Last analysis · No new activities"))
        assertFalse(File(ui,"AlkeExperience.kt").readText().contains("CloudActivityIntelligencePanel(vm)"))
    }
}
