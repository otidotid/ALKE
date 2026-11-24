package com.otidotid.alke.ui

import android.content.Context
import com.otidotid.alke.analytics.SleepHypopneaRiskEngine
import com.otidotid.alke.data.WhoopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Read-only completed-night screening. A classified main-night result is immutable
 * for that local wake-day, including across process death and further daytime syncs.
 * The next wake-day is a different cache key. No Room migration or online calls.
 *
 * Both Today and Sleep use this SAME store; a Sleep result publishes a lightweight
 * revision so Today can update immediately without a per-heartbeat observer.
 */
internal object SleepHypopneaData {
    private const val PREFS = "alke_completed_night_hypopnea_v2"
    private val completedRevision = MutableStateFlow(0)
    val revision: StateFlow<Int> = completedRevision

    // A morning's first computed verdict stays locked on purpose. The recorded
    // canonical span is provenance, NOT a reason to recalculate after an edit.
    internal data class LatestNight(
        val wakeDay: String?,
        val startTs: Long?,
        val endTs: Long?,
        val assessment: SleepHypopneaRiskEngine.Assessment,
    )

    private fun resultKey(activeStrapId: String, wakeTs: Long): String =
        "completed:" + activeStrapId + ":" + localDayString(wakeTs)

    fun cached(
        context: Context,
        activeStrapId: String,
        wakeTs: Long,
    ): SleepHypopneaRiskEngine.Assessment? {
        if (wakeTs <= 0L) return null
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(resultKey(activeStrapId, wakeTs), null) ?: return null
        val risk = runCatching { SleepHypopneaRiskEngine.Risk.valueOf(saved.substringBefore('|')) }.getOrNull()
            ?: return null
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
        assessment: SleepHypopneaRiskEngine.Assessment,
        startTs: Long,
    ) {
        if (!assessment.isReady) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = resultKey(activeStrapId, wakeTs)
        // Never revise a completed wake-day during an unrelated sync. Only the
        // following morning (a different key) can produce a new classification.
        if (prefs.contains(key)) return
        if (prefs.edit().putString(key, "${assessment.risk.name}|$startTs|$wakeTs").commit()) {
            completedRevision.value = completedRevision.value + 1
        }
    }

    // Keep the historical Assessment API for legacy callers; the REAL ALKE
    // Today uses latestNight() to avoid carrying yesterday's risk into a new
    // wake-day with missing/insufficient records.
    suspend fun latest(
        context: Context,
        repo: WhoopRepository,
        activeStrapId: String,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): SleepHypopneaRiskEngine.Assessment =
        latestNight(context, repo, activeStrapId, nowTs = nowTs).assessment

    suspend fun latestNight(
        context: Context,
        repo: WhoopRepository,
        activeStrapId: String,
        habitualMidsleepSec: Long? = null,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): LatestNight = withContext(Dispatchers.IO) {
        val from = nowTs - 7L * 86_400L
        val imported = repo.sleepSessionsUnion(activeStrapId, from, nowTs)
        val computed = repo.computedSleepSessionsUnion(activeStrapId, from, nowTs)
        val merged = WhoopRepository.mergeSleepRichness(imported, computed) {
            localDayString(it.endTs)
        }
        val latestDay = merged.asSequence()
            .filter { it.endTs <= nowTs && it.endTs > it.effectiveStartTs }
            .groupBy { localDayString(it.endTs) }
            .toSortedMap(reverseOrder())
            .entries.firstOrNull()
        if (latestDay == null) {
            return@withContext LatestNight(
                null, null, null,
                SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.UNAVAILABLE),
            )
        }
        // Critically: DO NOT skip an invalid new morning and silently fall
        // back to a prior day's successful LOW/MEDIUM/HIGH assessment.
        // Main-night selection uses the same learned midsleep as Sleep's hero.
        val mainNight = mainSleepSpan(
            latestDay.value.sortedBy { it.effectiveStartTs },
            habitualMidsleepSec,
        )
        val risk = if (mainNight == null) {
            SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        } else {
            forWindow(
                context, repo, activeStrapId,
                mainNight.first, mainNight.second, nowTs,
            )
        }
        LatestNight(
            latestDay.key,
            mainNight?.first,
            mainNight?.second,
            risk,
        )
    }

    suspend fun forWindow(
        context: Context,
        repo: WhoopRepository,
        activeStrapId: String,
        startTs: Long,
        endTs: Long,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): SleepHypopneaRiskEngine.Assessment = withContext(Dispatchers.IO) {
        if (endTs > nowTs - 600L) {
            return@withContext SleepHypopneaRiskEngine.Assessment(
                SleepHypopneaRiskEngine.Risk.WAITING,
            )
        }
        if (startTs <= 0L || endTs - startTs !in 3L * 3600L..16L * 3600L) {
            return@withContext SleepHypopneaRiskEngine.Assessment(
                SleepHypopneaRiskEngine.Risk.UNAVAILABLE,
            )
        }
        // Pinned classifications take priority over ongoing daytime BLE offload.
        cached(context, activeStrapId, endTs)?.let { return@withContext it }

        // Keep the scoring-safe provenance and artifact exclusions unchanged.
        val rr = repo.rrIntervalsUnion(activeStrapId, startTs, endTs, limit = 120_000)
        val hr = repo.hrBucketsUnion(activeStrapId, startTs, endTs, bucketSeconds = 5L)
        val gravity = repo.gravitySamplesUnion(activeStrapId, startTs, endTs, limit = 65_000)
        val resp = repo.respSamples(activeStrapId, startTs, endTs, limit = 65_000)
        val result = withContext(Dispatchers.Default) {
            SleepHypopneaRiskEngine.assess(startTs, endTs, nowTs, rr, hr, resp, gravity)
        }
        pinCompleted(context, activeStrapId, endTs, result, startTs)
        cached(context, activeStrapId, endTs) ?: result
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
