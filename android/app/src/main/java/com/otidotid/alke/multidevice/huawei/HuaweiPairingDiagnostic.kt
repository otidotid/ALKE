package com.otidotid.alke.multidevice.huawei

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Serialized connection owner; holds the authenticated key only while the socket is active. */
@SuppressLint("MissingPermission")
internal object HuaweiPairingDiagnostic {
    private val attemptLock = Mutex()
    const val SESSION_ROTATION = "Huawei session rotation requested."
    const val SESSION_LINK_LOST = "Huawei fitness session Bluetooth link lost."
    private class HuaweiSessionRotation : IOException()

    suspend fun run(context: Context, device: BluetoothDevice, allowFirstBind: Boolean,
        onStatus: (Int?, Boolean) -> Unit = { _, _ -> },
        onVerified: (String) -> Unit = {}): String = attemptLock.withLock {
        coroutineScope {
            val link = HuaweiRfcommTransport(device)
            val closeOnCancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { link.close() }
            }
            try {
                withContext(Dispatchers.IO) {
                    val attemptContext = coroutineContext
                    attempt(context, device, link, allowFirstBind, onStatus, onVerified) { attemptContext.ensureActive() }
                }
            } finally {
                link.close()
                closeOnCancellation.cancel()
            }
        }
    }

    private fun attempt(context: Context, device: BluetoothDevice, link: HuaweiRfcommTransport,
        allowFirstBind: Boolean, onStatus: (Int?, Boolean) -> Unit,
        onVerified: (String) -> Unit, ensureActive: () -> Unit): String {
        val expired = AtomicBoolean(false)
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val timeout = watchdog.schedule({
            expired.set(true)
            link.close()
        }, 90, TimeUnit.SECONDS)
        fun checkActive() {
            ensureActive()
            if (expired.get()) throw IOException("Huawei pairing timeout")
        }
        val transport = HuaweiHiChainTransport(link, ::checkActive)
        val protocol = HuaweiPairingProtocol(transport)
        var binding: HuaweiCredentialStore.Binding? = null
        var checkingBinding = true
        var sessionStarted = false
        var fitnessStore: HuaweiFitnessStore? = null
        try {
            require(HuaweiRfcommTransport.isGt5Name(device.name))
            require(device.bondState == BluetoothDevice.BOND_BONDED)
            val store = HuaweiCredentialStore(context.applicationContext)
            val address = device.address
            val deviceKey = HuaweiFitnessStore.deviceKey(address)
            val pending = store.loadPending(address)
            binding = pending ?: store.load(address)
            if (binding == null && !allowFirstBind) return "First-time binding requires your confirmation."
            val identity = binding?.identity ?: UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT)
            val model = Build.MODEL.take(30).ifBlank { "Android" }
            checkingBinding = false
            link.connect()
            protocol.authenticate(identity, model, binding?.credential, pending != null, allowFirstBind,
                savePending = { credential ->
                    checkActive()
                    HuaweiCredentialStore.Binding(identity, credential.copyOf()).use {
                        store.savePendingBinding(address, it)
                    }
                },
                saveVerified = { credential ->
                    checkActive()
                    HuaweiCredentialStore.Binding(identity, credential.copyOf()).use {
                        store.saveVerifiedBinding(address, it)
                    }
                }, deviceName = device.name ?: "Huawei Watch GT 5", onSession = { info, fitness ->
                    timeout.cancel(false)
                    checkActive()
                    sessionStarted = true
                    onStatus(info.batteryPercent, true)
                    val batteryMode = if (info.batteryEncrypted) "GCM" else "plaintext"
                    val connect = when {
                        info.initialization?.connectSupported != true -> "not advertised"
                        !info.connectStatusAcknowledged -> "no successful reply"
                        info.connectStatusEncrypted -> "GCM reply"
                        else -> "plaintext reply"
                    }
                    val header = "Huawei authenticated (GCM ProductInfo). Battery at connection: ${info.batteryPercent}% ($batteryMode). ConnectStatus: $connect. Watch UI not yet verified."
                    val notes = info.initialization?.notes.orEmpty().joinToString("\n")
                    onVerified("$header\nReading advertised fitness capabilities.\n$notes")
                    // Keep the session alive and read only known, advertised commands.
                    // No automatic sensor changes, agreement acceptance or WHOOP switching.
                    val localStore = HuaweiFitnessStore(context.applicationContext)
                    fitnessStore = localStore
                    HuaweiTimelineSource.selectVerifiedDevice(context.applicationContext, deviceKey)
                    val sessionStart = android.os.SystemClock.elapsedRealtime()
                    var charge = info.batteryPercent
                    var nextBattery = android.os.SystemClock.elapsedRealtime() + 300_000L
                    var nextHistory = 0L
                    var backfillEnd = 0L
                    var olderWindows = 0
                    var historyCount: Int? = null
                    var sourcePreview = "HR source comparison unavailable."
                    var historySummary = "Sleep and workout not yet read."
                    var historyRead = false
                    while (true) {
                        checkActive()
                        if (android.os.SystemClock.elapsedRealtime() - sessionStart >= 30 * 60 * 1000L)
                            throw HuaweiSessionRotation()
                        onStatus(charge, true)
                        val elapsed = android.os.SystemClock.elapsedRealtime()
                        if (elapsed >= nextBattery) {
                            charge = fitness.batteryPercent()
                            nextBattery = elapsed + 300_000L
                            onStatus(charge, true)
                        }
                        val before = System.currentTimeMillis()
                        val zone = java.time.ZoneId.systemDefault()
                        val day = java.time.Instant.ofEpochMilli(before).atZone(zone).toLocalDate()
                        val totals = if (fitness.supports(HuaweiFitnessRead.TOTALS)) fitness.totals() else null
                        val now = System.currentTimeMillis() / 1000
                        if (totals != null && java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate() == day)
                            localStore.saveTotals(deviceKey, day.toString(), now, totals)
                        if (now >= nextHistory && fitness.supports(HuaweiFitnessRead.COUNT) &&
                            fitness.supports(HuaweiFitnessRead.PAGE)) {
                            // A bounded recent window, with no invented intervals or cursor advancement.
                            val end = now - now % 60
                            val start = end - 3600
                            val minutes = fitness.minutes(start, end)
                            localStore.saveMinutes(deviceKey, start, end, minutes)
                            val stored = localStore.minutes(deviceKey, start, end)
                            historyCount = stored.size
                            if (backfillEnd == 0L) backfillEnd = start
                            // Read-only source selection on minute windows. Do not alter native WHOOP scoring.
                            sourcePreview = runCatching {
                                val app = context.applicationContext as com.otidotid.alke.AlkeApplication
                                val buckets = kotlinx.coroutines.runBlocking {
                                    app.repository.hrBucketsUnion(app.activeDeviceId, start, end - 1, 60)
                                }
                                val chosen = HuaweiHrSelection.select(stored,
                                    buckets.map { HuaweiHrSelection.WhoopMinute(it.bucket, it.avgBpm) })
                                val primary = chosen.count { it.source == com.otidotid.alke.multidevice.WearableSourcePolicy.Source.HUAWEI }
                                "HR source preview: Huawei $primary, WHOOP ${chosen.size - primary} minute windows."
                            }.getOrDefault("HR source preview unavailable.")
                            nextHistory = now + 900
                        }
                        // One completed older hour per poll, without summing or advancing on failure.
                        // A new connection restarts this bounded 24h recovery; idempotent upserts protect overlap.
                        if (backfillEnd > 0L && olderWindows < 23) {
                            val olderStart = backfillEnd - 3600
                            val older = fitness.minutes(olderStart, backfillEnd)
                            localStore.saveMinutes(deviceKey, olderStart, backfillEnd, older)
                            backfillEnd = olderStart
                            olderWindows++
                        }
                        if (!historyRead) {
                            val end = now - now % 60
                            val start = end - 86400
                            val sleep = if (fitness.supportsLegacySleep()) fitness.legacySleep(start, end) else null
                            sleep?.let { localStore.saveSleep(deviceKey, it) }
                            val workouts = if (fitness.supportsWorkoutSummaries()) fitness.workoutBundles(start, end) else null
                            workouts?.let {
                                localStore.saveWorkouts(deviceKey, it.map { bundle -> bundle.summary })
                                localStore.saveWorkoutDetails(deviceKey, it)
                            }
                            historySummary = "Legacy sleep intervals: ${sleep?.size ?: "not advertised"}; workout summaries: ${workouts?.size ?: "not advertised"}; workout samples: ${workouts?.sumOf { it.samples.size } ?: 0}; pace splits: ${workouts?.sumOf { it.splits.size } ?: 0}."
                            historyRead = true
                        }
                        val totalsText = totals?.let {
                            "Huawei totals: steps=${it.steps ?: "unavailable"}, distance=${it.distanceMeters ?: "unavailable"} m."
                        } ?: "Huawei totals not advertised."
                        val historyText = historyCount?.let { "GCM-verified minute records stored: $it." }
                            ?: "Minute history unavailable."
                        onVerified("$header\n$totalsText\n$historyText\n$sourcePreview\n$historySummary\nSaved in separate Huawei store; historical HR timeline uses verified minute data. Scores unchanged.\n$notes")
                        onStatus(charge, false)
                        // Check cancellation frequently. No second reader or background thread per poll.
                        repeat(120) {
                            checkActive()
                            Thread.sleep(250)
                        }
                    }
                })
            return "Huawei session ended."
        } catch (e: CancellationException) {
            throw e
        } catch (_: HuaweiSessionRotation) {
            return SESSION_ROTATION
        } catch (_: HuaweiAccountSelectionRequired) {
            return "Watch requests account selection. ALKE will not confirm a reset or switch account."
        } catch (_: HuaweiPairingProtocol.BindingRequired) {
            return "The watch requests a new pairing. Use first-time ALKE binding to confirm."
        } catch (e: Exception) {
            ensureActive()
            if (sessionStarted && e is IOException && transport.trace.summary().contains("failure=socket"))
                return SESSION_LINK_LOST
            val stage = if (checkingBinding) "Checking saved binding" else if (sessionStarted)
                "Huawei fitness session" else protocol.stage
            val failure = if (expired.get()) "timed out" else "failed or unsupported"
            return "$stage: $failure. ${protocol.lastResponse}\n${transport.trace.summary()}"
        } finally {
            onStatus(null, false)
            fitnessStore?.close()
            binding?.close()
            timeout.cancel(false)
            link.close()
            watchdog.shutdownNow()
        }
    }
}
