package com.otidotid.alke.ai

import android.content.Context
import androidx.work.*
import com.otidotid.alke.AlkeApplication
import com.otidotid.alke.data.PersonalBackupBootstrap
import com.otidotid.alke.data.SecurePrefs
import com.otidotid.alke.ui.ProfileStore
import com.otidotid.alke.ui.ActiveWorkoutStore
import com.otidotid.alke.location.GpsSession
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
    fun sensitivity(c: Context) = ActivitySensitivity.fromStored(state(c).getString("sensitivity",null))
    fun setSensitivity(c: Context, value: ActivitySensitivity) {
        if (sensitivity(c) != value) {
            check(state(c).edit().putString("sensitivity",value.name).commit())
            configChanged()
            CloudActivityScheduler.request(c)
        }
    }
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
        check(state(c).edit().remove(RETRIES).remove("last_deferred").commit())
        status(c,"Cloud detection off")
        configChanged()
    }
    fun cursor(c: Context) = state(c).getLong("cursor",System.currentTimeMillis()/1000L/900L*900L-900L)
    private const val RETRIES = "deferred_activity_windows_v2"
    fun retries(c: Context, now: Long) = ActivityRetryPolicy.decode(state(c).getString(RETRIES,null),now)
    fun lastDeferred(c: Context) = state(c).getBoolean("last_deferred",false)
    fun noteSelection(c: Context, deferred: Boolean) { check(state(c).edit().putBoolean("last_deferred",deferred).commit()) }
    fun finishWindow(c: Context, start: Long, deferred: Boolean, now: Long) {
        val editor = state(c).edit()
        if (!deferred) editor.putLong("cursor",start+900L)
        editor.putString(RETRIES,ActivityRetryPolicy.encode(retries(c,now).filterNot { it.start == start }))
        check(editor.commit())
    }
    fun deferWindow(c: Context, start: Long, deferred: Boolean, now: Long) {
        val items = retries(c,now)
        val next = ActivityRetryPolicy.fail(items.firstOrNull { it.start == start },start,now)
        val editor = state(c).edit().putString(RETRIES,ActivityRetryPolicy.encode(items.filterNot { it.start == start } + next))
        if (!deferred) editor.putLong("cursor",start+900L)
        check(editor.commit())
        configChanged()
    }
    /** A late offload is not a provider error and must not block newer windows. */
    fun deferSensorWindow(c: Context, start: Long, deferred: Boolean, now: Long) {
        val items = retries(c,now)
        val next = ActivityRetryPolicy.waitForSensor(start,now)
        val editor = state(c).edit().putString(RETRIES,ActivityRetryPolicy.encode(items.filterNot { it.start == start } + next))
        if (!deferred) editor.putLong("cursor",start+900L)
        check(editor.commit())
        configChanged()
    }
    fun hasParked(c: Context) = retries(c,System.currentTimeMillis()/1000L).any { it.failures >= ActivityRetryPolicy.MAX_FAILURES }
    fun retryParked(c: Context) {
        val now = System.currentTimeMillis()/1000L
        check(state(c).edit().putString(RETRIES,ActivityRetryPolicy.encode(ActivityRetryPolicy.retryParked(retries(c,now),now))).commit())
        configChanged()
        CloudActivityScheduler.request(c)
    }
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
            fun manualRecordingActive(): Boolean =
                ActiveWorkoutStore.from(c).hasSnapshot() || GpsSession.state.value.active
            // Leave pending sensor windows untouched until the existing manual recorder finishes.
            if (manualRecordingActive()) {
                CloudActivitySettings.status(c,"Manual recording in progress · cloud analysis paused")
                return@withContext Result.success()
            }
            val repo = app.repository
            val id = app.activeDeviceId
            if (id.isBlank()) return@withContext Result.success()
            val now = System.currentTimeMillis()/1000L
            val latestEnd = now/900L*900L
            val forward = maxOf(CloudActivitySettings.cursor(c),(now-ActivityRetryPolicy.RETENTION)/900L*900L)
            val due = ActivityRetryPolicy.due(CloudActivitySettings.retries(c,now),now,latestEnd)
            val retry = due?.takeIf { forward >= latestEnd || !CloudActivitySettings.lastDeferred(c) }
            val deferred = retry != null
            var from = retry?.start ?: forward
            val profile = ProfileStore.from(c)
            try {
                // A bounded number of expired empty windows may be skipped without using Groq quota.
                repeat(if (deferred) 1 else 12) {
                    val to = from+CloudActivityProtocol.WINDOW
                    if (to > latestEnd) return@withContext Result.success()
                    CloudActivitySettings.noteSelection(c,deferred)
                    val known = repo.cloudActivityExclusions(id,from,to)
                    val day = Instant.ofEpochSecond(from).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    val resting = repo.dayMerged(id,day)?.restingHr
                    val bins = CloudActivityProtocol.bins(from,
                        repo.hrSamplesUnion(id,from,to-1,1800),
                        repo.gravitySamplesUnion(id,from-1,to-1,1800),
                        repo.stepSamples(id,from-1,to-1,1800),profile.stepTicksPerStep)
                    val excluded = CloudActivityProtocol.excludes(known,from,to)
                    if (excluded.size == 1 && excluded[0].first <= from && excluded[0].second >= to) {
                        CloudActivitySettings.finishWindow(c,from,deferred,now)
                        from = to
                        return@repeat
                    }
                    val usable = bins.indices.count { index ->
                        val a = from+index*30L
                        CloudActivityProtocol.usable(bins[index]) && excluded.none { CloudActivityProtocol.overlap(a,a+30,it.first,it.second) }
                    }
                    // The Worker can analyze two usable bins; keep the strict per-bin evidence
                    // contract, but never demand 70% coverage of the entire 15-minute window.
                    if (!ActivityRetryPolicy.canSubmit(usable)) {
                        if (ActivityRetryPolicy.awaitLateSensor(now,to)) {
                            CloudActivitySettings.deferSensorWindow(c,from,deferred,now)
                            CloudActivitySettings.status(c,"Waiting for wearable data")
                        } else {
                            CloudActivitySettings.finishWindow(c,from,deferred,now)
                            CloudActivitySettings.status(c,"Insufficient sensor data")
                        }
                        from = to
                    } else {
                        val sensitivity = CloudActivitySettings.sensitivity(c)
                        val snapshot = CloudActivityProtocol.Snapshot(from,bins,resting,excluded,sensitivity)
                        if (CloudActivitySettings.token(c) != token) return@withContext Result.success()
                        CloudActivitySettings.status(c,"Waiting for cloud analysis")
                        val result = CloudActivityClient().detect(snapshot,token)
                        if (CloudActivitySettings.token(c) != token) return@withContext Result.success()
                        val reply = result.response
                        if (reply == null) {
                            val message = when(result.code) {
                                400 -> if (sensitivity != ActivitySensitivity.MEDIUM) "Sensitivity requires updated cloud Worker" else "Cloud snapshot unavailable"
                                401,403 -> "Check Activity access token"
                                429 -> "Cloud quota reached · retry later"
                                503 -> "Cloud unavailable or disabled"
                                else -> "Cloud response unavailable"
                            }
                            CloudActivitySettings.status(c,message)
                            if (ActivityRetryPolicy.isWindowFailure(result.code)) {
                                if (CloudActivitySettings.token(c) != token || manualRecordingActive() ||
                                    CloudActivitySettings.sensitivity(c) != sensitivity) return@withContext Result.success()
                                // Persist failed window and cursor in one commit; other windows can continue.
                                CloudActivitySettings.deferWindow(c,from,deferred,now)
                                if (CloudActivitySettings.cursor(c) < latestEnd) CloudActivityScheduler.request(c,65)
                            }
                            // Global failures (auth, quota, provider outage) leave the cursor intact.
                            return@withContext Result.success()
                        }
                        if (reply.status == "INSUFFICIENT_DATA" && ActivityRetryPolicy.awaitLateSensor(now,to)) {
                            if (CloudActivitySettings.token(c) != token || CloudActivitySettings.sensitivity(c) != sensitivity || manualRecordingActive()) return@withContext Result.success()
                            CloudActivitySettings.deferSensorWindow(c,from,deferred,now)
                            CloudActivitySettings.status(c,"Waiting for wearable data")
                            if (CloudActivitySettings.cursor(c) < latestEnd) CloudActivityScheduler.request(c,65)
                            return@withContext Result.success()
                        }
                        if (CloudActivitySettings.token(c) != token || CloudActivitySettings.sensitivity(c) != sensitivity || manualRecordingActive()) return@withContext Result.success()
                        // Recheck active device as offloads can adopt a new strap identity mid-request.
                        if (app.activeDeviceId != id) return@withContext Result.success()
                        val added = repo.saveCloudActivityEvents(id,reply.events)
                        CloudActivitySettings.finishWindow(c,from,deferred,now)
                        CloudActivitySettings.status(c,if (reply.status == "COMPLETE")
                            "Cloud analysis complete · $added new activities" else "Insufficient sensor data")
                        if (CloudActivitySettings.cursor(c) < latestEnd) CloudActivityScheduler.request(c,65)
                        return@withContext Result.success()
                    }
                }
                // Keep the most recent explicit status; do not overwrite successful analysis.
                if (CloudActivitySettings.cursor(c) < latestEnd) CloudActivityScheduler.request(c,65)
                Result.success()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                CloudActivitySettings.status(c,"Cloud analysis unavailable · retry later")
                Result.success()
            }
        }
    }
}
