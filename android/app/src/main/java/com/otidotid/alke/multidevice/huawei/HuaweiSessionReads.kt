package com.otidotid.alke.multidevice.huawei

import java.io.Closeable
import java.security.SecureRandom

/**
 * Read-only GCM verification of a candidate reconnect session. A terminal
 * HiChain packet alone cannot publish connection state or authorize storage.
 * This class requires both product and battery responses under the candidate
 * key on the current transport. It never reads health data or saves a key.
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
    private var product: HuaweiDeviceInfo.Product? = null
    private var battery: Int? = null

    data class Readback(val product: HuaweiDeviceInfo.Product, val batteryPercent: Int)

    fun request(command: Int): List<ByteArray> = guarded {
        check(!closed && pending == null)
        // Do not expose other commands until the minimal readback is verified.
        require(command == HuaweiDeviceInfo.PRODUCT || command == HuaweiDeviceInfo.BATTERY)
        require(if (product == null) command == HuaweiDeviceInfo.PRODUCT
            else battery == null && command == HuaweiDeviceInfo.BATTERY)
        val iv = ByteArray(12).also(random::nextBytes)
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
        val iv = requireNotNull(HuaweiTlv.one(HuaweiTlv.decode(message.payload), 0x7d))
        remember(iv)
        val plain = HuaweiEncryptedPayload.open(HuaweiEncryptedPayload.Mode.GCM, secret, message.payload)
        try {
            val decoded = HuaweiLinkFrames.Message(message.service, message.command, plain)
            when (expected) {
                HuaweiDeviceInfo.PRODUCT -> product = HuaweiDeviceInfo.product(decoded)
                HuaweiDeviceInfo.BATTERY -> battery = HuaweiDeviceInfo.battery(decoded)
                else -> error("Unexpected Huawei read")
            }
            pending = null
        } finally {
            plain.fill(0)
        }
    }

    fun readback(): Readback? {
        check(!closed)
        val p = product ?: return null
        val b = battery ?: return null
        return Readback(p, b)
    }

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
        product = null
        battery = null
    }
}
