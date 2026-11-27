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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Temporary debug-build-only physical test entry. Does not add a device to the registry,
 * change the active WHOOP, start a background job, or imply authenticated connectivity.
 */
@SuppressLint("MissingPermission")
@Composable
internal fun HuaweiDiagnosticCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Huawei GT 5 — connection diagnostic")
            Text("Development only. Tests the Bluetooth link, not pairing or health sync. WHOOP settings remain unchanged.")
            Button(enabled = !busy, onClick = {
                busy = true
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
                        busy = false
                    }
                }
            }) { Text("Find paired GT 5") }
            candidates.forEach { device ->
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    report = null
                    scope.launch {
                        try {
                            report = HuaweiLinkProbe.run(device)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            report = "Connection diagnostic could not be completed."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Probe " + (runCatching { device.name }.getOrNull() ?: "Huawei GT 5")) }
            }
            if (busy) Text("Checking Bluetooth…")
            report?.let { result ->
                Text(result)
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("ALKE Huawei diagnostic", result))
                }) { Text("Copy diagnostic result") }
            }
        }
    }
}
