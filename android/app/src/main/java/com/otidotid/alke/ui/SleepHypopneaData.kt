package com.otidotid.alke.ui

import android.content.Context
import com.otidotid.alke.analytics.SleepHypopneaRiskEngine
import com.otidotid.alke.data.WhoopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Completed-night risk is pinned to the DETECTED main sleep identity and wake day.
 * Editing bedtime / changing stages on the same night does not recompute a verdict.
 * A NEW night's insufficient coverage is not replaced by yesterday's classified risk.
 */
internal object SleepHypopneaData {
    private const val PREFS = "alke_completed_night_hypopnea_v2"
    private val completedRevision = MutableStateFlow(0)
    val revision: StateFlow<Int> = completedRevision

    /** Identity from a stable detected startTs, NOT user-edited effectiveStartTs. */
    internal data class NightResult(
        val identity: String?,
        val assessment: SleepHypopneaRiskEngine.Assessment,
    )

    internal fun visibleResult(previous: NightResult?, incoming: NightResult): NightResult {
        // A temporary empty session list during BLE offload is not evidence of
        // a different night. Keep the previously verified result until a fresh
        // identifiable night arrives.
        if (incoming.identity == null && previous?.assessment?.isReady == true) return previous
        // A *different* sleep night always replaces yesterday's risk, including
        // UNAVAILABLE or WAITING. Never carry an old LOW across this boundary.
        if (previous?.identity != incoming.identity) return incoming
        // Background refreshes for the SAME identified night cannot flash
        // LOW -> PENDING. A new ready result can still publish its first label.
        return if (previous?.assessment?.isReady == true && !incoming.assessment.isReady) previous else incoming
    }

    private fun dayKey(activeStrapId: String, wakeTs: Long): String =
        activeStrapId + ":" + localDayString(wakeTs)

    private fun resultKey(activeStrapId: String, wakeTs: Long, detectedStartTs: Long): String =
        "completed-v3:" + dayKey(activeStrapId, wakeTs) + ":" + detectedStartTs

    internal fun identity(activeStrapId: String, wakeTs: Long, detectedStartTs: Long): String =
        resultKey(activeStrapId, wakeTs, detectedStartTs)

    fun cached(
        context: Context,
        activeStrapId: String,
        wakeTs: Long,
        detectedStartTs: Long,
    ): SleepHypopneaRiskEngine.Assessment? {
        if (wakeTs <= 0L || detectedStartTs <= 0L) return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = resultKey(activeStrapId, wakeTs, detectedStartTs)
        var saved = prefs.getString(key, null)
        if (saved == null) {
            // One-time migration from v618's *day-only* cache, so a user who
            // already saw LOW does not lose it upon installing v619. Bind the
            // old verdict to exactly ONE detected session for that wake-day.
            // Never import the same old verdict for another same-day session.
            val migrated = "migration-v3:" + dayKey(activeStrapId, wakeTs)
            val old = prefs.getString("completed:" + dayKey(activeStrapId, wakeTs), null)
            val oldRisk = old?.let { runCatching { SleepHypopneaRiskEngine.Risk.valueOf(it) }.getOrNull() }
            if (!prefs.contains(migrated) && oldRisk in setOf(
                    SleepHypopneaRiskEngine.Risk.LOW,
                    SleepHypopneaRiskEngine.Risk.MEDIUM,
                    SleepHypopneaRiskEngine.Risk.HIGH,
                )
            ) {
                if (prefs.edit().putString(key, old).putString(migrated, key).commit()) {
                    saved = old
                }
            }
        }
        val risk = saved?.let { runCatching { SleepHypopneaRiskEngine.Risk.valueOf(it) }.getOrNull() }
        return if (risk == SleepHypopneaRiskEngine.Risk.LOW ||
                   risk == SleepHypopneaRiskEngine.Risk.MEDIUM ||
                   risk == SleepHypopneaRiskEngine.Risk.HIGH) {
            SleepHypopneaRiskEngine.Assessment(risk)
        } else null
    }

    private fun pinCompleted(
        context: Context,
        activeStrapId: String,
        wakeTs: Long,
        detectedStartTs: Long,
        assessment: SleepHypopneaRiskEngine.Assessment,
    ) {
        if (!assessment.isReady) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = resultKey(activeStrapId, wakeTs, detectedStartTs)
        // Same detected night stays immutable even when bedtime is edited.
        if (prefs.contains(key)) return
        if (prefs.edit().putString(key, assessment.risk.name).commit()) {
            completedRevision.value = completedRevision.value + 1
        }
    }

    /** The current canonical night, including a newly recorded insufficient night. */
    suspend fun latestNight(
        context: Context,
        repo: WhoopRepository,
        activeStrapId: String,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): NightResult = withContext(Dispatchers.IO) {
        val from = nowTs - 7L * 86_400L
        val imported = repo.sleepSessionsUnion(activeStrapId, from, nowTs)
        val computed = repo.computedSleepSessionsUnion(activeStrapId, from, nowTs)
        val merged = WhoopRepository.mergeSleepRichness(imported, computed) {
            localDayString(it.endTs)
        }
        // Read the SAME learned midsleep used by the Sleep hero. A shift worker
        // must not have one main-night candidate on Today and another on Sleep.
        val habitualMidsleep = try {
            repo.habitualMidsleepSec(activeStrapId)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Same cold-start fallback as SleepScreen; missing learned pattern
            // must never suppress a completed night entirely.
            null
        }
        val selected = merged.asSequence()
            .filter { it.endTs <= nowTs && it.endTs > it.effectiveStartTs }
            .groupBy { localDayString(it.endTs) }
            .toSortedMap(reverseOrder())
            .values.asSequence()
            .mapNotNull { blocks ->
                val main = mainSleepBlock(blocks, habitualMidsleep) ?: return@mapNotNull null
                val span = mainSleepSpan(blocks, habitualMidsleep) ?: return@mapNotNull null
                Triple(main.startTs, span.first, span.second)
            }
            // Skip a tiny nap-only day; a NEW main sleep of 1.5-3 hours is
            // still selected and truthfully reports insufficient coverage.
            // Do not fall back to yesterday just because 3h coverage failed.
            .firstOrNull { (_, onset, wake) -> wake - onset >= 90L * 60L }
        if (selected == null) {
            NightResult(null, SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.UNAVAILABLE))
        } else {
            val (detectedStart, onset, wake) = selected
            NightResult(
                identity(activeStrapId, wake, detectedStart),
                forWindow(context, repo, activeStrapId, onset, wake, nowTs, detectedStart),
            )
        }
    }

    /** Compatibility for any legacy caller not displaying the session identity. */
    suspend fun latest(
        context: Context,
        repo: WhoopRepository,
        activeStrapId: String,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): SleepHypopneaRiskEngine.Assessment =
        latestNight(context, repo, activeStrapId, nowTs).assessment

    suspend fun forWindow(
        context: Context,
        repo: WhoopRepository,
        activeStrapId: String,
        startTs: Long,
        endTs: Long,
        nowTs: Long = System.currentTimeMillis() / 1000L,
        detectedStartTs: Long = startTs,
    ): SleepHypopneaRiskEngine.Assessment = withContext(Dispatchers.IO) {
        if (endTs > nowTs - 600L) {
            return@withContext SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.WAITING)
        }
        if (startTs <= 0L || endTs - startTs !in 3L * 3600L..16L * 3600L) {
            return@withContext SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        }
        cached(context, activeStrapId, endTs, detectedStartTs)?.let { return@withContext it }

        // Original provenance thresholds and classifier are unchanged.
        val rr = repo.rrIntervalsUnion(activeStrapId, startTs, endTs, limit = 120_000)
        val hr = repo.hrBucketsUnion(activeStrapId, startTs, endTs, bucketSeconds = 5L)
        val gravity = repo.gravitySamplesUnion(activeStrapId, startTs, endTs, limit = 65_000)
        val resp = repo.respSamples(activeStrapId, startTs, endTs, limit = 65_000)
        val result = withContext(Dispatchers.Default) {
            SleepHypopneaRiskEngine.assess(startTs, endTs, nowTs, rr, hr, resp, gravity)
        }
        pinCompleted(context, activeStrapId, endTs, detectedStartTs, result)
        cached(context, activeStrapId, endTs, detectedStartTs) ?: result
    }
}

/** Tiny single-use navigation intent; Today points to the actual Sleep detail. */
internal object SleepHypopneaNavigation {
    private val pending = androidx.compose.runtime.mutableStateOf(false)
    val showDetailRequested: Boolean get() = pending.value

    fun requestDetail() { pending.value = true }
    fun consumeDetail(): Boolean {
        if (!pending.value) return false
        pending.value = false
        return true
    }
}
