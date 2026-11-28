package com.otidotid.alke.multidevice.huawei

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Huawei step deltas are observed minute counts, not WHOOP cumulative motion ticks.
 * A partial vendor day must never replace or be added to an overlapping WHOOP
 * full-day estimate. This resolver only promotes a completely observed window.
 */
internal object HuaweiStepSource {
    const val SOURCE_ID = "huawei-gt5"
    data class Coverage(val start: Long, val endExclusive: Long, val observedMinutes: Int,
        val steps: Long) {
        val complete: Boolean
            get() = start % 60 == 0L && endExclusive % 60 == 0L && endExclusive > start &&
                observedMinutes.toLong() == (endExclusive - start) / 60
    }

    fun coverage(rows: List<HuaweiFitnessRead.Minute>, start: Long, endExclusive: Long): Coverage {
        require(start < endExclusive && endExclusive - start <= 172800L)
        val observed = rows.asSequence().filter {
            it.epochSeconds >= start && it.epochSeconds <= endExclusive - 60 &&
                it.epochSeconds % 60 == 0L && it.steps != null && it.steps in 0..500
        }.groupBy { it.epochSeconds }.values.mapNotNull { sameMinute ->
            val distinct = sameMinute.mapNotNull { it.steps }.distinct()
            distinct.singleOrNull()
        }
        return Coverage(start,endExclusive,observed.size,observed.sumOf { it.toLong() })
    }

    /** Complete historical calendar days only; an in-progress partial day does not displace
     * a full-window WHOOP estimate. Explicit zero is valid; absent data is not zero. */
    fun completeDays(rows: List<HuaweiFitnessRead.Minute>, zone: ZoneId, now: Long): Map<String, Long> {
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        return rows.asSequence().filter { it.epochSeconds < now }.groupBy {
            Instant.ofEpochSecond(it.epochSeconds).atZone(zone).toLocalDate()
        }.mapNotNull { (day, records) ->
            if (day >= today) return@mapNotNull null
            val start = day.atStartOfDay(zone).toEpochSecond()
            val end = day.plusDays(1).atStartOfDay(zone).toEpochSecond()
            if (end <= start || end - start > 172800) return@mapNotNull null
            val result = coverage(records,start,end)
            if (result.complete && result.steps <= 200_000) day.toString() to result.steps else null
        }.toMap()
    }

    suspend fun historicalDays(context: Context): Map<String, Long> = try {
        withContext(Dispatchers.IO) {
            val key = HuaweiTimelineSource.verifiedDeviceKey(context) ?: return@withContext emptyMap()
            val zone = ZoneId.systemDefault()
            // Bounded projection of the recent year; original older ALKE history is untouched.
            val from = LocalDate.now(zone).minusDays(366).atStartOfDay(zone).toEpochSecond()
            val to = LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
            HuaweiFitnessStore(context.applicationContext).use { store ->
                val first = store.firstStepTimestamp(key,from,to) ?: return@use emptyMap()
                val endDay = LocalDate.now(zone)
                var day = Instant.ofEpochSecond(first).atZone(zone).toLocalDate()
                buildMap {
                    while (day < endDay) {
                        val start = day.atStartOfDay(zone).toEpochSecond()
                        val end = day.plusDays(1).atStartOfDay(zone).toEpochSecond()
                        if (end > start && end-start <= 172800L) {
                            val c = store.stepCoverage(key,start,end)
                            if (c.complete && c.steps <= 200_000) put(day.toString(),c.steps)
                        }
                        day = day.plusDays(1)
                    }
                }
            }
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }

    suspend fun workoutWindow(context: Context, from: Long, to: Long): Int? = try {
        // Only exact full minute boundaries, no fractional extrapolation.
        if (from % 60 != 0L || to % 60 != 0L || to <= from || to-from > 86400L) null
        else withContext(Dispatchers.IO) {
            val key = HuaweiTimelineSource.verifiedDeviceKey(context) ?: return@withContext null
            HuaweiFitnessStore(context.applicationContext).use {
                val c = it.stepCoverage(key,from,to)
                if (c.complete && c.steps <= 200_000) c.steps.toInt() else null
            }
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
