package com.otidotid.alke.ingest

import org.junit.Assert.*
import org.junit.Test

class StravaWorkoutExportTest {
    private val start=1_780_000_000L
    @Test fun recordedFitContainsActualTimesAndValidCrc() {
        val points=listOf(RouteExport.RecordedPoint(start+2,hr=100),
            RouteExport.RecordedPoint(start+37,-6.2,106.8,120),
            RouteExport.RecordedPoint(start+98,-6.201,106.801))
        val fit=RouteExport.recordedFit(points,start,start+120,"Running",150.0,null)
        assertEquals(".FIT",String(fit.copyOfRange(8,12)))
        assertEquals(0,RouteExport.fitCrc(fit))
        assertTrue(fit.size>100)
    }
    @Test fun mergePreservesActualTimestampAndDoesNotInventCoordinates() {
        val gps=listOf(RouteExport.RecordedPoint(start+2,-6.2,106.8))
        val result=StravaWorkoutExport.merge(gps,mapOf(start+2 to 100,start+37 to 110))
        assertEquals(listOf(start+2,start+37),result.map { it.timestamp })
        assertEquals(100,result.first().hr)
        assertNull(result.last().lat)
    }
    @Test fun invalidOrEmptyRecordsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteExport.recordedFit(emptyList(),start,start+120,"Running",null,null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouteExport.recordedFit(listOf(RouteExport.RecordedPoint(start+2,hr=100),
                RouteExport.RecordedPoint(start+2,hr=120)),start,start+120,"Running",null,null)
        }
    }
}
