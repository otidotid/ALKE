package com.otidotid.alke.ai

import org.junit.Assert.*
import org.junit.Test
import com.otidotid.alke.data.HrSample
import com.otidotid.alke.data.GravitySample
import java.io.File

class CloudActivityContractTest {
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
    @Test fun sourceDesignLock() {
        var root=File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(5){if(!File(root,"settings.gradle.kts").exists())root=root.parentFile ?: root}
        val ui=File(root,"app/src/main/java/com/otidotid/alke/ui")
        val today=File(ui,"TodayScreen.kt").readText()
        assertFalse(today.contains("AutoWorkoutNudgeCard("))
        assertTrue(today.contains("cloudDailyActivityRows(start,end)"))
        assertTrue(today.contains("hrEventVisual(workout.sport)"))
        assertTrue(File(ui,"AlkeExperience.kt").readText().contains("CloudActivityIntelligencePanel(vm)"))
    }
}
