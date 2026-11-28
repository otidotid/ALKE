package com.otidotid.alke.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.otidotid.alke.multidevice.huawei.HuaweiLinkProbe
import com.otidotid.alke.multidevice.huawei.HuaweiConnectionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Explicit experimental physical test entry for Personal and non-demo debug builds. Does not add a device to the registry,
 * change the active WHOOP, start a background job, or imply authenticated connectivity.
 */
@SuppressLint("MissingPermission")
@Composable
internal fun HuaweiDiagnosticCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var report by remember { mutableStateOf<String?>(null) }
    var probeBusy by remember { mutableStateOf(false) }
    val connection by HuaweiConnectionController.state.collectAsStateWithLifecycle()
    val busy = probeBusy || connection.running
    var pendingBind by remember { mutableStateOf<BluetoothDevice?>(null) }

    fun authenticate(device: BluetoothDevice, firstBind: Boolean, testFiles: Boolean = false) {
        report = null
        HuaweiConnectionController.connect(context.applicationContext, device, firstBind, testFiles)
    }

    pendingBind?.let { device ->
        AlertDialog(onDismissRequest = { pendingBind = null },
            title = { Text("Bind Huawei GT 5 to ALKE?") },
            text = { Text("This experimental first bind may replace the Huawei Health binding. Confirm on the watch if requested. ALKE will not reset the watch or change WHOOP settings. Read-only fitness diagnostic; no data merged into ALKE scores.") },
            confirmButton = { TextButton(onClick = {
                pendingBind = null
                authenticate(device, true)
            }) { Text("Start binding test") } },
            dismissButton = { TextButton(onClick = { pendingBind = null }) { Text("Cancel") } })
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Huawei GT 5 — connection diagnostic")
            Text("Experimental. Authenticate and maintain a read-only session while ALKE runs. WHOOP settings remain unchanged.")
            HuaweiSystemPairButton(enabled = !busy) { report = it }
            Button(enabled = !busy, onClick = {
                probeBusy = true
                report = null
                candidates = emptyList()
                scope.launch {
                    try {
                        candidates = HuaweiLinkProbe.candidates(context.applicationContext)
                        if (candidates.isEmpty()) {
                            report = "No system-paired GT 5 found. Keep existing watch data; do not reset the watch."
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: SecurityException) {
                        report = "Allow Nearby devices permission first."
                    } catch (_: Exception) {
                        report = "Bluetooth adapter unavailable."
                    } finally {
                        probeBusy = false
                    }
                }
            }) { Text("Find paired GT 5") }
            candidates.forEach { device ->
                TextButton(enabled = !busy, onClick = {
                    probeBusy = true
                    report = null
                    scope.launch {
                        try {
                            report = HuaweiLinkProbe.run(device)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            report = "Connection diagnostic could not be completed."
                        } finally {
                            probeBusy = false
                        }
                    }
                }) { Text("Probe " + (runCatching { device.name }.getOrNull() ?: "Huawei GT 5")) }
                TextButton(enabled = !busy, onClick = { authenticate(device, false) }) {
                    Text("Resume / test saved ALKE binding")
                }
                TextButton(enabled = !busy, onClick = { authenticate(device, false, true) }) {
                    Text("Test GPS & TruSleep files (experimental)")
                }
                TextButton(enabled = !busy, onClick = { pendingBind = device }) {
                    Text("Test first-time ALKE binding")
                }
            }
            if (connection.running) TextButton(onClick = { HuaweiConnectionController.disconnect() }) { Text("Disconnect Huawei") }
            val workoutRevision by com.otidotid.alke.multidevice.huawei.HuaweiTimelineSource.revision.collectAsStateWithLifecycle()
            val verifiedKey = remember(workoutRevision) {
                com.otidotid.alke.multidevice.huawei.HuaweiTimelineSource.verifiedDeviceKey(context)
            }
            if(verifiedKey!=null && connection.authenticated) {
                val enabled = remember(workoutRevision,verifiedKey) {
                    com.otidotid.alke.multidevice.huawei.HuaweiWorkoutIntegration.enabled(context,verifiedKey)
                }
                var confirmWorkout by remember { mutableStateOf(false) }
                TextButton(onClick = {
                    if(enabled) com.otidotid.alke.multidevice.huawei.HuaweiWorkoutIntegration.setEnabled(context,verifiedKey,false)
                    else confirmWorkout=true
                }) { Text(if(enabled) "Disable GT5 workout integration" else "Enable verified GT5 workout integration") }
                if(confirmWorkout) AlertDialog(
                    onDismissRequest={ confirmWorkout=false },
                    title={ Text("Confirm GT5 workout comparison") },
                    text={ Text("Enable only after comparing one real GT5 workout type, start/end time, distance and GPS route with the diagnostic. This experimental setting applies to this firmware; firmware changes disable it.") },
                    confirmButton={ TextButton(onClick={
                        com.otidotid.alke.multidevice.huawei.HuaweiWorkoutIntegration.setEnabled(context,verifiedKey,true)
                        confirmWorkout=false
                    }) { Text("Comparison passed") } },
                    dismissButton={ TextButton(onClick={confirmWorkout=false}) { Text("Cancel") } })
            }
            HuaweiRecoveryCard(enabled = !busy)
            if (probeBusy) Text("Checking Bluetooth…")
            (report ?: connection.report)?.let { result ->
                Text(result)
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("ALKE Huawei diagnostic", result))
                }) { Text("Copy diagnostic result") }
            }
        }
    }
}
