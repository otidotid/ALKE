package com.otidotid.alke.multidevice.huawei

import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiPairingProtocolTest {
    @Test fun firstBindAndSavedReconnectUseTheWatchWireFormat() {
        val watch = Watch()
        var pending: ByteArray? = null
        var saved: ByteArray? = null
        val paired = watch.open().use { link ->
            protocol(link).authenticate(IDENTITY, MODEL, null, false, true, { token ->
                watch.events += "pending"
                pending = token.copyOf()
            }, { token ->
                watch.events += "verified"
                saved = token.copyOf()
            })
        }
        assertEquals("VLI-B19", paired.product.model)
        assertEquals("6.0.0.119", paired.product.firmware)
        assertEquals(73, paired.batteryPercent)
        assertArrayEquals(DEVICE_TOKEN, pending)
        assertArrayEquals(DEVICE_TOKEN, saved)
        assertEquals(
            listOf("link", "status", "security:5", "pin", "iso:1", "iso:2", "iso:3", "pending", "iso:4",
                "iso:17", "iso:18", "iso:19", "product", "clock", "battery", "services", "commands", "expand", "account", "settings", "connect status", "setup", "wear", "verified"),
            watch.events,
        )

        watch.events.clear()
        val reconnected = watch.open().use { link ->
            protocol(link).authenticate(IDENTITY, MODEL, saved, false, false,
                { error("Reconnect attempted to stage a binding") },
                { error("Reconnect attempted to overwrite a verified binding") })
        }
        assertEquals(paired, reconnected)
        assertEquals(listOf("link", "status", "security:6", "iso:17", "iso:18", "iso:19", "product", "clock", "battery", "services", "commands", "expand", "account", "settings", "connect status", "setup", "wear"),
            watch.events)
    }

    @Test fun aDroppedReadbackResumesThePendingBindingWithoutAnotherPinRequest() {
        val watch = Watch().apply { dropNextBattery = true }
        var pending: ByteArray? = null
        watch.open().use { link ->
            assertThrows(IOException::class.java) {
                protocol(link).authenticate(IDENTITY, MODEL, null, false, true,
                    { pending = it.copyOf() }, { error("Binding was verified without a battery reply") })
            }
        }
        assertNotNull(pending)

        watch.events.clear()
        val resumed = watch.open().use { link ->
            protocol(link).authenticate(IDENTITY, MODEL, pending, true, false,
                { error("Recovery attempted another first bind") }, { token ->
                    assertArrayEquals(DEVICE_TOKEN, token)
                    watch.events += "verified"
                })
        }
        assertEquals(73, resumed.batteryPercent)
        assertEquals(listOf("link", "status", "security:6", "iso:17", "iso:18", "iso:19", "product", "clock", "battery", "services", "commands", "expand", "account", "settings", "connect status", "setup", "wear", "verified"),
            watch.events)
    }

    @Test fun accountMismatchNeverSendsResetOrAgreementAndDoesNotPromoteBinding() {
        val watch = Watch().apply { differentAccount = true }
        watch.open().use { link ->
            assertThrows(HuaweiAccountSelectionRequired::class.java) {
                protocol(link).authenticate(IDENTITY, MODEL, null, false, true, {},
                    { error("Account mismatch must not promote the pending binding") })
            }
        }
        assertEquals("account", watch.events.last())
        assertTrue("setup" !in watch.events)
        assertTrue("connect status" !in watch.events)
    }

    @Test fun optionalConnectTimeoutAndLateSetupReplyDoNotAbortAuthentication() {
        val watch = Watch().apply {
            silentConnectStatus = true
            lateSetupReply = true
        }
        val result = watch.open().use { link ->
            protocol(link).authenticate(IDENTITY, MODEL, null, false, true, {}, {})
        }
        val init = requireNotNull(result.initialization)
        assertTrue(init.connectSupported)
        assertTrue(!init.connectReply)
        assertTrue(init.notes.any { it.contains("no reply") })
        assertTrue(init.notes.any { it.contains("not sent without explicit consent") })
        assertTrue("wear" in watch.events)
    }

    private fun protocol(link: Watch.Link) = HuaweiPairingProtocol(HuaweiHiChainTransport(link) {})

    private class Watch {
        val events = mutableListOf<String>()
        var dropNextBattery = false
        var differentAccount = false
        var silentConnectStatus = false
        var lateSetupReply = false
        private var bound = false

        fun open() = Link()

        inner class Link : HuaweiHiChainTransport.Link {
            private var reply = byteArrayOf()
            private var offset = 0
            private var exchange: Exchange? = null
            private lateinit var deviceSessionKey: ByteArray

            override fun write(frame: ByteArray) {
                assertEquals(reply.size, offset)
                assertEquals(0x5a, unsigned(frame[0]))
                assertEquals(frame.size - 5, ByteBuffer.wrap(frame, 1, 2).short.toInt())
                assertEquals(0, unsigned(frame[3]))
                val service = unsigned(frame[4])
                assertTrue(service in setOf(1, 0x1a))
                assertEquals(crc(frame.copyOfRange(0, frame.size - 2)),
                    ByteBuffer.wrap(frame, frame.size - 2, 2).short.toInt() and 0xffff)
                val command = unsigned(frame[5])
                val body = frame.copyOfRange(6, frame.size - 2)
                reply = if (service == 0x1a) {
                    assertEquals(5, command)
                    events += "account"
                    assertArrayEquals(bytes(1, 1, 0, 3, 1, 1), decryptRequest(body))
                    packet(command, bytes(2, 1, if (differentAccount) 1 else 0), service = 0x1a)
                } else when (command) {
                    1 -> {
                        events += "link"
                        assertArrayEquals(bytes(1, 0, 2, 0, 3, 0, 4, 0), body)
                        packet(1, bytes(1, 1, 2, 2, 2, 3, 254, 3, 2, 3, 222, 5, 18, 0, 1) +
                            ByteArray(16) + bytes(7, 1, 4, 9, 1, 0))
                    }
                    0x16 -> {
                        events += "status"
                        assertArrayEquals(bytes(1, 0), body)
                        packet(command, bytes(1, 1, 1))
                    }
                    0x33 -> {
                        events += if (bound) "security:6" else "security:5"
                        val fields = fields(body)
                        assertArrayEquals(bytes(4), fields.getValue(1))
                        assertArrayEquals(bytes(1), fields.getValue(2))
                        assertArrayEquals(IDENTITY.toByteArray(), fields.getValue(5))
                        assertArrayEquals(MODEL.toByteArray(), fields.getValue(7))
                        packet(command, bytes(1, 1, 4, 2, 1, if (bound) 2 else 1) + tlv(5, PEER_ID))
                    }
                    0x2c -> {
                        events += "pin"
                        assertTrue(!bound)
                        assertArrayEquals(bytes(1, 0), body)
                        val iv = ByteArray(16) { it.toByte() }
                        val ciphertext = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                            init(Cipher.ENCRYPT_MODE, SecretKeySpec(fromHex("70FB6C24035FDB552F38898AEEDE3F69"), "AES"),
                                IvParameterSpec(iv))
                            doFinal(PIN)
                        }
                        packet(command, tlv(1, ciphertext) + tlv(2, iv))
                    }
                    0x28 -> iso(body)
                    7 -> {
                        events += "product"
                        val requested = fields(decryptRequest(body))
                        assertArrayEquals(byteArrayOf(), requested.getValue(7))
                        assertArrayEquals(byteArrayOf(), requested.getValue(10))
                        // Cross 127 bytes in both the inner TLV and the ciphertext envelope.
                        encryptedReply(command, tlv(7, "6.0.0.119".toByteArray()) +
                            tlv(10, "VLI-B19".toByteArray()) + tlv(0x11, ByteArray(135) { 0x41 }))
                    }
                    5 -> {
                        events += "clock"
                        val clock = fields(decryptRequest(body))
                        assertEquals(4, clock.getValue(1).size)
                        assertEquals(2, clock.getValue(2).size)
                        packet(command, tlv(1, clock.getValue(1)))
                    }
                    2 -> {
                        events += "services"
                        assertArrayEquals(bytes(1, 10, 1, 7, 0x0a, 0x17, 0x19, 0x1a, 0x20, 0x24, 0x2c, 0x34), decryptRequest(body))
                        packet(command, bytes(2, 10, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1))
                    }
                    8 -> {
                        events += "battery"
                        assertArrayEquals(bytes(1, 0), decryptRequest(body))
                        if (dropNextBattery) {
                            dropNextBattery = false
                            byteArrayOf()
                        } else packet(command, bytes(1, 1, 73))
                    }
                    3 -> {
                        events += "commands"
                        assertArrayEquals(bytes(0x81, 16, 2, 1, 1, 3, 4, 0x30, 0x31, 0x35, 0x37,
                            2, 1, 0x1a, 3, 2, 5, 6), decryptRequest(body))
                        packet(command, bytes(0x81, 16, 2, 1, 1, 4, 4, 1, 1, 1, 1,
                            2, 1, 0x1a, 4, 2, 1, 1))
                    }
                    0x37 -> {
                        events += "expand"
                        assertArrayEquals(bytes(1, 0), decryptRequest(body))
                        val bits = ByteArray(22)
                        bits[13] = 32 // capability 109
                        bits[21] = 16 // capability 172
                        encryptedReply(command, tlv(1, bits))
                    }
                    0x31 -> {
                        events += "settings"
                        assertArrayEquals(bytes(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0), decryptRequest(body))
                        packet(command, bytes(2, 1, 0x0a))
                    }
                    0x3e -> {
                        events += "setup"
                        val setup = fields(decryptRequest(body))
                        assertArrayEquals(bytes(1), setup.getValue(1))
                        assertArrayEquals("Huawei Watch GT 5".toByteArray(), setup.getValue(2))
                        assertArrayEquals(bytes(0), setup.getValue(3))
                        // The actual setup request can be one-way; don't block on it.
                        byteArrayOf()
                    }
                    0x3d -> {
                        events += "wear"
                        assertArrayEquals(bytes(1, 0), decryptRequest(body))
                        val wear = packet(command, bytes(1, 1, 1))
                        if (lateSetupReply) packet(0x3e, bytes(0x7f, 4, 0, 1, 0x86, 0xa0)) + wear else wear
                    }
                    0x35 -> {
                        events += "connect status"
                        assertArrayEquals(bytes(1, 1, 1), decryptRequest(body))
                        if (silentConnectStatus) byteArrayOf() else packet(command, bytes(0x7f, 4, 0, 1, 0x86, 0xa0))
                    }
                    else -> error("Unexpected LPv2 command $command")
                }
                offset = 0
            }

            private fun iso(body: ByteArray): ByteArray {
                val fields = fields(body)
                val op = unsigned(fields.getValue(2).single())
                val requestId = fields.getValue(3)
                val request = JSONObject(fields.getValue(1).toString(Charsets.UTF_8))
                val message = request.getInt("message")
                val payload = request.getJSONObject("payload")
                events += "iso:$message"
                if (op == 1) {
                    assertEquals(ByteBuffer.wrap(requestId).long.toString(), request.getString("requestId"))
                    assertEquals(IDENTITY, request.getString("peerDeviceId"))
                    assertEquals(IDENTITY, request.getString("connDeviceId"))
                }
                when (message) {
                    1, 17 -> {
                        assertEquals(if (message == 1) 1 else 2, op)
                        val self = fromHex(payload.getString("peerAuthId"))
                        assertArrayEquals(IDENTITY.toByteArray(), self)
                        if (message == 1) {
                            assertArrayEquals(bytes(1, 0x85, 0x1f), body.copyOfRange(0, 3))
                        } else {
                            assertTrue(bound)
                            assertEquals("com.huawei.devicegroupmanage", payload.getString("pkgName"))
                        }
                        val salt = fromHex(payload.getString("isoSalt"))
                        val seed = fromHex(payload.getString("seed"))
                        assertEquals(16, salt.size)
                        assertEquals(32, seed.size)
                        val base = if (op == 1) MessageDigest.getInstance("SHA-256").digest(hex(PIN).toByteArray())
                            else DEVICE_TOKEN
                        val current = Exchange(op, requestId, salt, self, hmac(base, seed))
                        exchange = current
                        val proof = hmac(current.psk, WATCH_SALT + salt + self + PEER_ID)
                        val response = JSONObject().put("isoSalt", hex(WATCH_SALT)).put("peerAuthId", hex(PEER_ID))
                            .put("token", hex(proof)).put("peerUserType", 0)
                        return packet(3, ByteArray(42), service = 0x25) + jsonReply(current, 0x8011, response)
                    }
                }
                val current = checkNotNull(exchange)
                assertEquals(current.op, op)
                assertArrayEquals(current.requestId, requestId)
                return when (message) {
                    2, 18 -> {
                        assertArrayEquals(hmac(current.psk, current.salt + WATCH_SALT + PEER_ID + current.self),
                            fromHex(payload.getString("token")))
                        jsonReply(current, 0x8012, JSONObject().put("returnCodeMac", hex(hmac(current.psk, ByteArray(4)))))
                    }
                    3 -> {
                        val challenge = gcm(Cipher.DECRYPT_MODE, current.key, fromHex(payload.getString("nonce")),
                            fromHex(payload.getString("encData")), "hichain_iso_exchange".toByteArray())
                        assertEquals(16, challenge.size)
                        val nonce = ByteArray(12) { 0x11 }
                        val token = gcm(Cipher.ENCRYPT_MODE, current.key, nonce, DEVICE_TOKEN, challenge)
                        jsonReply(current, 0x8013, JSONObject().put("nonce", hex(nonce)).put("encAuthToken", hex(token)))
                    }
                    4, 19 -> {
                        val result = gcm(Cipher.DECRYPT_MODE, current.key, fromHex(payload.getString("nonce")),
                            fromHex(payload.getString("encResult")), "hichain_iso_result".toByteArray())
                        assertArrayEquals(ByteArray(4), result)
                        if (op == 1) bound = true
                        else deviceSessionKey = derive(current.key, current.salt + WATCH_SALT, "hichain_return_key")
                        exchange = null
                        packet(0x28, tlv(1, "com.huawei.health".toByteArray()) + tlv(2, bytes(op)) +
                            tlv(3, ByteArray(8)) + bytes(4, 1, 2, 5, 1, 0))
                    }
                    else -> error("Unexpected HiChain message $message")
                }
            }

            private fun jsonReply(exchange: Exchange, message: Int, payload: JSONObject): ByteArray {
                val json = JSONObject().put("message", message).put("payload", payload).toString() + '\u0000'
                return packet(0x28, tlv(1, json.toByteArray()) + tlv(2, bytes(exchange.op)) +
                    tlv(3, exchange.requestId) + bytes(4, 1, 0, 5, 1, 0))
            }

            private fun decryptRequest(body: ByteArray): ByteArray {
                val encrypted = fields(body)
                assertArrayEquals(bytes(1), encrypted.getValue(0x7c))
                val iv = encrypted.getValue(0x7d)
                assertEquals(16, iv.size)
                return gcm(Cipher.DECRYPT_MODE, deviceSessionKey, iv, encrypted.getValue(0x7e))
            }

            private fun encryptedReply(command: Int, plain: ByteArray): ByteArray {
                val iv = ByteArray(16) { command.toByte() }
                val ciphertext = gcm(Cipher.ENCRYPT_MODE, deviceSessionKey, iv, plain)
                return packet(command, bytes(0x7c, 1, 1) + tlv(0x7d, iv) + tlv(0x7e, ciphertext))
            }

            override fun read(buffer: ByteArray, timeoutMillis: Long): Int {
                if (offset == reply.size && silentConnectStatus && events.lastOrNull() == "connect status")
                    throw java.net.SocketTimeoutException("Simulated optional timeout")
                return read(buffer)
            }

            override fun read(buffer: ByteArray): Int {
                if (offset == reply.size) throw IOException("Simulated watch disconnected")
                // RFCOMM may return partial packets and the tail of consecutive packets together.
                val end = minOf(offset + 37, reply.size)
                reply.copyInto(buffer, 0, offset, end)
                return (end - offset).also { offset = end }
            }

            override fun close() = Unit
        }
    }

    private class Exchange(val op: Int, val requestId: ByteArray, val salt: ByteArray, val self: ByteArray, val psk: ByteArray) {
        val key = derive(psk, salt + WATCH_SALT, "hichain_iso_session_key")
    }

    // The watch fixture exercises wire bytes independently of ALKE's frame, TLV and cryptography helpers.
    private companion object {
        const val IDENTITY = "00112233445566778899AABBCCDDEEFF"
        const val MODEL = "Protocol test handset"
        val PEER_ID = "0123456789ABCDEF".repeat(4).toByteArray()
        val WATCH_SALT = ByteArray(16) { (0xf0 + it).toByte() }
        val DEVICE_TOKEN = ByteArray(32) { (0xc0 + it).toByte() }
        val PIN = ByteArray(64) { it.toByte() }

        fun unsigned(value: Byte) = value.toInt() and 0xff
        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
        fun fromHex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun hex(value: ByteArray) = value.joinToString("") { "%02X".format(unsigned(it)) }

        fun tlv(tag: Int, value: ByteArray): ByteArray {
            require(value.size < 16384)
            val length = if (value.size < 128) bytes(value.size)
                else bytes(0x80 + value.size / 128, value.size % 128)
            return bytes(tag) + length + value
        }

        fun fields(encoded: ByteArray): Map<Int, ByteArray> = buildMap {
            var offset = 0
            while (offset < encoded.size) {
                val tag = unsigned(encoded[offset++])
                val first = unsigned(encoded[offset++])
                val size = if (first < 128) first else (first - 128) * 128 + unsigned(encoded[offset++])
                require(size <= encoded.size - offset) { "Watch rejected TLV length $size" }
                check(put(tag, encoded.copyOfRange(offset, offset + size)) == null)
                offset += size
            }
        }

        fun packet(command: Int, payload: ByteArray, service: Int = 1): ByteArray {
            val length = payload.size + 3
            val frame = bytes(0x5a, length / 256, length % 256, 0, service, command) + payload
            val checksum = crc(frame)
            return frame + bytes(checksum / 256, checksum % 256)
        }

        fun crc(frame: ByteArray): Int {
            var register = 0
            for (octet in frame) {
                for (bit in 7 downTo 0) {
                    val feedback = ((register ushr 15) xor (unsigned(octet) ushr bit)) and 1
                    register = (register shl 1) and 0xffff
                    if (feedback != 0) register = register xor 0x1021
                }
            }
            return register
        }

        fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

        fun derive(key: ByteArray, salt: ByteArray, purpose: String) =
            hmac(hmac(salt, key), purpose.toByteArray() + bytes(1))

        fun gcm(mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray, aad: ByteArray = byteArrayOf()): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                updateAAD(aad)
                doFinal(data)
            }
    }
}
