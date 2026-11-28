package com.otidotid.alke.multidevice.huawei

import com.otidotid.alke.data.WorkoutRow
import org.junit.Assert.*
import org.junit.Test

class HuaweiWorkoutIntegrationTest {
    private fun row(id:String,start:Long,end:Long,distance:Double?=null) =
        WorkoutRow(id,start,end,"Running",id,durationS=(end-start).toDouble(),distanceM=distance)
    @Test fun uniqueMatchKeepsCanonicalIdentityAndUsesHuaweiDetails() {
        val base=row("my-whoop",100,1000,980.0).copy(strain=9.0,routePolyline="phone")
        val native=row("huawei-gt5",105,995,1000.0).copy(steps=1200,avgHr=140)
        val merged=HuaweiWorkoutIntegration.reconcile(listOf(base),listOf(native,native)).single()
        assertEquals(base.deviceId,merged.deviceId)
        assertEquals(base.startTs,merged.startTs)
        assertEquals(1000.0,merged.distanceM!!,0.0)
        assertEquals(1200,merged.steps)
        assertEquals("phone",merged.routePolyline)
        assertEquals(9.0,merged.strain!!,0.0)
    }
    @Test fun nearbyDistinctSessionsStaySeparateAndAmbiguousOverlapIsNotAdded() {
        val first=row("my-whoop",100,400)
        val second=row("huawei-gt5",410,710)
        assertEquals(2,HuaweiWorkoutIntegration.reconcile(listOf(first),listOf(second)).size)
        val ambiguous=row("huawei-gt5",250,550)
        assertEquals(listOf(first),HuaweiWorkoutIntegration.reconcile(listOf(first),listOf(ambiguous)))
    }
    @Test fun unknownSportAndUnverifiedRouteRemainAbsent() {
        assertNull(HuaweiWorkoutIntegration.sport(254))
        val s=HuaweiHistoryRead.WorkoutSummary(1,100,1000,1,null,1000,null,null,null,null)
        assertNull(HuaweiWorkoutIntegration.route(s,emptyList()))
        assertNull(HuaweiWorkoutIntegration.route(s,listOf(
            HuaweiGpsRead.Point(100,0.0,0.0,null),HuaweiGpsRead.Point(101,1.0,1.0,null))))
    }
}
