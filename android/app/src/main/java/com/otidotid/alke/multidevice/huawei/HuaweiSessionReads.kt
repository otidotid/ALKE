package com.otidotid.alke.multidevice.huawei

import java.io.Closeable
import java.security.SecureRandom

/**
 * Read-only GCM verification of a candidate reconnect session. A terminal
 * HiChain packet alone cannot publish connection state or authorize storage.
 * GCM ProductInfo proves possession of the candidate key. Some firmware
 * returns battery and initialization data in cleartext after authentication.
 * Such replies are command-scoped observations, not independent key proof.
 *
 * GCM envelopes on this protocol do not authenticate the outer command header;
 * commands are therefore strictly serialized and matched to the pending read.
 */
internal class HuaweiSessionReads(
    key: ByteArray,
    private val frameBudget: Int,
    private val random: SecureRandom = SecureRandom(),
) : Closeable {
    init {
        require(key.size == 32)
        require(frameBudget in 64..32772)
    }
    private val secret = key.copyOf()
    private val seenIv = mutableListOf<ByteArray>()
    private var closed = false
    private var pending: Int? = null
    private var initializationPending: Pair<Int, Int>? = null
    private var product: HuaweiDeviceInfo.Product? = null
    private var battery: Int? = null
    private var batteryAttempted = false
    private var batteryEncrypted = false
    private var statusEncrypted = false
    private var timeSynchronized = false
    private var servicesDiscovered = false
    var lastMetadata = ""
        private set
    var connectStatusSupported: Boolean? = null
        private set
    var connectStatusAcknowledged = false
        private set

    data class Readback(
        val product: HuaweiDeviceInfo.Product,
        val batteryPercent: Int?,
        val batteryEncrypted: Boolean,
        val connectStatusAcknowledged: Boolean,
        val connectStatusEncrypted: Boolean,
        val initialization: HuaweiInitialization.Report? = null,
    )

    fun request(command: Int): List<ByteArray> = guarded {
        check(!closed && pending == null && initializationPending == null)
        // Do not expose other commands until the minimal readback is verified.
        require(when {
            product == null -> command == HuaweiDeviceInfo.PRODUCT
            !batteryAttempted -> command == HuaweiDeviceInfo.BATTERY ||
                (command == HuaweiDeviceInfo.TIME && !timeSynchronized)
            connectStatusSupported == null -> command == HuaweiDeviceInfo.COMMANDS ||
                (command == HuaweiDeviceInfo.SERVICES && !servicesDiscovered)
            else -> connectStatusSupported == true && !connectStatusAcknowledged &&
                command == HuaweiDeviceInfo.CONNECT_STATUS
        })
        lastMetadata = ""
        // LPv2 service requests use a 16-byte IV. HiChain's ISO exchange has its own nonce format.
        val iv = ByteArray(16).also(random::nextBytes)
        remember(iv)
        val payload = HuaweiEncryptedPayload.seal(HuaweiEncryptedPayload.Mode.GCM,
            secret, iv, HuaweiDeviceInfo.query(command))
        HuaweiLinkFrames.encodeWithin(HuaweiDeviceInfo.SERVICE, command, payload, frameBudget)
            .also { pending = command }
    }

    fun receive(message: HuaweiLinkFrames.Message) = guarded {
        check(!closed)
        val expected = checkNotNull(pending)
        require(message.service == HuaweiDeviceInfo.SERVICE && message.command == expected)
        lastMetadata = "Outer " + metadata(message.payload)
        val outer = HuaweiTlv.decode(message.payload)
        val encrypted = outer.any { it.tag in 0x7c..0x7e }
        val plain = if (encrypted) {
            val iv = requireNotNull(HuaweiTlv.one(outer, 0x7d))
            remember(iv)
            HuaweiEncryptedPayload.open(HuaweiEncryptedPayload.Mode.GCM, secret, message.payload)
        } else {
            // Never downgrade a malformed/invalid ciphertext. ProductInfo
            // cannot take this branch even when a watch sends plaintext.
            require(product != null)
            require(when (expected) {
                HuaweiDeviceInfo.BATTERY -> outer.isNotEmpty() &&
                    outer.all { it.tag in 1..3 || it.tag == 0x7f } &&
                    HuaweiTlv.one(outer, 1)?.size == 1
                HuaweiDeviceInfo.TIME -> outer.isNotEmpty() &&
                    outer.all { it.tag in 1..2 || it.tag == 0x7f }
                HuaweiDeviceInfo.SERVICES -> outer.all { it.tag == 2 || it.tag == 0x7f }
                HuaweiDeviceInfo.COMMANDS -> outer.all { it.tag == 0x81 || it.tag == 0x7f }
                HuaweiDeviceInfo.CONNECT_STATUS -> outer.size == 1 && outer[0].tag == 0x7f
                else -> false
            }) { "Unexpected plaintext response" }
            message.payload.copyOf()
        }
        try {
            lastMetadata += if (encrypted) "; GCM verified; inner " + metadata(plain)
                else "; plaintext device reply after GCM ProductInfo"
            val decoded = HuaweiLinkFrames.Message(message.service, message.command, plain)
            when (expected) {
                HuaweiDeviceInfo.PRODUCT -> product = HuaweiDeviceInfo.product(decoded)
                HuaweiDeviceInfo.BATTERY -> {
                    battery = HuaweiDeviceInfo.battery(decoded)
                    batteryAttempted = true
                    batteryEncrypted = encrypted
                }
                HuaweiDeviceInfo.TIME -> {
                    HuaweiDeviceInfo.timeAcknowledged(decoded)
                    timeSynchronized = true
                }
                HuaweiDeviceInfo.SERVICES -> {
                    HuaweiDeviceInfo.services(decoded)
                    servicesDiscovered = true
                }
                HuaweiDeviceInfo.COMMANDS -> connectStatusSupported = HuaweiDeviceInfo.supportsConnectStatus(decoded)
                HuaweiDeviceInfo.CONNECT_STATUS -> {
                    HuaweiDeviceInfo.connectAcknowledged(decoded)
                    connectStatusAcknowledged = true
                    statusEncrypted = encrypted
                }
                else -> error("Unexpected Huawei read")
            }
            pending = null
        } finally {
            plain.fill(0)
        }
    }

    /** The known optional clock step can be skipped on a real response timeout. */
    fun skipClockTimeout() {
        check(!closed && product != null && pending == HuaweiDeviceInfo.TIME)
        pending = null
    }

    /** A real timeout after key proof is missing telemetry, not failed authentication. */
    fun skipBatteryTimeout() {
        check(!closed && product != null && pending == HuaweiDeviceInfo.BATTERY && !batteryAttempted)
        pending = null
        batteryAttempted = true
    }

    fun requestInitialization(service: Int, command: Int, body: ByteArray): List<ByteArray> = guarded {
        check(!closed && product != null && batteryAttempted && pending == null && initializationPending == null)
        require(HuaweiInitialization.allowed(service, command))
        lastMetadata = ""
        val iv = ByteArray(16).also(random::nextBytes)
        remember(iv)
        val encrypted = HuaweiEncryptedPayload.seal(HuaweiEncryptedPayload.Mode.GCM, secret, iv, body)
        HuaweiLinkFrames.encodeWithin(service, command, encrypted, frameBudget)
            .also { initializationPending = service to command }
    }

    class InitializationReply(val payload: ByteArray, val encrypted: Boolean) : Closeable {
        override fun close() { payload.fill(0) }
    }

    /** This is only a command-scoped reply. The caller validates its schema and status. */
    fun receiveInitialization(message: HuaweiLinkFrames.Message): InitializationReply = guarded {
        check(!closed && initializationPending == (message.service to message.command))
        lastMetadata = "Outer " + metadata(message.payload)
        val outer = HuaweiTlv.decode(message.payload)
        val encrypted = outer.any { it.tag in 0x7c..0x7e }
        val plain = if (encrypted) {
            val iv = requireNotNull(HuaweiTlv.one(outer, 0x7d))
            remember(iv)
            HuaweiEncryptedPayload.open(HuaweiEncryptedPayload.Mode.GCM, secret, message.payload)
        } else {
            // No fallback from invalid GCM; proof was established by ProductInfo.
            require(product != null)
            message.payload.copyOf()
        }
        lastMetadata += if (encrypted) "; GCM verified; inner " + metadata(plain)
            else "; plaintext initialization reply"
        initializationPending = null
        InitializationReply(plain, encrypted)
    }

    fun finishInitializationWithoutReply(service: Int, command: Int) {
        check(!closed && initializationPending == (service to command))
        initializationPending = null
    }

    fun readback(): Readback? {
        check(!closed)
        val p = product ?: return null
        if (!batteryAttempted) return null
        return Readback(p, battery, batteryEncrypted, connectStatusAcknowledged, statusEncrypted)
    }

    private fun metadata(payload: ByteArray): String = runCatching {
        val fields = HuaweiTlv.decode(payload)
        val tags = fields.joinToString(",") { "%02X:%d".format(java.util.Locale.ROOT, it.tag, it.value.size) }
        val status = HuaweiTlv.one(fields, 0x7f)?.takeIf { it.size == 4 }?.let {
            java.nio.ByteBuffer.wrap(it).int.toUInt().toString(16)
        }
        "tags:lengths=$tags" + if (status == null) "" else "; status=0x$status"
    }.getOrDefault("malformed TLV (content withheld)")

    private fun remember(iv: ByteArray) {
        require(iv.size == 12 || iv.size == 16)
        require(seenIv.none { it.contentEquals(iv) }) { "Repeated Huawei session IV" }
        seenIv.add(iv.copyOf())
    }

    private inline fun <T> guarded(block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    override fun close() {
        secret.fill(0)
        seenIv.forEach { it.fill(0) }
        seenIv.clear()
        closed = true
        pending = null
        initializationPending = null
        product = null
        battery = null
    }
}
