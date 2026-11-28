package com.otidotid.alke.multidevice.huawei

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LPv2 encrypted TLV envelope. Wire facts: marker 7c=01, IV 7d, ciphertext 7e.
 * Uses JCA primitives, not a reimplementation of AES. Does not establish trust,
 * negotiate a key, or authorize a device. Only use with a verified session key
 * and IV supplied by the corresponding authenticated protocol implementation.
 * Legacy CBC offers confidentiality, NOT message authentication; no downgrade.
 */
internal object HuaweiEncryptedPayload {
    enum class Mode { GCM, CBC }

    fun modeFor(parameters: HuaweiAuthCapabilities.Parameters): Mode? = when {
        parameters.supportType !in 0..4 -> null
        parameters.effectiveEncryptMethod !in 0..1 -> null
        parameters.supportType == 4 || parameters.effectiveEncryptMethod == 1 -> Mode.GCM
        else -> Mode.CBC
    }

    fun seal(mode: Mode, key: ByteArray, iv: ByteArray, payload: ByteArray): ByteArray {
        require(payload.size <= HuaweiLinkFrames.MAX_BODY - 128)
        val encrypted = crypt(mode, Cipher.ENCRYPT_MODE, key, iv, payload)
        return HuaweiTlv.encode(
            HuaweiTlv.Field(0x7c, byteArrayOf(1)),
            HuaweiTlv.Field(0x7d, iv),
            HuaweiTlv.Field(0x7e, encrypted),
        )
    }

    @Throws(GeneralSecurityException::class)
    fun open(mode: Mode, key: ByteArray, envelope: ByteArray): ByteArray {
        val fields = HuaweiTlv.decode(envelope)
        require(fields.size == 3)
        require(HuaweiTlv.one(fields, 0x7c)?.contentEquals(byteArrayOf(1)) == true)
        val iv = requireNotNull(HuaweiTlv.one(fields, 0x7d))
        val encrypted = requireNotNull(HuaweiTlv.one(fields, 0x7e))
        return crypt(mode, Cipher.DECRYPT_MODE, key, iv, encrypted)
    }

    private fun crypt(mode: Mode, operation: Int, key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray {
        require(key.size in setOf(16, 24, 32))
        val secret = SecretKeySpec(key, "AES")
        val cipher = when (mode) {
            Mode.GCM -> {
                require(iv.size in setOf(12, 16))
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(operation, secret, GCMParameterSpec(128, iv))
                }
            }
            Mode.CBC -> {
                require(iv.size == 16)
                Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(operation, secret, IvParameterSpec(iv))
                }
            }
        }
        return cipher.doFinal(input)
    }
}
