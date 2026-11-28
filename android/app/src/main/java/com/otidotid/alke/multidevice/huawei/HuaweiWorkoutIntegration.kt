package com.otidotid.alke.multidevice.huawei

import android.content.Context
import com.otidotid.alke.analytics.RouteMath
import com.otidotid.alke.data.WorkoutRow
import com.otidotid.alke.ui.WorkoutEditing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Personal GT5 interoperability candidate. Independently implemented protocol facts:
 * native workout type 1 run, 2 walk, 3 cycling, 4 mountain hike, 5 indoor run,
 * 7 indoor cycle, 13 indoor walk, 14 hike (Gadgetbridge HuaweiWorkoutGbParser
 * blob ffe48cee20d5a4644ccf362392203a98768d204b). These are wire values, not
 * the Huawei Health API sport enum. No clinical scoring or raw WHOOP writes.
 *
 * A real-watch comparison is required before enabling this firmware-specific
 * read projection; changing firmware invalidates the opt-in.
 */
internal object HuaweiWorkoutIntegration {
    const val SOURCE = "huawei-gt5"
    private const val PREFS = "alke_huawei_workout_validation"
    fun observeFirmware(context: Context, device: String, firmware: String) {
        val p = context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        if(p.getString("firmware:$device",null)!=firmware) {
            p.edit().putString("firmware:$device",firmware).commit()
            HuaweiTimelineSource.changed(hrChanged=true,stepsChanged=false)
        }
    }

    fun enabled(context: Context,device: String): Boolean {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val fw=p.getString("firmware:$device",null) ?: return false
        return p.getString("approved:$device",null)==fw
    }

    fun setEnabled(context: Context,device: String,enabled: Boolean): Boolean {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val fw=p.getString("firmware:$device",null) ?: return false
        val edit=p.edit()
        if(enabled) edit.putString("approved:$device",fw) else edit.remove("approved:$device")
        val success=edit.commit()
        if(success) HuaweiTimelineSource.changed(hrChanged=true,stepsChanged=false)
        return success
    }

    fun sport(code: Int?): String? = when(code) {
        1 -> "Running"
        2 -> "Walking"
        3 -> "Cycling"
        4,14 -> "Hiking"
        5 -> "Indoor Running"
        7 -> "Indoor Cycling"
        13 -> "Indoor Walking"
        else -> null
    }

    fun gpsEligible(code: Int?): Boolean = code in setOf(1,2,3,4,14)

    /** Route remains absent unless independent geometry and summary distance agree.
     * No synthetic points, no extrapolation and no fallback to fabricated GPS. */
    fun route(summary: HuaweiHistoryRead.WorkoutSummary,points: List<HuaweiGpsRead.Point>): String? {
        if(!gpsEligible(summary.nativeType) || points.size<2) return null
        val distance=summary.distanceMeters?.toDouble()?.takeIf { it>=100 } ?: return null
        if(points.any { it.timestamp !in summary.start..summary.end ||
            !it.latitude.isFinite() || !it.longitude.isFinite() ||
            it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) return null
        val coords=points.map { RouteMath.LatLng(it.latitude,it.longitude) }
        val elapsed=summary.end-summary.start
        if(elapsed<=0 || points.last().timestamp-points.first().timestamp<elapsed*0.75) return null
        for(i in 1 until points.size) {
            val dt=points[i].timestamp-points[i-1].timestamp
            val meters=RouteMath.haversineMeters(coords[i-1],coords[i])
            if(dt !in 1L..600L || !meters.isFinite() || meters/dt>80.0) return null
        }
        val length=RouteMath.totalMeters(coords)
        if(!length.isFinite() || length/distance !in 0.70..1.30) return null
        return RouteMath.encode(coords)
    }

    /** Unique, same-sport and near-identical time windows; distance corroboration
     * when both devices measured it. Never merge two distinct Huawei identities. */
    fun matches(base: WorkoutRow, native: WorkoutRow): Boolean {
        if(WorkoutEditing.sportKey(base.sport)!=WorkoutEditing.sportKey(native.sport)) return false
        if(abs(base.startTs-native.startTs)>120 || abs(base.endTs-native.endTs)>120) return false
        val overlap=minOf(base.endTs,native.endTs)-maxOf(base.startTs,native.startTs)
        val longer=maxOf(base.endTs-base.startTs,native.endTs-native.startTs)
        if(longer<=0 || overlap<=0 || overlap.toDouble()/longer<0.80) return false
        val a=base.distanceM?.takeIf { it.isFinite() && it>0 }
        val b=native.distanceM?.takeIf { it.isFinite() && it>0 }
        return a==null || b==null || abs(a-b)<=maxOf(100.0,maxOf(a,b)*0.20)
    }

    /** Keep existing canonical identity and manual timing/sport corrections. A
     * verified Huawei detail replaces only corresponding fields; WHOOP remains
     * fallback and its score/calories/zones are not recalculated. Ambiguous
     * overlaps stay staged rather than creating a second visible workout. */
    fun reconcile(existing: List<WorkoutRow>, native: List<WorkoutRow>): List<WorkoutRow> {
        if(native.isEmpty()) return existing
        val unique=native.distinctBy { Triple(it.deviceId,it.startTs,it.sport) }
        val candidates=unique.map { n -> existing.indices.filter { matches(existing[it],n) } }
        val claimed=candidates.flatten().groupingBy { it }.eachCount()
        val result=existing.toMutableList()
        unique.forEachIndexed { i,n ->
            val match=candidates[i].singleOrNull()
            if(match!=null && claimed[match]==1) {
                val base=result[match]
                result[match]=base.copy(distanceM=n.distanceM ?: base.distanceM,
                    steps=n.steps ?: base.steps,avgHr=n.avgHr ?: base.avgHr,maxHr=n.maxHr ?: base.maxHr,
                    routePolyline=n.routePolyline ?: base.routePolyline)
            } else if(candidates[i].isEmpty() && existing.none {
                    n.startTs<it.endTs && it.startTs<n.endTs
                }) result.add(n)
        }
        return result
    }

    suspend fun merge(context: Context,existing: List<WorkoutRow>,from: Long,to: Long): List<WorkoutRow> = try {
        withContext(Dispatchers.IO) {
            val device=HuaweiTimelineSource.verifiedDeviceKey(context) ?: return@withContext existing
            if(!enabled(context,device)) return@withContext existing
            val rows=HuaweiFitnessStore(context.applicationContext).use { db ->
                db.workoutSummaries(device,from,to).mapNotNull { w ->
                    val sport=sport(w.nativeType) ?: return@mapNotNull null
                    if(w.end<=w.start || w.end>System.currentTimeMillis()/1000L) return@mapNotNull null
                    val hr=db.workoutHr(device,w)
                    WorkoutRow(deviceId="$SOURCE:$device:${w.number}",startTs=w.start,endTs=w.end,
                        sport=sport,source=SOURCE,durationS=(w.end-w.start).toDouble(),
                        distanceM=w.distanceMeters?.takeIf { it in 0..1_000_000 }?.toDouble(),
                        steps=w.steps?.takeIf { it in 0..200_000 }?.toInt(),
                        avgHr=hr.first,maxHr=hr.second,
                        routePolyline=route(w,db.gpsCandidate(device,w.start,w.number)))
                }
            }
            reconcile(existing,rows)
        }
    } catch(e: CancellationException) { throw e } catch(_: Exception) { existing }
}
