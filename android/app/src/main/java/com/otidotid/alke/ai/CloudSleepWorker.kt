package com.otidotid.alke.ai

import android.content.Context
import androidx.work.*
import com.otidotid.alke.AlkeApplication
import com.otidotid.alke.data.CloudSleepLedger
import com.otidotid.alke.data.PersonalBackupBootstrap
import com.otidotid.alke.data.SecurePrefs
import com.otidotid.alke.ui.ActiveWorkoutStore
import com.otidotid.alke.location.GpsSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal object CloudSleepSettings {
    private const val TAG = "alke-cloud-sleep-v1"
    private val configGate = Mutex()
    private val refresh = MutableStateFlow(0L)
    val revision = refresh.asStateFlow()
    private fun secure(c: Context) = SecurePrefs.of(c, "alke_sleep_secure_v1")
    fun state(c: Context) = c.getSharedPreferences("alke_sleep_state_v1", Context.MODE_PRIVATE)
    fun token(c: Context, id: String): String? = runCatching {
        val prefs = secure(c)
        if (prefs.getString("device", null) == id) prefs.getString("token", null) else null
    }.getOrNull()
    fun status(c: Context): String = state(c).getString("status", null) ?: "Off"
    fun status(c: Context, message: String) {
        if (status(c) != message) {
            state(c).edit().putString("status", message).apply()
            refresh.update { it + 1 }
        }
    }

    suspend fun configure(app: AlkeApplication, id: String, value: String?) = configGate.withLock {
        val ledger = requireNotNull(app.repository.cloudSleep)
        val now = System.currentTimeMillis() / 1000
        require(id.isNotBlank() && app.activeDeviceId == id)
        val prefs = secure(app)
        val previous = prefs.getString("device", null)
        if (value != null) require(value.length >= 32 && value.none(Char::isWhitespace))
        if (previous != null && previous != id) ledger.configure(previous, false, now)
        if (value == null) {
            check(prefs.edit().clear().commit())
            ledger.configure(id, false, now)
            WorkManager.getInstance(app).cancelAllWorkByTag(TAG)
            status(app, "Off")
        } else {
            ledger.configure(id, true, now)
            check(prefs.edit().putString("device", id).putString("token", value).commit())
            state(app).edit().remove("lastAttempt").apply()
            status(app, "Waiting for wearable data")
            schedule(app, immediate = true)
        }
        refresh.update { it + 1 }
    }

    fun schedule(c: Context, immediate: Boolean) {
        val app = c.applicationContext as? AlkeApplication ?: return
        if (token(c, app.activeDeviceId) == null) return
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CloudSleepWorker>(1,TimeUnit.HOURS).setConstraints(online).addTag(TAG).build())
        if (immediate) wm.enqueueUniqueWork("$TAG-offload", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudSleepWorker>().setConstraints(online).addTag(TAG).build())
    }
}

class CloudSleepWorker(c: Context, params: WorkerParameters): CoroutineWorker(c, params) {
    override suspend fun doWork(): Result = gate.withLock {
        withContext(Dispatchers.IO) {
            val app = applicationContext as? AlkeApplication ?: return@withContext Result.failure()
            if (PersonalBackupBootstrap.needsInitialRestore(app)) return@withContext Result.success()
            val device = app.activeDeviceId
            val token = CloudSleepSettings.token(app, device) ?: return@withContext Result.success()
            val repository = app.repository
            val ledger = repository.cloudSleep ?: return@withContext Result.failure()
            fun current() = app.activeDeviceId == device && CloudSleepSettings.token(app, device) == token &&
                !GpsSession.state.value.active && !ActiveWorkoutStore.from(app).hasSnapshot()
            if (!current()) return@withContext Result.success()
            val now = System.currentTimeMillis() / 1000
            val state = CloudSleepSettings.state(app)
            if (now - state.getLong("lastAttempt", 0L) in 0 until 3600) return@withContext Result.success()
            try {
                val end = now / CloudSleepProtocol.BIN * CloudSleepProtocol.BIN
                val mode = ledger.history(device, end - CloudSleepLedger.DAY, end).modes.lastOrNull()
                if (mode?.enabled != true) return@withContext Result.success()
                val firstBin = (mode.time + CloudSleepProtocol.BIN - 1) / CloudSleepProtocol.BIN * CloudSleepProtocol.BIN
                val from = maxOf(firstBin, end - CloudSleepLedger.DAY)
                if (end - from < 3 * CloudSleepProtocol.BIN) return@withContext Result.success()
                val exclusions = ledger.exclusions(device, from, end)
                val snapshot = CloudSleepEvidence.read(repository, device, from, end,
                    TimeZone.getDefault().getOffset(from * 1000) / 60000,
                    repository.habitualMidsleepSec(device, 30), exclusions)
                if (!snapshot.bins.windowed(3).any { group -> group.all(CloudSleepProtocol::usable) }) {
                    CloudSleepSettings.status(app, "Waiting for wearable data")
                    return@withContext Result.success()
                }
                if (!current()) return@withContext Result.success()
                state.edit().putLong("lastAttempt", now).apply()
                val result = CloudSleepClient().detect(snapshot, token, consent = true)
                if (!current()) return@withContext Result.success()
                val response = result.response
                val status = when {
                    response == null -> when (result.code) {
                        401, 403 -> "Check Sleep access token"
                        404, 503 -> "Sleep Worker unavailable"
                        429 -> "Cloud quota reached"
                        else -> "Sleep analysis unavailable"
                    }
                    response.status == "INSUFFICIENT_DATA" -> "Insufficient wearable data"
                    else -> {
                        val added = ledger.save(device, snapshot, response)
                        if (added > 0) "New sleep episodes recorded" else "Sleep analysis complete"
                    }
                }
                CloudSleepSettings.status(app, status)
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CloudSleepSettings.status(app, "Sleep analysis unavailable")
                Result.retry()
            }
        }
    }
    companion object { private val gate = Mutex() }
}
