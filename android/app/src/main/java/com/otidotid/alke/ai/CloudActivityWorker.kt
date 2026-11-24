package com.otidotid.alke.ai

import android.content.Context
import androidx.work.*
import com.otidotid.alke.AlkeApplication
import com.otidotid.alke.data.PersonalBackupBootstrap
import com.otidotid.alke.data.SecurePrefs
import com.otidotid.alke.data.WorkoutRow
import com.otidotid.alke.ui.ProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** One-time user consent plus personal bearer stored only in Android encrypted prefs. */
internal object CloudActivitySettings {
    private const val FILE = "alke_activity_secure_v1"
    private const val STATE = "alke_activity_state_v1"
    private fun secure(c: Context) = SecurePrefs.of(c,FILE)
    private fun state(c: Context) = c.applicationContext.getSharedPreferences(STATE,Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private val _dataRevision = MutableStateFlow(0L)
    val dataRevision = _dataRevision.asStateFlow()
    fun changed() { _dataRevision.value = _dataRevision.value + 1 }
    private fun configChanged() { _revision.value = _revision.value + 1 }
    fun token(c: Context): String? = runCatching {
        secure(c).takeIf { it.getBoolean("consent",false) }?.getString("token",null)?.takeIf { it.length >= 32 }
    }.getOrNull()
    fun enabled(c: Context) = token(c) != null
    fun configure(c: Context, token: String) {
        require(token.trim().length >= 32 && token.trim().none { it.isWhitespace() })
        check(secure(c).edit().putString("token",token.trim()).putBoolean("consent",true).commit())
        val now = System.currentTimeMillis()/1000L
        check(state(c).edit().putLong("cursor",now/900L*900L-900L).putString("status","Waiting for cloud analysis").commit())
        configChanged()
        CloudActivityScheduler.ensure(c)
        CloudActivityScheduler.request(c)
    }
    fun disable(c: Context) {
        check(secure(c).edit().remove("token").putBoolean("consent",false).commit())
        CloudActivityScheduler.cancel(c)
        status(c,"Cloud detection off")
        configChanged()
    }
    fun cursor(c: Context) = state(c).getLong("cursor",System.currentTimeMillis()/900000L*900L-900L)
    fun advance(c: Context, next: Long) { check(state(c).edit().putLong("cursor",next).commit()) }
    fun status(c: Context) = state(c).getString("status","Cloud detection off") ?: "Cloud detection off"
    fun status(c: Context, value: String) {
        if (status(c) != value) { state(c).edit().putString("status",value).apply(); configChanged() }
    }
}

internal object CloudActivityScheduler {
    private const val PERIODIC = "alke-activity-cloud-periodic"
    private const val TAG = "alke-activity-cloud"
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun ensure(c: Context) {
        if (!CloudActivitySettings.enabled(c)) return
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CloudActivityWorker>(15,TimeUnit.MINUTES)
                .setConstraints(online).addTag(TAG).build())
    }
    fun request(c: Context, delay: Long = 0) {
        if (!CloudActivitySettings.enabled(c)) return
        val cursor = CloudActivitySettings.cursor(c)
        WorkManager.getInstance(c).enqueueUniqueWork("alke-activity-window-$cursor",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudActivityWorker>().setInitialDelay(delay,TimeUnit.SECONDS)
                .setConstraints(online).addTag(TAG).build())
    }
    fun cancel(c: Context) { WorkManager.getInstance(c).cancelAllWorkByTag(TAG) }
}

/** Only aggregates measured samples; classification is performed exclusively by Cloudflare/Groq. */
class CloudActivityWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    companion object { private val gate = Mutex() }
    override suspend fun doWork(): Result = gate.withLock {
        withContext(Dispatchers.IO) {
            val c = applicationContext
            val token = CloudActivitySettings.token(c) ?: return@withContext Result.success()
            if (PersonalBackupBootstrap.needsInitialRestore(c)) return@withContext Result.success()
            val app = c as? AlkeApplication ?: return@withContext Result.failure()
            val repo = app.repository
            val id = app.activeDeviceId
            if (id.isBlank()) return@withContext Result.success()
            val now = System.currentTimeMillis()/1000L
            val latestEnd = now/900L*900L
            var from = maxOf(CloudActivitySettings.cursor(c),(now-6*86400L)/900L*900L)
            val profile = ProfileStore.from(c)
            try {
                // A bounded number of expired empty windows may be skipped without using Groq quota.
                repeat(12) {
                    val to = from+CloudActivityProtocol.WINDOW
                    if (to > latestEnd) return@withContext Result.success()
                    val known = repo.cloudActivityExclusions(id,from,to)
                    val day = Instant.ofEpochSecond(from).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    val resting = repo.dayMerged(id,day)?.restingHr
                    val bins = CloudActivityProtocol.bins(from,
                        repo.hrSamplesUnion(id,from,to-1,1800),
                        repo.gravitySamplesUnion(id,from-1,to-1,1800),
                        repo.stepSamples(id,from-1,to-1,1800),profile.stepTicksPerStep)
                    val excluded = CloudActivityProtocol.excludes(known,from,to)
                    if (excluded.size == 1 && excluded[0].first <= from && excluded[0].second >= to) {
                        CloudActivitySettings.advance(c,to)
                        from = to
                        return@repeat
                    }
                    val eligible = bins.indices.count { index ->
                        val a = from+index*30L
                        excluded.none { CloudActivityProtocol.overlap(a,a+30,it.first,it.second) }
                    }
                    val usable = bins.indices.count { index ->
                        val a = from+index*30L
                        CloudActivityProtocol.usable(bins[index]) && excluded.none { CloudActivityProtocol.overlap(a,a+30,it.first,it.second) }
                    }
                    if (usable < kotlin.math.ceil(eligible*0.7).toInt() && now-to < 7200L) {
                        CloudActivitySettings.status(c,"Waiting for wearable data")
                        return@withContext Result.success()
                    }
                    if (usable < 2) {
                        // An offload can arrive late. Leave the current cursor pending for two hours.
                        if (now-to < 7200L) {
                            CloudActivitySettings.status(c,"Waiting for wearable data")
                            return@withContext Result.success()
                        }
                        CloudActivitySettings.advance(c,to)
                        from = to
                    } else {
                        val snapshot = CloudActivityProtocol.Snapshot(from,bins,resting,excluded)
                        if (CloudActivitySettings.token(c) != token) return@withContext Result.success()
                        val result = CloudActivityClient().detect(snapshot,token)
                        if (CloudActivitySettings.token(c) != token) return@withContext Result.success()
                        val reply = result.response
                        if (reply == null) {
                            val message = when(result.code) {
                                401,403 -> "Check Activity access token"
                                429 -> "Cloud quota reached · retry later"
                                503 -> "Cloud unavailable or disabled"
                                else -> "Cloud response unavailable"
                            }
                            CloudActivitySettings.status(c,message)
                            // Periodic job retries on next scheduled run; avoid quota-blind rapid retries.
                            return@withContext Result.success()
                        }
                        if (reply.status == "INSUFFICIENT_DATA" && now-to < 7200L) {
                            CloudActivitySettings.status(c,"Waiting for wearable data")
                            return@withContext Result.success()
                        }
                        val added = repo.saveCloudActivityEvents(id,reply.events)
                        CloudActivitySettings.advance(c,to)
                        CloudActivitySettings.status(c,if (reply.status == "COMPLETE")
                            "Cloud analysis complete · $added new activities" else "Insufficient sensor data")
                        if (to < latestEnd) CloudActivityScheduler.request(c,65)
                        return@withContext Result.success()
                    }
                }
                CloudActivitySettings.status(c,"Waiting for wearable data")
                if (from < latestEnd) CloudActivityScheduler.request(c,65)
                Result.success()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                CloudActivitySettings.status(c,"Cloud analysis unavailable · retry later")
                Result.success()
            }
        }
    }
}
