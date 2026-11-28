package com.otidotid.alke.multidevice.huawei

import android.bluetooth.BluetoothDevice
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
        val authenticated: Boolean = false, val batteryPercent: Int? = null, val syncing: Boolean = false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    // Both entry points are invoked on the UI thread; do not start two sockets.
    fun connect(context: Context, device: BluetoothDevice, firstBind: Boolean) {
        if (job?.isActive == true || mutableState.value.running) return
        mutableState.value = State(true, "Connecting Huawei GT 5...")
        job = scope.launch {
            try {
                var allowFirstBind = firstBind
                var failures = 0
                var result: String
                while (true) {
                    result = HuaweiPairingDiagnostic.run(context.applicationContext, device, allowFirstBind,
                        onStatus = { percent, syncing ->
                            mutableState.value = mutableState.value.copy(authenticated = percent != null,
                                batteryPercent = percent?.takeIf { it in 0..100 }, syncing = syncing)
                        }, onVerified = {
                            mutableState.value = mutableState.value.copy(running = true, report = it)
                        })
                    // Never automatically repeat first bind or account/reset decisions.
                    allowFirstBind = false
                    if (result == HuaweiPairingDiagnostic.SESSION_ROTATION) {
                        failures = 0
                        mutableState.value = State(true, "Refreshing Huawei authenticated session...")
                        continue
                    }
                    if (result != HuaweiPairingDiagnostic.SESSION_LINK_LOST || ++failures > 3) break
                    mutableState.value = State(true, "Huawei link lost; reconnecting saved binding ($failures/3).")
                    delay(2000L * failures)
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
    fun disconnect() { job?.cancel() }
}
