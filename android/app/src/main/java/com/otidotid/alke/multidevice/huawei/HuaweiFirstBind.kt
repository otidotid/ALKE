package com.otidotid.alke.multidevice.huawei

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * First-bind credential acquisition for a user-owned watch. Wire command 01/2C
 * and auth-version constants are documented by public Huawei interoperability
 * implementations (DeviceConfig.PinCode and HuaweiCrypto). Independently
 * implemented with JCA. The fixed protocol key is not device authentication:
 * the ISO peer proof and watch confirmation are still required.
 */
internal object HuaweiFirstBind {
    const val COMMAND = 0x2c

    fun request(parameters: HuaweiAuthCapabilities.Parameters, confirmedByUser: Boolean): ByteArray {
        require(confirmedByUser) { "Explicit first-bind consent required" }
        validate(parameters)
        return HuaweiLinkFrames.encode(1, COMMAND, byteArrayOf(1, 0))
    }

    private fun validate(p: HuaweiAuthCapabilities.Parameters) {
        require(p.supportType == 4 && p.authVersion in 1..3 && p.encryptMethod in 0..1) {
            "Unsupported first-bind parameters"
        }
    }

    /** Caller owns returned credential bytes and must clear them after use. */
    fun credential(parameters: HuaweiAuthCapabilities.Parameters, response: HuaweiLinkFrames.Message): ByteArray {
        validate(parameters)
        require(response.service == 1 && response.command == COMMAND)
        val fields = HuaweiTlv.decode(response.payload)
        require(HuaweiTlv.one(fields, 0x7f) == null) { "Watch rejected first bind" }
        val encrypted = requireNotNull(HuaweiTlv.one(fields, 1))
        val iv = requireNotNull(HuaweiTlv.one(fields, 2))
        require(encrypted.size in 16..256)
        val key = protocolKey(requireNotNull(parameters.authVersion))
        val pin = try {
            val cipher = if (parameters.encryptMethod == 1) {
                require(iv.size in setOf(12, 16))
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                }
            } else {
                require(iv.size == 16 && encrypted.size % 16 == 0)
                Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                }
            }
            cipher.doFinal(encrypted)
        } finally { key.fill(0) }
        try {
            require(pin.size in 1..128) { "Invalid first-bind credential size" }
            // ISO op1 uses SHA-256 of the upper-case ASCII hex representation.
            val digits = "0123456789ABCDEF"
            val ascii = ByteArray(pin.size * 2)
            for (i in pin.indices) {
                val n = pin[i].toInt() and 255
                ascii[i * 2] = digits[n ushr 4].code.toByte()
                ascii[i * 2 + 1] = digits[n and 15].code.toByte()
            }
            try { return MessageDigest.getInstance("SHA-256").digest(ascii) }
            finally { ascii.fill(0) }
        } finally { pin.fill(0) }
    }

    private fun protocolKey(version: Int): ByteArray {
        val hex = when (version) {
            1 -> "70FB6C24035FDB552F38898AEEDE3F69"
            2 -> "93ACDEF76ACB09857DBFE5261AABCD78"
            3 -> "9C2763A9CCE134766DE3FF6118200553"
            else -> error("Unsupported authentication version")
        }
        return ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
