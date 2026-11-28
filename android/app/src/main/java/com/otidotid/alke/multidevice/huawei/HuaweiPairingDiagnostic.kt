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
        onStatus: (Boolean, Int?, Boolean) -> Unit = { _, _, _ -> },
        onVerified: (String) -> Unit = {}, testFiles: Boolean = false): String = attemptLock.withLock {
        coroutineScope {
            val link = HuaweiRfcommTransport(device)
            val closeOnCancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { link.close() }
            }
            try {
                withContext(Dispatchers.IO) {
                    val attemptContext = coroutineContext
                    attempt(context, device, link, allowFirstBind, onStatus, onVerified, testFiles) { attemptContext.ensureActive() }
                }
            } finally {
                link.close()
                closeOnCancellation.cancel()
            }
        }
    }

    private fun attempt(context: Context, device: BluetoothDevice, link: HuaweiRfcommTransport,
        allowFirstBind: Boolean, onStatus: (Boolean, Int?, Boolean) -> Unit,
        onVerified: (String) -> Unit, testFiles: Boolean, ensureActive: () -> Unit): String {
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
        var openingSocket = false
        var sessionStarted = false
        var canResume = false
        var fitnessStore: HuaweiFitnessStore? = null
        try {
            require(HuaweiRfcommTransport.isGt5Name(device.name))
            require(device.bondState == BluetoothDevice.BOND_BONDED)
            val store = HuaweiCredentialStore(context.applicationContext)
            val address = device.address
            val deviceKey = HuaweiFitnessStore.deviceKey(address)
            val pending = store.loadPending(address)
            binding = pending ?: store.load(address)
            canResume = binding != null
            if (binding == null && !allowFirstBind) return "First-time binding requires your confirmation."
            val identity = binding?.identity ?: UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT)
            val model = Build.MODEL.take(30).ifBlank { "Android" }
            checkingBinding = false
            openingSocket = true
            link.connect()
            openingSocket = false
            protocol.authenticate(identity, model, binding?.credential, pending != null, allowFirstBind,
                savePending = { credential ->
                    checkActive()
                    HuaweiCredentialStore.Binding(identity, credential.copyOf()).use {
                        store.savePendingBinding(address, it)
                        canResume = true
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
                    onStatus(true, info.batteryPercent, true)
                    val batteryMode = if (info.batteryEncrypted) "GCM" else "plaintext"
                    val connect = when {
                        info.initialization?.connectSupported != true -> "not advertised"
                        !info.connectStatusAcknowledged -> "no successful reply"
                        info.connectStatusEncrypted -> "GCM reply"
                        else -> "plaintext reply"
                    }
                    val header = "Huawei authenticated (GCM ProductInfo). Battery at connection: ${info.batteryPercent?.let { "$it% ($batteryMode)" } ?: "unavailable"}. ConnectStatus: $connect. Watch UI not yet verified."
                    val notes = info.initialization?.notes.orEmpty().joinToString("\n")
                    onVerified("$header\nReading advertised fitness capabilities.\n$notes")
                    // Keep the session alive and read only known, advertised commands.
                    // No automatic sensor changes, agreement acceptance or WHOOP switching.
                    val localStore = HuaweiFitnessStore(context.applicationContext)
                    fitnessStore = localStore
                    HuaweiTimelineSource.selectVerifiedDevice(context.applicationContext, deviceKey)
                    HuaweiWorkoutIntegration.observeFirmware(context,deviceKey,info.product.firmware)
                    val sessionStart = android.os.SystemClock.elapsedRealtime()
                    var charge = info.batteryPercent
                    // Do not reissue a timed-out command on the same stream: a late reply
                    // could be mistaken for the new response. Retry on fresh authentication.
                    var nextBattery = if (charge != null) sessionStart + 300_000L else Long.MAX_VALUE
                    var nextHistory = 0L
                    var nextOlderNative = 0L
                    var historyCount: Int? = null
                    var sourcePreview = "HR source comparison unavailable."
                    var historySummary = "Sleep and workout not yet read."
                    var nextNativeHistory = 0L
                    var fileProbePending = testFiles
                    val attemptedGps = mutableSetOf<Pair<Long,Int>>()
                    var sleepRejected = false
                    var workoutsRejected = false
                    var minuteRejected = false
                    var totalsRejected = false
                    var telemetryNotes = ""
                    fun <T> optionalFitness(label: String, block: () -> T): T? = try {
                        block()
                    } catch (_: HuaweiFitnessRejected) {
                        telemetryNotes += "\n$label rejected by authenticated watch."
                        null
                    }
                    while (true) {
                        checkActive()
                        if (android.os.SystemClock.elapsedRealtime() - sessionStart >= 30 * 60 * 1000L)
                            throw HuaweiSessionRotation()
                        onStatus(true, charge, true)
                        val elapsed = android.os.SystemClock.elapsedRealtime()
                        if (elapsed >= nextBattery) {
                            charge = fitness.batteryPercent()
                            nextBattery = if (charge != null)
                                android.os.SystemClock.elapsedRealtime() + 300_000L else Long.MAX_VALUE
                            if (charge == null) telemetryNotes += "\nBattery refresh unavailable; retry on next authenticated session."
                            onStatus(true, charge, true)
                        }
                        val before = System.currentTimeMillis()
                        val zone = java.time.ZoneId.systemDefault()
                        val day = java.time.Instant.ofEpochMilli(before).atZone(zone).toLocalDate()
                        val totals = if (!totalsRejected && fitness.supports(HuaweiFitnessRead.TOTALS)) {
                            optionalFitness("Fitness totals") { fitness.totals() }.also {
                                if (it == null) totalsRejected = true
                            }
                        } else null
                        val now = System.currentTimeMillis() / 1000
                        if (totals != null && java.time.Instant.ofEpochSecond(now).atZone(zone).toLocalDate() == day)
                            localStore.saveTotals(deviceKey, day.toString(), now, totals)
                        if (now >= nextHistory && !minuteRejected && fitness.supportsMinuteHistory()) {
                            // A bounded recent window, with no invented intervals or cursor advancement.
                            val end = now - now % 60
                            val start = end - 3600
                            val minutes = optionalFitness("Minute history") { fitness.minutes(start, end) }
                            if (minutes == null) minuteRejected = true
                            else localStore.saveMinutes(deviceKey, start, end, minutes)
                            val stored = localStore.minutes(deviceKey, start, end)
                            historyCount = stored.size
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
                        // Persistent per-device coverage; no progress recorded before committed data.
                        if (!minuteRejected && fitness.supportsMinuteHistory()) {
                            localStore.nextHistoryWindow(deviceKey,"minute",now,3600)?.let { window ->
                                val older = optionalFitness("Older minute history") { fitness.minutes(window.start,window.end) }
                                if (older == null) minuteRejected = true
                                else localStore.saveMinutes(deviceKey,window.start,window.end,older,checkpoint=true)
                            }
                        }
                        if (elapsed >= nextOlderNative) {
                            if (!sleepRejected && fitness.supportsLegacySleep()) {
                                localStore.nextHistoryWindow(deviceKey,"sleep",now,86400)?.let { window ->
                                    val older = optionalFitness("Older sleep history") { fitness.legacySleep(window.start,window.end) }
                                    if (older == null) sleepRejected = true
                                    else localStore.saveSleepWindow(deviceKey,window,older)
                                }
                            }
                            if (!workoutsRejected && fitness.supportsWorkoutSummaries()) {
                                localStore.nextHistoryWindow(deviceKey,"workout",now,86400)?.let { window ->
                                    val older = optionalFitness("Older workout history") { fitness.workoutBundles(window.start,window.end) }
                                    if (older == null) workoutsRejected = true
                                    else localStore.saveWorkoutWindow(deviceKey,window,older)
                                }
                            }
                            nextOlderNative = android.os.SystemClock.elapsedRealtime()+120_000L
                        }
                        if (elapsed >= nextNativeHistory) {
                            val end = now - now % 60
                            val start = end - 86400
                            val sleep = if (!sleepRejected && fitness.supportsLegacySleep()) {
                                optionalFitness("Legacy sleep") { fitness.legacySleep(start, end) }.also { if (it == null) sleepRejected = true }
                            } else null
                            sleep?.let { localStore.saveSleep(deviceKey, it) }
                            val workouts = if (!workoutsRejected && fitness.supportsWorkoutSummaries()) {
                                optionalFitness("Workout history") { fitness.workoutBundles(start, end) }.also { if (it == null) workoutsRejected = true }
                            } else null
                            workouts?.let { localStore.saveWorkoutWindow(deviceKey,null,it) }
                            historySummary = "Legacy sleep intervals: ${sleep?.size ?: "unavailable"}; workout summaries: ${workouts?.size ?: "unavailable"}; workout samples: ${workouts?.sumOf { it.samples.size } ?: 0}; pace splits: ${workouts?.sumOf { it.splits.size } ?: 0}."
                            if (fileProbePending) {
                                fileProbePending = false
                                onVerified("$header\nExperimental file diagnostic in progress.")
                                telemetryNotes += "\n" + HuaweiNativeFileDiagnostic.run(fitness.nativeFiles(),localStore,
                                    deviceKey,start,end,workouts.orEmpty().map { it.summary })
                            }
                            nextNativeHistory = android.os.SystemClock.elapsedRealtime() + 15*60*1000L
                        }
                        // Opt-in is scoped to the verified watch firmware. Fetch at most one
                        // missing route per poll, never retry the same failed file in this session.
                        if(HuaweiWorkoutIntegration.enabled(context,deviceKey) && fitness.nativeFiles().supported()) {
                            val candidate=localStore.workoutSummaries(deviceKey,now-7*86400L,now).firstOrNull { w ->
                                HuaweiWorkoutIntegration.gpsEligible(w.nativeType) &&
                                    (w.start to w.number) !in attemptedGps &&
                                    localStore.gpsCandidate(deviceKey,w.start,w.number).isEmpty()
                            }
                            if(candidate!=null) {
                                attemptedGps.add(candidate.start to candidate.number)
                                onVerified("$header\nReading one GT5 workout route.")
                                try {
                                    val gps=fitness.nativeFiles().read(HuaweiFileRead.Kind.GPS,0,0,candidate.number)
                                    try { localStore.saveGpsCandidate(deviceKey,candidate,gps) }
                                    catch(_: IllegalArgumentException) { telemetryNotes+="\nGPS layout unsupported for workout." }
                                    finally { gps.fill(0) }
                                } catch(_: HuaweiFileUnavailable) {
                                    telemetryNotes+="\nGPS file unavailable on watch."
                                }
                            }
                        }
                        val totalsText = totals?.let {
                            "Huawei totals: steps=${it.steps ?: "unavailable"}, distance=${it.distanceMeters ?: "unavailable"} m."
                        } ?: "Huawei totals unavailable."
                        val historyText = historyCount?.let { "GCM-verified minute records stored: $it." }
                            ?: "Minute history unavailable."
                        val lastRecord = localStore.latestRecord(deviceKey)?.let {
                            java.time.Instant.ofEpochSecond(it).atZone(zone).toLocalDateTime().toString()
                        } ?: "none"
                        onVerified("Bluetooth bonded.\n$header\nLast stored record: $lastRecord\n$totalsText\n$historyText\n$sourcePreview\n$historySummary\nSaved in separate Huawei store; historical HR timeline uses verified minute data. Scores unchanged.\n$notes$telemetryNotes")
                        onStatus(true, charge, false)
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
        } catch (_: SecurityException) {
            return "Nearby devices permission required to connect Huawei."
        } catch (e: Exception) {
            ensureActive()
            if (canResume && e is IOException && (openingSocket || transport.trace.isRetryableLinkFailure() || expired.get()))
                return SESSION_LINK_LOST
            val stage = if (checkingBinding) "Checking saved binding" else if (sessionStarted)
                "Huawei fitness session" else protocol.stage
            val failure = if (expired.get()) "timed out" else "failed or unsupported"
            return "$stage: $failure. ${protocol.lastResponse}\n${transport.trace.summary()}"
        } finally {
            onStatus(false, null, false)
            try { fitnessStore?.close() } finally {
                binding?.close()
                timeout.cancel(false)
                link.close()
                watchdog.shutdownNow()
            }
        }
    }
}
