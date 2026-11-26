package com.otidotid.alke.analytics

import com.otidotid.alke.data.DailyMetric
import com.otidotid.alke.data.SleepSession
import org.json.JSONArray

/**
 * An ALKE accounting model, not a clinically validated recovery formula.
 * Keep the main-sleep record and the existing physiological baseline unchanged.
 * Only complete, non-overlapping staged episodes contribute to the quality aggregate.
 * A row with unknown stages cannot silently dilute the restorative component.
 */
internal object UnifiedSleepRest {
    const val KEY = "alke_daily_rest"
    const val VERSION = "alke-unified-sleep-rest-v1"
    data class Score(val rest: Double, val asleepMin: Double, val inBedMin: Double,
                     val deepMin: Double, val remMin: Double, val supplementalMin: Double)

    // One minute tolerance for imported minute-rounded stage totals, NOT a sleep detection threshold.
    private const val ROUNDING_MIN = 1.0

    private fun completeStages(row: SleepSession): SleepStageTotals.Minutes? {
        val start = row.effectiveStartTs
        val end = row.endTs
        if (end <= start) return null
        val raw = row.stagesJSON ?: return null
        val arr = runCatching { JSONArray(raw) }.getOrNull()
        if (arr != null && (0 until arr.length()).any {
                arr.optJSONObject(it)?.has("start") == true || arr.optJSONObject(it)?.has("end") == true
            }) {
            val spans = (0 until arr.length()).map { i ->
                val o = arr.optJSONObject(i) ?: return null
                val a = o.optLong("start", Long.MIN_VALUE)
                val b = o.optLong("end", Long.MIN_VALUE)
                if (b <= a || o.optString("stage") !in setOf("wake","awake","light","deep","rem")) return null
                maxOf(a,start) to minOf(b,end)
            }.filter { it.second > it.first }.sortedBy { it.first }
            // Reject both missing epochs and inconsistent/overlapping epochs.
            if (spans.isEmpty() || spans.first().first != start || spans.last().second != end ||
                spans.zipWithNext().any { (a,b) -> a.second != b.first }) return null
            val clipped = JSONArray()
            for (i in 0 until arr.length()) {
                val o=arr.getJSONObject(i)
                val a=maxOf(o.getLong("start"),start)
                val b=minOf(o.getLong("end"),end)
                if (b>a) clipped.put(org.json.JSONObject().put("start",a).put("end",b).put("stage",o.getString("stage")))
            }
            return SleepStageTotals.minutes(clipped.toString())
        }
        val m = SleepStageTotals.minutes(raw) ?: return null
        if (listOf(m.awake,m.light,m.deep,m.rem).any { !it.isFinite() || it < 0.0 } ||
            kotlin.math.abs(m.inBed-(end-start)/60.0)>ROUNDING_MIN) return null
        return m
    }

    /** No re-use of a main episode as additional sleep, no guessed stages or duplicate time. */
    fun score(main: DailyMetric, episodes: List<SleepSession>, offsetSec: Long,
              habitualMidsleepSec: Long?, sleepNeedHours: Double? = null): Score? {
        val mainMin = main.totalSleepMin?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val eff = main.efficiency?.let { if (it>1.0) it/100.0 else it }
            ?.takeIf { it.isFinite() && it > 0.0 && it <= 1.0 } ?: return null
        val mainDeep = main.deepMin?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val mainRem = main.remMin?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        if (mainDeep+mainRem>mainMin) return null
        val rows=episodes.filter { it.endTs > it.effectiveStartTs }
            .distinctBy { Triple(it.deviceId,it.startTs,it.endTs) }
            .sortedBy { it.effectiveStartTs }
        val group = SleepStageTotals.mainNightGroupIndices(
            rows.map { SleepStageTotals.NightBlock(it.effectiveStartTs, it.endTs, it.cloudEpisodeType) },
            offsetSec,habitualMidsleepSec) ?: return null
        if (group.isEmpty()) return null
        val primary=group.map { rows[it] }
        if (primary.zipWithNext().any { (a,b)->a.endTs>b.effectiveStartTs }) return null
        val primaryStages=primary.map { completeStages(it) ?: return null }
        // Refuse to append a supplemental episode to a DailyMetric that may already contain it.
        if (kotlin.math.abs(primaryStages.sumOf { it.asleep }-mainMin)>ROUNDING_MIN) return null
        val mainStart=primary.minOf { it.effectiveStartTs }
        val mainEnd=primary.maxOf { it.endTs }
        val supplemental=rows.filterIndexed { i,_ -> i !in group }.filter { row ->
            !(row.effectiveStartTs<mainEnd && mainStart<row.endTs) &&
            rows.none { other -> other !== row &&
                other.effectiveStartTs < row.endTs && row.effectiveStartTs < other.endTs }
        }.mapNotNull(::completeStages).filter { it.asleep>0.0 }
        val extra=supplemental.sumOf { it.asleep }
        if (extra <= 0.0) return null
        val asleep=mainMin+extra
        val inBed=mainMin/eff+supplemental.sumOf { it.inBed }
        val deep=mainDeep+supplemental.sumOf { it.deep }
        val rem=mainRem+supplemental.sumOf { it.rem }
        val rest=RestScorer.rest(asleep*60.0,asleep/inBed,deep*60.0,rem*60.0,sleepNeedHours) ?: return null
        return Score(rest,asleep,inBed,deep,rem,extra)
    }

    /** Same half-open edit/dismiss rules used by the existing session persistence path. */
    fun fromDetected(main: DailyMetric, detected: List<DetectedSleep>, edits: List<SleepSession>,
                     dismissed: List<Pair<Long,Long>>, computedId: String, offsetSec: Long,
                     habitualMidsleepSec: Long?, sleepNeedHours: Double?): Score? {
        val editedWindows = edits.map { it.effectiveStartTs to it.endTs }
        val rows=DismissedSleepGuard.keeping(detected,editedWindows+dismissed) { it.start to it.end }
            .map { s -> SleepSession(computedId,s.start,s.end,efficiency=s.efficiency,
                restingHr=s.restingHR,avgHrv=s.avgHRV,stagesJSON=AnalyticsEngine.encodeStages(s.stages)).also { it.cloudEpisodeType = s.cloudEpisodeType } }
        return score(main,rows+DismissedSleepGuard.keeping(edits,dismissed) { it.effectiveStartTs to it.endTs },
            offsetSec,habitualMidsleepSec,sleepNeedHours)
    }
}
