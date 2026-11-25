package com.otidotid.alke.data

import java.time.Instant
import java.time.ZoneId

/** Immutable-source correction used by the unified history and HR timeline. */
internal data class SessionCorrectionKey(val device: String,val start: Long,val sport: String,val source: String) {
    companion object { fun of(row: WorkoutRow)=SessionCorrectionKey(row.deviceId,row.startTs,row.sport,row.source) }
}
internal data class SessionCorrection(val sport: String,val start: Long,val end: Long) {
    fun validFor(original: WorkoutRow,now: Long,zone: ZoneId=ZoneId.systemDefault()): Boolean =
        sport.isNotBlank() && sport.length<=120 && start>0 && end>start && end<=now && end-start<=86400L &&
            Instant.ofEpochSecond(start).atZone(zone).toLocalDate()==Instant.ofEpochSecond(original.startTs).atZone(zone).toLocalDate()
    fun applyTo(row: WorkoutRow): WorkoutRow {
        val timeChanged=start!=row.startTs || end!=row.endTs
        val result=row.copy(sport=sport,startTs=start,endTs=end,durationS=(end-start).toDouble())
        // A different time range cannot borrow metrics or a GPS route from the original.
        return if (!timeChanged) result else result.copy(avgHr=null,maxHr=null,energyKcal=null,
            strain=null,zonesJSON=null,distanceM=null,routePolyline=null,steps=null)
    }
}
