package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HuaweiFirstBindTest {
    private val parameters = HuaweiAuthCapabilities.Parameters(2, 1022, 990, 1, 4, 0, 0, 0)
    private val key = "70FB6C24035FDB552F38898AEEDE3F69".chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()
    private val pin = byteArrayOf(0x01, 0x23, 0xAB.toByte(), 0xEF.toByte())

    private fun response(gcm: Boolean, corrupt: Boolean = false): HuaweiLinkFrames.Message {
        val iv = ByteArray(if (gcm) 12 else 16) { 0x11 }
        val cipher = if (gcm) {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
        } else {
            Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }
        }
        val bytes = cipher.doFinal(pin)
        if (corrupt) bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        return HuaweiLinkFrames.Message(1, 0x2c,
            byteArrayOf(1, bytes.size.toByte()) + bytes + byteArrayOf(2, iv.size.toByte()) + iv)
    }

    @Test fun initialReadRequiresExplicitConsentAndSupportedMode() {
        assertTrue(runCatching { HuaweiFirstBind.request(parameters, false) }.isFailure)
        assertTrue(runCatching { HuaweiFirstBind.request(parameters.copy(authVersion = 9), true) }.isFailure)
        assertTrue(runCatching { HuaweiFirstBind.request(parameters.copy(supportType = 2), true) }.isFailure)
        val packet = HuaweiLinkFrames.Decoder().feed(HuaweiFirstBind.request(parameters, true)).single()
        assertEquals(0x2c, packet.command)
        assertArrayEquals(byteArrayOf(1, 0), packet.payload)
    }

    @Test fun supportedCipherVariantsDeriveSameIsoCredential() {
        val expected = MessageDigest.getInstance("SHA-256").digest("0123ABEF".toByteArray(Charsets.US_ASCII))
        assertArrayEquals(expected, HuaweiFirstBind.credential(parameters, response(false)))
        assertArrayEquals(expected, HuaweiFirstBind.credential(parameters.copy(encryptMethod = 1), response(true)))
    }

    @Test fun invalidGcmOrMissingFieldsFailClosed() {
        assertTrue(runCatching {
            HuaweiFirstBind.credential(parameters.copy(encryptMethod = 1), response(true, true))
        }.isFailure)
        assertTrue(runCatching {
            HuaweiFirstBind.credential(parameters, HuaweiLinkFrames.Message(1, 0x2c, byteArrayOf(1, 0)))
        }.isFailure)
        assertTrue(runCatching {
            HuaweiFirstBind.credential(parameters, HuaweiLinkFrames.Message(1, 0x2c, byteArrayOf(0x7f, 1, 1)))
        }.isFailure)
    }
}
