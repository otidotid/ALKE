package com.otidotid.alke.ui

import com.otidotid.alke.analytics.AnalyticsEngine
import com.otidotid.alke.analytics.SleepHypopneaRiskEngine
import com.otidotid.alke.data.WhoopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * Read-only nightly screening. Called only after completed sleep/sync from the two
 * sleep surfaces. No daytime polling, Room migration, new persistence, or network.
 * The existing merged sleep owner and scoring-safe R-R readers remain authoritative.
 */
internal object SleepHypopneaData {
    suspend fun latest(
        repo: WhoopRepository,
        activeStrapId: String,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): SleepHypopneaRiskEngine.Assessment = withContext(Dispatchers.IO) {
        val from = nowTs - 7L * 86_400L
        val imported = repo.sleepSessionsUnion(activeStrapId, from, nowTs)
        val computed = repo.computedSleepSessionsUnion(activeStrapId, from, nowTs)
        fun wakeDay(ts: Long): String {
            val offset = TimeZone.getDefault().getOffset(ts * 1000L).toLong() / 1000L
            return AnalyticsEngine.dayString(ts, offset)
        }
        val latest = WhoopRepository.mergeSleepRichness(imported, computed) { wakeDay(it.endTs) }
            .filter { it.endTs <= nowTs && it.endTs > it.effectiveStartTs }
            .maxByOrNull { it.endTs }
        if (latest == null) SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        else forWindow(repo, activeStrapId, latest.effectiveStartTs, latest.endTs, nowTs)
    }

    suspend fun forWindow(
        repo: WhoopRepository,
        activeStrapId: String,
        startTs: Long,
        endTs: Long,
        nowTs: Long = System.currentTimeMillis() / 1000L,
    ): SleepHypopneaRiskEngine.Assessment = withContext(Dispatchers.IO) {
        // Check bounds BEFORE accessing raw sensor rows; active sleep is never processed.
        if (endTs > nowTs - 600L) {
            return@withContext SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.WAITING)
        }
        if (startTs <= 0L || endTs - startTs !in 3L * 3600L..16L * 3600L) {
            return@withContext SleepHypopneaRiskEngine.Assessment(SleepHypopneaRiskEngine.Risk.UNAVAILABLE)
        }
        // The scoring-safe R-R union rejects SPO2_IBI duplicate transport, timestamp
        // quarantine and WHOOP 5/MG legacy fill beats. Never use rawRrIntervals.
        val rr = repo.rrIntervalsUnion(activeStrapId, startTs, endTs, limit = 120_000)
        val hr = repo.hrBucketsUnion(activeStrapId, startTs, endTs, bucketSeconds = 5L)
        val gravity = repo.gravitySamplesUnion(activeStrapId, startTs, endTs, limit = 65_000)
        // Raw respiration is only an auxiliary provenance signal (not calibrated
        // breathing amplitude and not an apnea event detector).
        val resp = repo.respSamples(activeStrapId, startTs, endTs, limit = 65_000)
        withContext(Dispatchers.Default) {
            SleepHypopneaRiskEngine.assess(startTs, endTs, nowTs, rr, hr, resp, gravity)
        }
    }
}

/** Tiny single-use navigation intent; Today points to the actual Sleep detail. */
internal object SleepHypopneaNavigation {
    var showDetailRequested by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun requestDetail() { showDetailRequested = true }
    fun consumeDetail(): Boolean {
        if (!showDetailRequested) return false
        showDetailRequested = false
        return true
    }
}
