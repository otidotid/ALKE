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

/** App-process-owned test connection, independent of the Devices screen and WHOOP. */
internal object HuaweiConnectionController {
    data class State(val running: Boolean = false, val report: String? = null)
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
                val result = HuaweiPairingDiagnostic.run(context.applicationContext, device, firstBind) {
                    mutableState.value = State(true, it)
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
