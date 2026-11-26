package com.otidotid.alke.ai

import com.otidotid.alke.analytics.HrvAnalyzer
import com.otidotid.alke.analytics.StreamReadCap
import com.otidotid.alke.data.GravitySample
import com.otidotid.alke.data.HrSample
import com.otidotid.alke.data.RrInterval
import com.otidotid.alke.data.WhoopRepository
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Measured evidence only. No sleep classification, interpolation, or imputed motion/HRV.
 * Coverage is an engineering quality measure, not a diagnostic sleep threshold.
 */
internal object CloudSleepEvidence {
    private const val BIN = CloudSleepProtocol.BIN
    private fun rounded(x:Double) = round(x*1000.0)/1000.0

    suspend fun read(repo:WhoopRepository,deviceId:String,from:Long,to:Long,offsetMinutes:Int,
                     habitualMidsleepSec:Long?,excluded:List<Pair<Long,Long>>):CloudSleepProtocol.Snapshot {
        require(from % BIN == 0L && to % BIN == 0L && (to-from)/BIN in 3L..CloudSleepProtocol.MAX_BINS.toLong())
        val bins = mutableListOf<List<Number?>>()
        // Bound peak heap on the background worker. Each chunk keeps the previous motion second.
        var start = from
        while (start < to) {
            val end = minOf(start + 3600L, to)
            val hr = repo.hrSamplesForDevice(deviceId, start, end-1, StreamReadCap.HR)
            val motion = repo.gravitySamplesForDevice(deviceId, start-1, end-1, StreamReadCap.GRAVITY)
            val rr = repo.rrIntervalsForDevice(deviceId, start, end-1, StreamReadCap.RR)
            bins.addAll(build(deviceId, start, end,
                hr.takeIf { it.size < StreamReadCap.HR }.orEmpty(),
                motion.takeIf { it.size < StreamReadCap.GRAVITY }.orEmpty(),
                rr.takeIf { it.size < StreamReadCap.RR }.orEmpty(),
                offsetMinutes, habitualMidsleepSec, emptyList()).bins)
            start = end
        }
        return CloudSleepProtocol.Snapshot(from, bins, offsetMinutes, habitualMidsleepSec, excluded)
    }

    fun build(deviceId:String,from:Long,to:Long,hr:List<HrSample>,gravity:List<GravitySample>,
              rr:List<RrInterval>,offsetMinutes:Int,habitualMidsleepSec:Long?,
              excluded:List<Pair<Long,Long>>):CloudSleepProtocol.Snapshot {
        require(from%BIN==0L && to%BIN==0L && to>from)
        val count=((to-from)/BIN).toInt()
        require(count in 1..CloudSleepProtocol.MAX_BINS)
        val h=hr.asSequence().filter { it.deviceId==deviceId && it.ts in from until to && it.bpm in 25..250 }
            .distinctBy { it.ts }.groupBy { (it.ts-from)/BIN }
        val motion=gravity.filter { it.deviceId==deviceId && it.ts in from-1 until to }
            .sortedBy { it.ts }.distinctBy { it.ts }
        val m=ArrayList<Pair<Long,Double>>()
        motion.forEachIndexed { i,row ->
            val prev=motion.getOrNull(i-1)
            val value=row.dynAccel ?: prev?.takeIf { row.ts-it.ts==1L }?.let {
                sqrt((row.x-it.x)*(row.x-it.x)+(row.y-it.y)*(row.y-it.y)+(row.z-it.z)*(row.z-it.z))
            }
            if(row.ts>=from && value!=null && value.isFinite() && value in 0.0..20.0) m.add(row.ts to value)
        }
        val g=m.groupBy { (it.first-from)/BIN }
        val r=rr.asSequence().filter { it.deviceId==deviceId && it.ts in from until to && it.tsSuspect!=1 }
            .distinctBy { Triple(it.ts,it.rrMs,it.seq) }.sortedBy { it.ts }.groupBy { (it.ts-from)/BIN }
        val bins=List(count) { i ->
            val hs=h[i.toLong()].orEmpty()
            val gs=g[i.toLong()].orEmpty()
            val rs=r[i.toLong()].orEmpty()
            val result=rrSummary(rs)
            listOf<Number?>(
                hs.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.let(::rounded),
                gs.takeIf { it.isNotEmpty() }?.let { rounded(sqrt(it.map { p -> p.second*p.second }.average())) },
                result.first,
                rounded((hs.size/BIN.toDouble()).coerceIn(0.0,1.0)),
                rounded((gs.size/BIN.toDouble()).coerceIn(0.0,1.0)),
                result.second,
            )
        }
        return CloudSleepProtocol.Snapshot(from,bins,offsetMinutes,habitualMidsleepSec,excluded)
    }

    private fun rrSummary(rows:List<RrInterval>):Pair<Double?,Double> {
        if(rows.size<2) return null to 0.0
        // Never compute successive differences across a long unobserved interval.
        if(rows.zipWithNext().any { (a,b)->b.ts-a.ts>5L }) return null to 0.0
        val values=rows.map { it.rrMs.toDouble() }
        val ts=rows.map { it.ts }
        val coverage=values.sum()/(BIN*1000.0)
        val observed=HrvAnalyzer.rrCoverage(ts,values)
        val verdict=HrvAnalyzer.classifyCoverage(observed,HrvAnalyzer.collapsedCoverage(ts,values))
        if(!coverage.isFinite() || coverage !in 0.7..1.10 ||
            !HrvAnalyzer.successiveDiffIsTrustworthy(verdict)) return null to 0.0
        val analyzed=HrvAnalyzer.analyzeRaw(values,HrvAnalyzer.DEFAULT_SPOT_MAX_REJECTED_FRACTION)
        val rmssd=analyzed.rmssd?.takeIf { it.isFinite() && it>=0.0 && it<=2000.0 } ?: return null to 0.0
        val validCoverage=coverage*analyzed.nClean/rows.size.toDouble()
        if(validCoverage<0.7) return null to 0.0
        return rounded(rmssd) to rounded(validCoverage.coerceIn(0.0,1.0))
    }
}
