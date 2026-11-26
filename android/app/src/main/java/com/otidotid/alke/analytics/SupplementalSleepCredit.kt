package com.otidotid.alke.analytics

import com.otidotid.alke.data.SleepSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * Accounting only: consumes recorded sleep estimates; never detects sleep, assigns stages,
 * or gives a physiological recovery bonus. Time is half-open epoch seconds.
 * A complete main-sleep envelope reserves the time (including wake gaps).
 * Segment data can be clipped; summary-only data cannot be located within an overlap.
 */
object SupplementalSleepCredit {
    data class Span(val start: Long, val end: Long) {
        fun overlaps(other: Span) = start < other.end && other.start < end
    }
    fun span(s: SleepSession) = Span(s.effectiveStartTs, s.endTs)

    fun union(spans: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        for (s in spans.filter { it.end > it.start }.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && s.start <= last.end)
                out[out.lastIndex] = Span(last.start, maxOf(last.end, s.end))
            else out.add(s)
        }
        return out
    }

    fun subtract(span: Span, blocked: List<Span>): List<Span> {
        var remaining = listOf(span)
        for (b in union(blocked)) {
            remaining = remaining.flatMap { a ->
                if (!a.overlaps(b)) listOf(a) else listOf(
                    Span(a.start, minOf(a.end,b.start)),
                    Span(maxOf(a.start,b.end),a.end),
                ).filter { it.end > it.start }
            }
        }
        return remaining
    }

    private val asleepStages = setOf("light", "deep", "rem")
    private fun segmentSpans(session: SleepSession): List<Span>? = runCatching {
        val arr = JSONArray(session.stagesJSON ?: return null)
        val bounds = span(session)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optString("stage").trim().lowercase() !in asleepStages) return@mapNotNull null
            val start = maxOf(o.optLong("start", Long.MAX_VALUE), bounds.start)
            val end = minOf(o.optLong("end", Long.MIN_VALUE), bounds.end)
            Span(start,end).takeIf { it.end > it.start }
        }
    }.getOrNull()

    private fun summarySeconds(session: SleepSession): Double? = runCatching {
        val o = JSONObject(session.stagesJSON ?: return null)
        val values = asleepStages.map { key -> o.optDouble(key,0.0) }
        if (values.any { !it.isFinite() || it < 0.0 }) return null
        (values.sum()*60.0).coerceAtMost((session.endTs-session.effectiveStartTs).coerceAtLeast(0L).toDouble())
    }.getOrNull()

    /** Credited estimated asleep minutes, not time in bed. No overlapping second is counted twice. */
    fun minutes(supplemental: List<SleepSession>, blocked: List<Span>): Double {
        val rows = supplemental.filter { it.endTs > it.effectiveStartTs }
            .distinctBy { Triple(it.deviceId,it.startTs,it.endTs) }
        val reserved = union(blocked)
        val segments = rows.flatMap { row -> segmentSpans(row).orEmpty() }
            .flatMap { subtract(it,reserved) }
        var seconds = union(segments).sumOf { it.end-it.start }.toDouble()
        // An aggregate has no per-epoch timestamps: only use it when its whole window is disjoint
        // from ALL other records. Never prorate or guess which minutes of an overlap were asleep.
        for (row in rows) {
            if (segmentSpans(row) != null) continue
            val bounds=span(row)
            if (reserved.any(bounds::overlaps) || rows.any { it !== row && bounds.overlaps(span(it)) }) continue
            seconds += summarySeconds(row) ?: 0.0
        }
        return seconds/60.0
    }
}
