package com.otidotid.alke.multidevice.huawei

import android.content.Context
import com.otidotid.alke.data.HrBucket
import com.otidotid.alke.data.WhoopRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Consumer-facing historical HR only. Original WHOOP raw HR, live HR, RR and
 * strain/recovery inputs are unchanged. A Huawei minute may replace a WHOOP
 * minute in the displayed timeline, never create 60 fictional second samples.
 */
internal object HuaweiTimelineSource {
    private const val PREFS = "alke_huawei_timeline"
    private const val KEY = "verified_device"
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val mutableStepRevision = MutableStateFlow(0L)
    val stepRevision = mutableStepRevision.asStateFlow()

    fun selectVerifiedDevice(context: Context, deviceKey: String) {
        require(deviceKey.matches(Regex("[0-9a-f]{64}")))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY, null) != deviceKey) {
            check(prefs.edit().putString(KEY, deviceKey).commit())
            changed()
        }
    }

    fun verifiedDeviceKey(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    @Synchronized fun changed(hrChanged: Boolean = true, stepsChanged: Boolean = true) {
        if (hrChanged) mutableRevision.value += 1
        if (stepsChanged) mutableStepRevision.value += 1
    }

    data class Result(val buckets: List<HrBucket>, val huaweiBucketStarts: Set<Long>)

    suspend fun read(context: Context, repository: WhoopRepository, whoopId: String,
        from: Long, to: Long, bucketSeconds: Long): Result {
        val original = repository.hrBucketsUnion(whoopId, from, to, bucketSeconds)
        if (to < from || to - from > 172800 || bucketSeconds < 60 || bucketSeconds % 60 != 0L)
            return Result(original, emptySet())
        return try {
            val huawei = withContext(Dispatchers.IO) {
                val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                if (key == null || !key.matches(Regex("[0-9a-f]{64}"))) emptyList()
                else HuaweiFitnessStore(context.applicationContext).use { store ->
                    buildList {
                        var cursor = from
                        while (cursor <= to) {
                            val end = minOf(cursor + 86400L, to + 1)
                            addAll(store.minutes(key, cursor, end))
                            cursor = end
                        }
                    }
                }
            }.filter { it.epochSeconds % 60L == 0L && it.heartRate != null &&
                it.heartRate in 25..250 && it.epochSeconds + 59 <= to }
            if (huawei.isEmpty()) Result(original, emptySet())
            else combine(original, repository.hrBucketsUnion(whoopId, from, to, 60L), huawei, bucketSeconds)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result(original, emptySet())
        }
    }

    fun combine(original: List<HrBucket>, whoopMinutes: List<HrBucket>,
        huawei: List<HuaweiFitnessRead.Minute>, bucketSeconds: Long): Result {
        require(bucketSeconds >= 60 && bucketSeconds % 60 == 0L)
        fun bucket(ts: Long) = Math.floorDiv(ts, bucketSeconds) * bucketSeconds
        val primary = huawei.filter { it.epochSeconds % 60L == 0L && it.heartRate != null &&
            it.heartRate in 25..250 }.associateBy { it.epochSeconds }
        if (primary.isEmpty()) return Result(original, emptySet())
        val affected = primary.keys.map(::bucket).toSet()
        val whoop = whoopMinutes.associateBy { it.bucket }
        val selected = (whoop.keys + primary.keys).filter { bucket(it) in affected }.mapNotNull { ts ->
            val native = primary[ts]?.heartRate?.toDouble()
            if (native != null) HrBucket(ts, native, native, native) else whoop[ts]
        }.groupBy { bucket(it.bucket) }
        // Mixed buckets use equal observed-minute weighting; unaffected buckets retain the
        // exact native WHOOP aggregation. Extrema are measured values, not inferred peaks.
        val replacement = selected.map { (start, minutes) ->
            HrBucket(start, minutes.map { it.avgBpm }.average(),
                minutes.minOf { it.minBpm }, minutes.maxOf { it.maxBpm })
        }
        return Result((original.filterNot { it.bucket in affected } + replacement).sortedBy { it.bucket }, affected)
    }
}
