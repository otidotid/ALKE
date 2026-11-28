package com.otidotid.alke.multidevice.huawei

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit experimental pairing attempt. No WHOOP registry changes and no health sync.
 * A bind token is kept in memory only until the reconnect proof AND encrypted reads
 * succeed. Failure never triggers a new bind automatically or deletes an existing key.
 */
@SuppressLint("MissingPermission")
internal object HuaweiPairingDiagnostic {
    private const val GROUP = "7B0BC0CBCE474F6C238D9661C63400B797B166EA7849B3A370FC73A9A236E989"

    suspend fun run(context: Context, device: BluetoothDevice, allowFirstBind: Boolean): String =
        withContext(Dispatchers.IO) {
            val store = HuaweiCredentialStore(context.applicationContext)
            val link = HuaweiRfcommTransport(device)
            val watchdog = Executors.newSingleThreadScheduledExecutor()
            val expired = AtomicBoolean(false)
            val timer = watchdog.schedule({
                expired.set(true)
                link.close()
            }, 60, TimeUnit.SECONDS)
            var stage = "Checking saved binding"
            var lastResponse = ""
            var existing: HuaweiCredentialStore.Binding? = null
            var provisional: ByteArray? = null
            var sessionKey: ByteArray? = null
            try {
                require(HuaweiRfcommTransport.isGt5Name(device.name))
                require(device.bondState == BluetoothDevice.BOND_BONDED)
                existing = store.load(device.address)
                if (existing == null && !allowFirstBind)
                    return@withContext "No verified ALKE binding. First-time binding requires your confirmation."
                val identity = existing?.identity ?: UUID.randomUUID().toString().replace("-", "")
                val decoder = HuaweiLinkFrames.Decoder()
                val pending = ArrayDeque<HuaweiLinkFrames.Message>()
                val buffer = ByteArray(4096)
                var remaining = 262144
                fun send(frames: List<ByteArray>) {
                    for (frame in frames) {
                        if (expired.get()) throw IOException("Huawei pairing timeout")
                        link.write(frame)
                    }
                }
                fun receive(command: Int): HuaweiLinkFrames.Message {
                    while (pending.isEmpty()) {
                        if (expired.get()) throw IOException("Huawei pairing timeout")
                        val n = link.read(buffer)
                        if (n <= 0 || n > buffer.size || expired.get())
                            throw IOException("Huawei transport ended")
                        remaining -= n
                        if (remaining < 0) throw IOException("Huawei response limit")
                        pending.addAll(decoder.feed(buffer.copyOf(n)))
                    }
                    return pending.removeFirst().also {
                        require(it.service == 1 && it.command == command) { "Unexpected Huawei response" }
                    }
                }
                stage = "Opening Bluetooth"
                link.connect()
                stage = "Link parameters"
                send(listOf(HuaweiLinkNegotiation.request()))
                val parameters = HuaweiAuthCapabilities.parse(receive(1))
                require(parameters.supportType == 4)
                require(parameters.authVersion in 1..3 && parameters.effectiveEncryptMethod in 0..1)
                val frameBudget = HuaweiAuthCapabilities.hiChainFrameBudget(parameters)
                stage = "Device status"
                send(listOf(HuaweiAuthCapabilities.statusRequest()))
                require(HuaweiAuthCapabilities.parseStatus(receive(0x16)) in listOf(0, 1, 255))
                stage = "Security negotiation"
                val model = Build.MODEL.take(30).ifBlank { "Android" }
                send(listOf(HuaweiSecurityNegotiation.request(parameters, identity, model)))
                val mode = HuaweiSecurityNegotiation.parse(receive(0x33))
                val operation = requireNotNull(HuaweiSecurityNegotiation.operation(mode, existing != null, allowFirstBind))
                fun exchange(op: HuaweiIsoExchange.Operation, credential: ByteArray): ByteArray {
                    HuaweiHiChainSession(op, credential, identity, GROUP, System.currentTimeMillis(),
                        frameBudget, allowProvisionalReadback = true).use { session ->
                        val label = if (op == HuaweiIsoExchange.Operation.BIND) "First bind" else "Reconnect"
                        lastResponse = ""
                        stage = "$label: send initial handshake"
                        send(session.begin())
                        repeat(4) {
                            stage = "$label: ${session.phase.name}"
                            val reply = receive(0x28)
                            lastResponse = HuaweiHiChainWire.safeResponseMetadata(reply)
                            send(session.receive(reply))
                            if (session.phase == HuaweiHiChainSession.Phase.TERMINAL_UNVERIFIED)
                                return session.takeProvisionalForVerification()
                        }
                        error("Incomplete Huawei authentication")
                    }
                }
                if (operation == HuaweiIsoExchange.Operation.BIND) {
                    stage = "First-bind credential"
                    send(listOf(HuaweiFirstBind.request(parameters, allowFirstBind)))
                    val pin = HuaweiFirstBind.credential(parameters, receive(HuaweiFirstBind.COMMAND))
                    try {
                        stage = "First bind — confirm on watch"
                        provisional = exchange(HuaweiIsoExchange.Operation.BIND, pin)
                    } finally { pin.fill(0) }
                }
                stage = "Reconnect proof"
                val credential = requireNotNull(existing?.credential ?: provisional)
                val key = exchange(HuaweiIsoExchange.Operation.RECONNECT, credential)
                sessionKey = key
                stage = "Encrypted device readback"
                val info = HuaweiSessionReads(key, frameBudget).use { reads ->
                    send(reads.request(HuaweiDeviceInfo.PRODUCT))
                    reads.receive(receive(HuaweiDeviceInfo.PRODUCT))
                    send(reads.request(HuaweiDeviceInfo.BATTERY))
                    reads.receive(receive(HuaweiDeviceInfo.BATTERY))
                    requireNotNull(reads.readback())
                }
                if (existing == null) {
                    stage = "Saving verified binding"
                    HuaweiCredentialStore.Binding(identity, credential.copyOf()).use {
                        store.saveVerifiedBinding(device.address, it)
                    }
                }
                "Huawei authentication and encrypted readback verified. Battery: ${info.batteryPercent}%. Diagnostic ended; no health data synced."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Do not include exception text, peer identifiers, payloads or credentials.
                "$stage: ${if (expired.get()) "timed out" else "failed or unsupported"}. $lastResponse\nNo health data synced. Do not reset automatically."
            } finally {
                existing?.close()
                provisional?.fill(0)
                sessionKey?.fill(0)
                timer.cancel(false)
                link.close()
                watchdog.shutdownNow()
            }
        }
}
