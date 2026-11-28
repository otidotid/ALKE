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

/** The explicit Huawei pairing and saved-binding actions from the existing diagnostic card. */
@SuppressLint("MissingPermission")
internal object HuaweiPairingDiagnostic {
    private val attemptLock = Mutex()

    suspend fun run(context: Context, device: BluetoothDevice, allowFirstBind: Boolean): String =
        attemptLock.withLock {
            coroutineScope {
                val link = HuaweiRfcommTransport(device)
                val closeOnCancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        link.close()
                    }
                }
                try {
                    withContext(Dispatchers.IO) {
                        val attemptContext = coroutineContext
                        attempt(context, device, link, allowFirstBind) { attemptContext.ensureActive() }
                    }
                } finally {
                    closeOnCancellation.cancel()
                }
            }
        }

    private fun attempt(
        context: Context,
        device: BluetoothDevice,
        link: HuaweiRfcommTransport,
        allowFirstBind: Boolean,
        ensureActive: () -> Unit,
    ): String {
        val expired = AtomicBoolean(false)
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val timeout = watchdog.schedule({
            expired.set(true)
            link.close()
        }, 60, TimeUnit.SECONDS)
        fun checkActive() {
            ensureActive()
            if (expired.get()) throw IOException("Huawei pairing timeout")
        }
        val transport = HuaweiHiChainTransport(link, ::checkActive)
        val protocol = HuaweiPairingProtocol(transport)
        var binding: HuaweiCredentialStore.Binding? = null
        var checkingBinding = true
        try {
            require(HuaweiRfcommTransport.isGt5Name(device.name))
            require(device.bondState == BluetoothDevice.BOND_BONDED)
            val store = HuaweiCredentialStore(context.applicationContext)
            val address = device.address
            val pending = store.loadPending(address)
            binding = pending ?: store.load(address)
            if (binding == null && !allowFirstBind) {
                return "First-time binding requires your confirmation."
            }
            val identity = binding?.identity ?: UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT)
            val model = Build.MODEL.take(30).ifBlank { "Android" }
            checkingBinding = false
            link.connect()
            val info = protocol.authenticate(identity, model, binding?.credential, pending != null, allowFirstBind,
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
                })
            return "Huawei authentication and encrypted readback verified. Battery: ${info.batteryPercent}%."
        } catch (e: CancellationException) {
            throw e
        } catch (_: HuaweiPairingProtocol.BindingRequired) {
            return "The watch requests a new pairing. Use first-time ALKE binding to confirm."
        } catch (_: Exception) {
            ensureActive()
            val stage = if (checkingBinding) "Checking saved binding" else protocol.stage
            val failure = if (expired.get()) "timed out" else "failed or unsupported"
            return "$stage: $failure. ${protocol.lastResponse}\n${transport.trace.summary()}"
        } finally {
            binding?.close()
            timeout.cancel(false)
            link.close()
            watchdog.shutdownNow()
        }
    }
}
