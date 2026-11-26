package com.otidotid.alke.ai

import com.otidotid.alke.data.GravitySample
import com.otidotid.alke.data.HrSample
import com.otidotid.alke.data.RrInterval
import org.junit.Assert.*
import org.junit.Test

class CloudSleepEvidenceTest {
    private val from=1_800_000_000L
    private val to=from+900
    @Test fun missingAndOtherDeviceSamplesRemainUnknown() {
        val result=CloudSleepEvidence.build("strap",from,to,
            listOf(HrSample("other",from,60)),emptyList(),emptyList(),420,null,emptyList())
        assertTrue(result.bins.all { it==listOf<Number?>(null,null,null,0.0,0.0,0.0) })
    }
    @Test fun actualHrMotionAndRrProduceCoverageWithoutClassifyingSleep() {
        val hr=(0 until 300).map { HrSample("strap",from+it,60) }
        val motion=(0 until 300).map { GravitySample("strap",from+it,0.0,0.0,1.0,dynAccel=0.02) }
        val rr=(0 until 300).map { RrInterval("strap",from+it,if(it%2==0) 990 else 1010) }
        val result=CloudSleepEvidence.build("strap",from,to,hr,motion,rr,420,null,emptyList())
        val b=result.bins.first()
        assertEquals(60.0,b[0]!!.toDouble(),1e-9)
        assertEquals(0.02,b[1]!!.toDouble(),1e-9)
        assertNotNull(b[2])
        assertEquals(1.0,b[3]!!.toDouble(),1e-9)
        assertEquals(1.0,b[4]!!.toDouble(),1e-9)
        assertTrue(result.bins.drop(1).all { !CloudSleepProtocol.usable(it) })
    }
    @Test fun longGapIsNotMotionOrHrvEvidence() {
        val g=listOf(GravitySample("strap",from,0.0,0.0,1.0),
            GravitySample("strap",from+50,1.0,0.0,0.0))
        val rr=(0 until 300).filter { it !in 100..120 }.map { RrInterval("strap",from+it,1000) }
        val result=CloudSleepEvidence.build("strap",from,to,emptyList(),g,rr,420,null,emptyList())
        assertNull(result.bins[0][1]);assertNull(result.bins[0][2])
    }
}
