package com.otidotid.alke.multidevice.huawei

import java.io.ByteArrayOutputStream

/**
 * LPv2 security negotiation for support type 4. Based on publicly documented
 * command 01/33 fields, including the Watch Fit 4 observations in JoyoFit
 * protocol/README.md. GT5 firmware compatibility is still unverified.
 * This command selects a handshake; it does NOT bind or authenticate.
 */
internal object HuaweiSecurityNegotiation {
    enum class NextStep { FIRST_BIND, RECONNECT, UNSUPPORTED }

    fun request(parameters: HuaweiAuthCapabilities.Parameters, clientId: String, model: String): ByteArray {
        require(parameters.supportType == 4)
        require(parameters.encryptMethod == 0 || parameters.encryptMethod == 1)
        require(clientId.matches(Regex("[0-9A-Fa-f]{32}")))
        val phone = model.toByteArray(Charsets.UTF_8)
        require(phone.size in 1..120)
        val out = ByteArrayOutputStream()
        fun field(tag: Int, bytes: ByteArray) {
            require(bytes.size < 128)
            out.write(tag)
            out.write(bytes.size)
            out.write(bytes)
        }
        field(1, byteArrayOf(4))
        field(2, byteArrayOf(1))
        field(5, clientId.toByteArray(Charsets.US_ASCII))
        field(3, byteArrayOf(1))
        field(4, byteArrayOf(0))
        field(6, byteArrayOf())
        field(7, phone)
        if (parameters.encryptMethod == 1) field(13, byteArrayOf(1))
        return HuaweiLinkFrames.encode(1, 0x33, out.toByteArray())
    }

    fun parse(message: HuaweiLinkFrames.Message): NextStep {
        require(message.service == 1 && message.command == 0x33)
        val fields = HuaweiTlv.decode(message.payload)
        // A status/error response is never proof of a successful negotiation.
        if (fields.any { it.tag == 0x7f }) return NextStep.UNSUPPORTED
        if (HuaweiTlv.unsigned(fields, 1, 1) != 4) return NextStep.UNSUPPORTED
        return when (HuaweiTlv.unsigned(fields, 2, 1)) {
            1 -> NextStep.FIRST_BIND
            2 -> NextStep.RECONNECT
            else -> NextStep.UNSUPPORTED
        }
    }
}
