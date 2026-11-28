package com.otidotid.alke.ingest

import android.content.Context
import com.otidotid.alke.data.WorkoutRow
import com.otidotid.alke.data.WhoopRepository
import com.otidotid.alke.multidevice.huawei.HuaweiFitnessStore
import com.otidotid.alke.multidevice.huawei.HuaweiTimelineSource
import com.otidotid.alke.multidevice.huawei.HuaweiWorkoutIntegration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object StravaWorkoutExport {
    data class Prepared(val bytes: ByteArray,val hasGps: Boolean,val omittedUntimedRoute: Boolean)

    /** Use authenticated timestamped native workout GPS only if it is the same
     * validated route shown by ALKE. Older polylines lack time; never fabricate it.
     * Native HR wins its observed minutes; WHOOP fills the other minutes. */
    suspend fun prepare(context: Context,repo: WhoopRepository,activeId: String,row: WorkoutRow): Prepared =
        withContext(Dispatchers.IO) {
            require(row.endTs>row.startTs && row.endTs-row.startTs<=86400L)
            val device=HuaweiTimelineSource.verifiedDeviceKey(context)
            val gps=mutableListOf<RouteExport.RecordedPoint>()
            val nativeHr=linkedMapOf<Long,Int>()
            if(device!=null && HuaweiWorkoutIntegration.enabled(context,device)) {
                HuaweiFitnessStore(context).use { db ->
                    val candidates=db.workoutSummaries(device,row.startTs-120,row.endTs+120).filter { w ->
                        val sport=HuaweiWorkoutIntegration.sport(w.nativeType) ?: return@filter false
                        val native=WorkoutRow("huawei-gt5:$device:${w.number}",w.start,w.end,sport,"huawei-gt5",
                            distanceM=w.distanceMeters?.toDouble())
                        HuaweiWorkoutIntegration.matches(row,native)
                    }
                    candidates.singleOrNull()?.let { w ->
                        val points=db.gpsCandidate(device,w.start,w.number)
                        val encoded=HuaweiWorkoutIntegration.route(w,points)
                        if(encoded!=null && encoded==row.routePolyline) points.forEach { p ->
                            if(p.timestamp in row.startTs..row.endTs)
                                gps.add(RouteExport.RecordedPoint(p.timestamp,p.latitude,p.longitude))
                        }
                        db.workoutHrRecords(device,w).forEach { (ts,bpm) ->
                            if(ts in row.startTs..row.endTs) nativeHr[ts]=bpm
                        }
                    }
                    val start=row.startTs-row.startTs%60
                    val end=row.endTs-row.endTs%60
                    if(end>start) {
                        val covered=nativeHr.keys.map { it/60 }.toSet()
                        db.minutes(device,start,end).forEach { p ->
                            val hr=p.heartRate
                            if(p.epochSeconds in row.startTs..row.endTs && p.epochSeconds/60 !in covered &&
                                hr!=null && hr in 25..250) nativeHr.putIfAbsent(p.epochSeconds,hr)
                        }
                    }
                }
            }
            val ids=if(row.source==HuaweiWorkoutIntegration.SOURCE)
                WhoopRepository.workoutHrDeviceIds("",activeId,activeId)
                else WhoopRepository.workoutHrDeviceIds(row.source,row.deviceId,activeId)
            val covered=nativeHr.keys.map { it/60 }.toSet()
            val whoop=repo.hrSamplesFor(ids,row.startTs,row.endTs,100_000)
                .filter { it.ts in row.startTs..row.endTs && it.bpm in 25..250 && it.ts/60 !in covered }
                .associate { it.ts to it.bpm }
            val points=merge(gps,whoop+nativeHr)
            require(points.size>=2) { "No timestamped workout sensor data available" }
            Prepared(RouteExport.recordedFit(points,row.startTs,row.endTs,row.sport,row.distanceM,row.energyKcal),
                gps.size>=2,!row.routePolyline.isNullOrBlank() && gps.size<2)
        }

    fun merge(gps: List<RouteExport.RecordedPoint>,hr: Map<Long,Int>): List<RouteExport.RecordedPoint> {
        val result=sortedMapOf<Long,RouteExport.RecordedPoint>()
        for(p in gps) result[p.timestamp]=p
        for((ts,bpm) in hr) result[ts]=result[ts]?.copy(hr=bpm) ?: RouteExport.RecordedPoint(ts,hr=bpm)
        return result.values.toList()
    }
}
