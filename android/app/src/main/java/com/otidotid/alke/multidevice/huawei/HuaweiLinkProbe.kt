package com.otidotid.alke.multidevice.huawei

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only, explicitly requested hardware diagnostic. It opens one temporary RFCOMM
 * socket and sends the documented LPv2 link, device-status and security-negotiation queries. No binding, keys,
 * account changes, wearable settings, health reads/writes or WHOOP calls.
 */
@SuppressLint("MissingPermission")
internal object HuaweiLinkProbe {
    private const val TIMEOUT_SECONDS = 15L

    suspend fun candidates(context: Context): List<BluetoothDevice> = withContext(Dispatchers.IO) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return@withContext emptyList()
        if (!adapter.isEnabled) return@withContext emptyList()
        HuaweiRfcommTransport.bondedCandidates(adapter).sortedBy { it.address }
    }

    suspend fun run(device: BluetoothDevice): String = withContext(Dispatchers.IO) {
        val transport = HuaweiRfcommTransport(device)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val expired = AtomicBoolean(false)
        var stage = "Opening Bluetooth socket"
        val timeout = scheduler.schedule({
            expired.set(true)
            transport.close()
        }, TIMEOUT_SECONDS, TimeUnit.SECONDS)
        var linkReport: String? = null
        var capabilities: HuaweiAuthCapabilities.Parameters? = null
        var awaitingSecurity = false
        try {
            transport.connect()
            stage = "Requesting link parameters"
            transport.write(HuaweiLinkNegotiation.request())
            val decoder = HuaweiLinkFrames.Decoder()
            val buffer = ByteArray(4096)
            var remaining = 131072
            while (remaining > 0 && !expired.get()) {
                val n = transport.read(buffer)
                if (n <= 0) throw IOException("Socket ended")
                remaining -= n
                for (message in decoder.feed(buffer.copyOf(n))) {
                    if (linkReport == null && message.service == 1 && message.command == 1) {
                        linkReport = describe(message)
                        capabilities = runCatching { HuaweiAuthCapabilities.parse(message) }.getOrNull()
                        stage = "Reading device status"
                        transport.write(HuaweiAuthCapabilities.statusRequest())
                    } else if (!awaitingSecurity && linkReport != null && message.service == 1 && message.command == 0x16) {
                        val status = HuaweiAuthCapabilities.parseStatus(message)
                        linkReport = "$linkReport\nDevice status: ${status ?: "unknown"}"
                        val params = capabilities
                        if (status !in listOf(0, 1, 255) || params?.supportType != 4 ||
                            params.encryptMethod !in listOf(0, 1)) {
                            return@withContext "$linkReport\nSecurity negotiation not attempted: unsupported or missing capability."
                        }
                        // Disposable identity for the debug probe only. Never reuse it as a
                        // persistent binding identity, and never request PIN or start HiChain bind.
                        val probeId = java.util.UUID.randomUUID().toString().replace("-", "")
                        val model = android.os.Build.MODEL.take(40).ifBlank { "Android" }
                        stage = "Reading security negotiation"
                        awaitingSecurity = true
                        transport.write(HuaweiSecurityNegotiation.request(params, probeId, model))
                    } else if (awaitingSecurity && message.service == 1 && message.command == 0x33) {
                        val next = HuaweiSecurityNegotiation.parse(message)
                        return@withContext "$linkReport\nSecurity negotiation (temporary probe identity): ${next.name}\nNo binding or key exchange attempted."
                    }
                }
            }
            linkReport?.plus("\n$stage: response unavailable.") ?: "Link response unavailable. No authentication attempted."
        } catch (_: SecurityException) {
            "Bluetooth permission missing. No authentication attempted."
        } catch (_: IOException) {
            val reason = if (expired.get()) "timed out." else "connection closed or unavailable."
            linkReport?.plus("\n$stage: $reason") ?: "$stage: $reason"
        } catch (_: IllegalArgumentException) {
            linkReport?.plus("\n$stage: unsupported or malformed response.") ?:
                "$stage: unsupported or malformed response. No authentication attempted."
        } catch (_: IllegalStateException) {
            "$stage: Bluetooth transport unavailable."
        } finally {
            timeout.cancel(false)
            transport.close()
            scheduler.shutdownNow()
        }
    }

    /**
     * Safe diagnostic: only protocol parameters and TLV tag/length metadata.
     * Never return raw packets, nonce, device address, account ID or keys.
     */
    fun describe(message: HuaweiLinkFrames.Message): String {
        require(message.service == 1 && message.command == 1)
        val metadata = ArrayList<String>()
        val payload = message.payload
        var i = 0
        while (i < payload.size) {
            if (payload.size - i < 2) {
                metadata.add("truncated")
                break
            }
            val tag = payload[i++].toInt() and 255
            var length = 0
            var shift = 0
            var ended = false
            while (i < payload.size && shift <= 21) {
                val b = payload[i++].toInt() and 255
                length = length or ((b and 127) shl shift)
                if (b and 128 == 0) { ended = true; break }
                shift += 7
            }
            if (!ended || length > payload.size - i) {
                metadata.add("unsupported TLV length")
                break
            }
            metadata.add("%02X:%d".format(java.util.Locale.ROOT, tag, length))
            i += length
        }
        val legacy = runCatching { HuaweiLinkNegotiation.parseLegacyResponse(message) }.getOrNull()
        return buildString {
            append("Huawei RFCOMM LinkParams response received.\n")
            append("TLV tags:lengths: ").append(metadata.joinToString(", ")).append('\n')
            val modern = runCatching { HuaweiAuthCapabilities.parse(message) }.getOrNull()
            if (modern != null) {
                append("Device support type: ").append(modern.supportType ?: "unknown").append('\n')
                append("Authentication version: ").append(modern.authVersion ?: "unknown").append('\n')
                append("Authentication algorithm: ").append(modern.authAlgorithm ?: "unknown").append('\n')
                append("Bond state: ").append(modern.bondState ?: "unknown").append('\n')
                append("Encryption method: ").append(modern.encryptMethod ?: "unknown").append('\n')
            }
            if (legacy != null) {
                append("Protocol version: ").append(legacy.protocolVersion).append('\n')
                append("Max frame/link: ").append(legacy.maxFrameSize).append('/').append(legacy.maxLinkSize).append('\n')
                append("Legacy authentication field: ").append(legacy.authenticationVersion).append('\n')
            } else {
                append("Legacy parameter layout not recognized.\n")
            }
            append("Transport only. Not paired or authenticated. No health data read.")
        }
    }
}
