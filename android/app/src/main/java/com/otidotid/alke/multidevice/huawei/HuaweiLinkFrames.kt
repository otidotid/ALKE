package com.otidotid.alke.multidevice.huawei

import java.io.ByteArrayOutputStream

/**
 * Huawei Link Protocol v2 outer wire framing only, independently implemented
 * from https://github.com/zyv/huawei-lpv2/wiki/Wire-Protocol .
 * CRC is corruption detection, NOT cryptographic authentication.
 * No encrypted session, key exchange or device commands are implemented here.
 */
internal object HuaweiLinkFrames {
    const val MAX_BODY = 32766

    fun crcXmodem(bytes: ByteArray, endExclusive: Int = bytes.size): Int {
        require(endExclusive in 0..bytes.size)
        var crc = 0
        for (i in 0 until endExclusive) {
            crc = crc xor ((bytes[i].toInt() and 255) shl 8)
            repeat(8) {
                crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xffff
            }
        }
        return crc
    }

    fun encode(service: Int, command: Int, payload: ByteArray): ByteArray {
        require(service in 0..255 && command in 0..255)
        require(payload.size <= MAX_BODY - 2)
        val body = byteArrayOf(service.toByte(), command.toByte()) + payload
        val declared = body.size + 1
        val head = byteArrayOf(0x5a, (declared shr 8).toByte(), declared.toByte(), 0)
        val bytes = head + body
        val crc = crcXmodem(bytes)
        return bytes + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    data class Message(val service: Int, val command: Int, val payload: ByteArray)

    /** One decoder per transport session; discard/reset on any malformed frame. */
    class Decoder {
        private var pending = ByteArray(0)
        private var sliced: ByteArrayOutputStream? = null
        private var nextIndex = 0

        fun reset() {
            pending = ByteArray(0)
            sliced = null
            nextIndex = 0
        }

        fun feed(chunk: ByteArray): List<Message> {
            try {
                require(chunk.size <= 65536)
                pending += chunk
                val out = ArrayList<Message>()
                var offset = 0
                while (pending.size - offset >= 3) {
                    require(u(pending[offset]) == 0x5a) { "Huawei magic" }
                    val len = (u(pending[offset + 1]) shl 8) or u(pending[offset + 2])
                    require(len in 3..32767) { "Huawei length" }
                    val total = len + 5
                    if (pending.size - offset < total) break
                    val packet = pending.copyOfRange(offset, offset + total)
                    val check = (u(packet[total - 2]) shl 8) or u(packet[total - 1])
                    require(crcXmodem(packet, total - 2) == check) { "Huawei CRC" }
                    val flag = u(packet[3])
                    if (flag == 0) {
                        require(sliced == null) { "Unexpected unsliced frame" }
                        out.add(message(packet.copyOfRange(4, total - 2)))
                    } else {
                        require(flag in 1..3) { "Huawei slice flag" }
                        val index = u(packet[4])
                        val part = packet.copyOfRange(5, total - 2)
                        if (flag == 1) {
                            require(sliced == null && index == 0) { "Unexpected first slice" }
                            sliced = ByteArrayOutputStream()
                            nextIndex = 0
                        }
                        val stream = requireNotNull(sliced) { "Missing first slice" }
                        require(index == nextIndex && stream.size() + part.size <= MAX_BODY) {
                            "Huawei slice sequence or length"
                        }
                        stream.write(part)
                        nextIndex = (nextIndex + 1) and 255
                        if (flag == 3) {
                            out.add(message(stream.toByteArray()))
                            sliced = null
                        }
                    }
                    offset += total
                }
                pending = pending.copyOfRange(offset, pending.size)
                return out
            } catch (e: IllegalArgumentException) {
                reset()
                throw e
            }
        }

        private fun message(body: ByteArray): Message {
            require(body.size >= 2) { "Huawei body too short" }
            return Message(u(body[0]), u(body[1]), body.copyOfRange(2, body.size))
        }
    }

    private fun u(value: Byte): Int = value.toInt() and 255
}
