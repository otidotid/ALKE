package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiEncryptedPayloadTest {
    private val key = ByteArray(16) { it.toByte() }

    @Test fun roundTripAcrossExtendedTlvLengths() {
        val plaintext = ByteArray(200) { it.toByte() }
        for (mode in HuaweiEncryptedPayload.Mode.values()) {
            val iv = ByteArray(16) { (it + 32).toByte() }
            val packet = HuaweiEncryptedPayload.seal(mode, key, iv, plaintext)
            assertArrayEquals(plaintext, HuaweiEncryptedPayload.open(mode, key, packet))
        }
    }

    @Test fun gcmRejectsChangedCiphertextAndWrongKey() {
        val mode = HuaweiEncryptedPayload.Mode.GCM
        val envelope = HuaweiEncryptedPayload.seal(mode, key, ByteArray(12), byteArrayOf(4, 5, 6))
        val corrupted = envelope.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        assertTrue(runCatching { HuaweiEncryptedPayload.open(mode, key, corrupted) }.isFailure)
        assertTrue(runCatching { HuaweiEncryptedPayload.open(mode, ByteArray(16), envelope) }.isFailure)
    }

    @Test fun unknownProtocolDoesNotDowngrade() {
        val base = HuaweiAuthCapabilities.Parameters(2, 1022, 990, 1, 4, 0, 0, 0)
        assertEquals(HuaweiEncryptedPayload.Mode.GCM, HuaweiEncryptedPayload.modeFor(base))
        assertEquals(HuaweiEncryptedPayload.Mode.CBC,
            HuaweiEncryptedPayload.modeFor(base.copy(supportType = 0)))
        assertNull(HuaweiEncryptedPayload.modeFor(base.copy(supportType = null)))
        assertNull(HuaweiEncryptedPayload.modeFor(base.copy(encryptMethod = 8)))
    }

    @Test fun rejectsDuplicateEnvelopeFields() {
        val envelope = HuaweiEncryptedPayload.seal(HuaweiEncryptedPayload.Mode.GCM,
            key, ByteArray(12), byteArrayOf(4, 5, 6))
        assertTrue(runCatching {
            HuaweiEncryptedPayload.open(HuaweiEncryptedPayload.Mode.GCM, key,
                envelope + byteArrayOf(0x7c, 1, 1))
        }.isFailure)
    }
}
