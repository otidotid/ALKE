package com.otidotid.alke.multidevice.huawei

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** App-process-owned test connection, independent of the Devices screen and WHOOP. */
internal object HuaweiConnectionController {
    data class State(val running: Boolean = false, val report: String? = null,
        val authenticated: Boolean = false, val batteryPercent: Int? = null, val syncing: Boolean = false,
        val previouslyAuthenticated: Boolean = false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var ownerContext: Context? = null
    private const val PREFS = "alke_huawei_connection"
    private const val ADDRESS = "verified_address"
    private const val ENABLED = "auto_reconnect"

    /** Only a previously verified, explicitly enabled binding may reconnect at app launch.
     * All connections still go through the single process owner. No first bind on startup. */
    @SuppressLint("MissingPermission")
    fun resumeSaved(context: Context) {
        if (job?.isActive == true || mutableState.value.running) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ENABLED,false)) return
        val address = prefs.getString(ADDRESS,null) ?: return
        try {
            val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
            val device = adapter.bondedDevices.firstOrNull { it.address == address &&
                HuaweiRfcommTransport.isGt5Name(it.name) } ?: return
            // A verified credential is required; pending-only bindings never auto-start.
            if (HuaweiCredentialStore(app).load(address)?.use { true } != true) return
            connect(app,device,false)
        } catch (_: SecurityException) {
            mutableState.value = State(report = "Allow Nearby devices permission to reconnect Huawei.")
        } catch (_: Exception) {
            mutableState.value = State(report = "Saved Huawei connection unavailable; use Devices to resume.")
        }
    }

    internal fun retryDelayMillis(failures: Int): Long =
        minOf(300_000L, 2_000L * (1L shl failures.coerceIn(0,8)))

    // Both entry points are invoked on the UI thread; do not start two sockets.
    fun connect(context: Context, device: BluetoothDevice, firstBind: Boolean, testFiles: Boolean = false) {
        if (job?.isActive == true || mutableState.value.running) return
        ownerContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        mutableState.value = State(true, "Connecting Huawei GT 5...")
        job = scope.launch {
            try {
                var allowFirstBind = firstBind
                var failures = 0
                var fileProbe = testFiles
                var persisted = false
                var result: String
                while (true) {
                    result = HuaweiPairingDiagnostic.run(context.applicationContext, device, allowFirstBind,
                        onStatus = { authenticated, percent, syncing ->
                            mutableState.value = mutableState.value.copy(authenticated = authenticated,
                                batteryPercent = percent?.takeIf { it in 0..100 }, syncing = syncing,
                                previouslyAuthenticated = mutableState.value.previouslyAuthenticated || authenticated)
                            if (authenticated && !persisted) {
                                // Authentication and credential promotion precede this callback.
                                prefs.edit().putString(ADDRESS,device.address).putBoolean(ENABLED,true).commit()
                                persisted = true
                            }
                        }, onVerified = {
                            mutableState.value = mutableState.value.copy(running = true, report = it)
                        }, testFiles = fileProbe)
                    // Explicit file probe is attempted only once, never retried on rotation/link loss.
                    fileProbe = false
                    // Never automatically repeat first bind or account/reset decisions.
                    allowFirstBind = false
                    if (result == HuaweiPairingDiagnostic.SESSION_ROTATION) {
                        failures = 0
                        mutableState.value = State(true, "Refreshing Huawei authenticated session...",
                            previouslyAuthenticated = mutableState.value.previouslyAuthenticated)
                        continue
                    }
                    if (result != HuaweiPairingDiagnostic.SESSION_LINK_LOST) break
                    val enabled = prefs.getBoolean(ENABLED,false) &&
                        prefs.getString(ADDRESS,null) == device.address
                    if (!enabled && failures >= 3) break
                    val wait = retryDelayMillis(failures)
                    failures = (failures + 1).coerceAtMost(8)
                    mutableState.value = State(true, "Huawei out of range or link lost; retry in ${wait / 1000}s.",
                        previouslyAuthenticated = mutableState.value.previouslyAuthenticated)
                    delay(wait)
                }
                mutableState.value = State(false, result)
            } catch (e: CancellationException) {
                mutableState.value = State(false, "Huawei disconnected. WHOOP unchanged.")
                throw e
            } catch (_: Exception) {
                mutableState.value = State(false, "Huawei connection could not be completed.")
            } finally {
                if (mutableState.value.running) mutableState.value = State(false, "Huawei connection closed.")
            }
        }
    }
    fun disconnect() {
        ownerContext?.getSharedPreferences(PREFS,Context.MODE_PRIVATE)?.edit()
            ?.putBoolean(ENABLED,false)?.commit()
        job?.cancel()
    }
}
