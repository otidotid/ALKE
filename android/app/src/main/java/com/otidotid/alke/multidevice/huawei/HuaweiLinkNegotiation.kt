package com.otidotid.alke.multidevice.huawei

/**
 * Read-only initial LPv2 negotiation probe. Wire fixture:
 * https://github-wiki-see.page/m/quantifiedstudent/mobile-android/wiki/Huawei-Link-protocol-v2
 * It describes a legacy normal-mode response, NOT proof that GT5 uses that mode.
 * Unknown authentication variants and extended TLV lengths are intentionally
 * rejected by the legacy parser. Never use this response as proof of pairing.
 */
internal object HuaweiLinkNegotiation {
    data class LegacyParameters(
        val protocolVersion: Int,
        val maxFrameSize: Int,
        val maxLinkSize: Int,
        val connectionInterval: Int,
        val authenticationVersion: Int,
        val nonce: ByteArray,
    )

    fun request(): ByteArray = HuaweiLinkFrames.encode(
        1, 1, byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
    )

    fun parseLegacyResponse(message: HuaweiLinkFrames.Message): LegacyParameters {
        require(message.service == 1 && message.command == 1)
        val fields = linkedMapOf<Int, ByteArray>()
        var i = 0
        val payload = message.payload
        while (i < payload.size) {
            require(payload.size - i >= 2) { "Truncated negotiation TLV" }
            val tag = payload[i++].toInt() and 255
            val length = payload[i++].toInt() and 255
            require(length < 128 && length <= payload.size - i) {
                "Unsupported or truncated negotiation TLV"
            }
            require(tag !in fields) { "Duplicate negotiation field" }
            fields[tag] = payload.copyOfRange(i, i + length)
            i += length
        }
        fun field(tag: Int) = requireNotNull(fields[tag]) { "Missing negotiation field" }
        fun number(tag: Int): Int {
            val value = field(tag)
            require(value.size in 1..2)
            return value.fold(0) { n, b -> (n shl 8) or (b.toInt() and 255) }
        }
        val auth = field(5)
        require(auth.size == 18) { "Unrecognized authentication parameters" }
        val authVersion = ((auth[0].toInt() and 255) shl 8) or (auth[1].toInt() and 255)
        return LegacyParameters(
            number(1), number(2), number(3), number(4),
            authVersion, auth.copyOfRange(2, 18)
        ).also {
            require(it.maxFrameSize > 0 && it.maxLinkSize > 0)
        }
    }
}
