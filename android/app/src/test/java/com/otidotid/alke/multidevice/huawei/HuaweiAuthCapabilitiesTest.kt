package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiAuthCapabilitiesTest {
    @Test fun extendedLengthAndRepeatedTags() {
        val value = ByteArray(130) { 3 }
        val fields = HuaweiTlv.decode(byteArrayOf(7, 0x82.toByte(), 1) + value +
            byteArrayOf(7, 1, 9))
        assertEquals(2, fields.size)
        assertArrayEquals(value, fields.first().value)
        assertTrue(runCatching { HuaweiTlv.one(fields, 7) }.isFailure)
    }

    @Test fun malformedLengthsRejected() {
        listOf(byteArrayOf(7), byteArrayOf(7, 0x80.toByte()),
            byteArrayOf(7, 2, 1), byteArrayOf(7, 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0)).forEach {
            assertTrue(runCatching { HuaweiTlv.decode(it) }.isFailure)
        }
    }

    @Test fun modernCapabilityFieldsAreNotGuessed() {
        // Synthetic fixture from documented tag layout, not a GT5 capture.
        val auth = byteArrayOf(0, 1) + ByteArray(16) { 55 }
        val payload = byteArrayOf(1, 1, 2, 2, 2, 3, 0xfe.toByte(),
            3, 2, 3, 0xde.toByte(), 5, 18) + auth +
            byteArrayOf(7, 1, 4, 8, 1, 0, 9, 1, 0, 12, 1, 0)
        val message = HuaweiLinkFrames.Message(1, 1, payload)
        val p = HuaweiAuthCapabilities.parse(message)
        assertEquals(2, p.protocolVersion)
        assertEquals(1022, p.sliceSize)
        assertEquals(990, p.mtu)
        assertEquals(1, p.authVersion)
        assertEquals(4, p.supportType)
        assertEquals(0, p.authAlgorithm)
        assertEquals(0, p.bondState)
        assertEquals(0, p.encryptMethod)
        assertTrue(HuaweiLinkProbe.describe(message).contains("Device support type: 4"))
        assertNull(HuaweiAuthCapabilities.parse(
            HuaweiLinkFrames.Message(1, 1, byteArrayOf(1, 1, 2))).supportType)
    }

    @Test fun gt5ReportedTagLayoutUsesBaselineWithoutInventingAdvertisedValue() {
        // Reconstruct only the observed tag lengths and reported nonsecret values.
        val payload = byteArrayOf(1, 1, 2, 2, 2, 3, 0xfe.toByte(),
            3, 2, 3, 0xde.toByte(), 4, 2, 0, 1, 5, 18, 0, 1) + ByteArray(16) +
            byteArrayOf(7, 1, 4, 9, 1, 0, 13, 1, 0, 15, 1, 0, 16, 1, 0)
        val message = HuaweiLinkFrames.Message(1, 1, payload)
        val p = HuaweiAuthCapabilities.parse(message)
        assertNull(p.encryptMethod)
        assertNull(p.authAlgorithm)
        assertEquals(0, p.effectiveEncryptMethod)
        assertNull(p.copy(supportType = 3).effectiveEncryptMethod)
        assertNull(p.copy(authVersion = 2).effectiveEncryptMethod)
        assertEquals(1, p.copy(encryptMethod = 1).effectiveEncryptMethod)
        assertEquals(7, p.copy(encryptMethod = 7).effectiveEncryptMethod)
        val request = HuaweiLinkFrames.Decoder().feed(HuaweiSecurityNegotiation.request(
            p, "00112233445566778899aabbccddeeff", "Test phone")).single()
        assertEquals(0x33, request.command)
        assertNull(HuaweiTlv.one(HuaweiTlv.decode(request.payload), 13))
        assertTrue(HuaweiLinkProbe.describe(message).contains("Effective encryption method: 0 (LPv2 default)"))
        assertTrue(runCatching { HuaweiFirstBind.request(p, false) }.isFailure)
    }

    @Test fun statusReadIsQueryOnly() {
        val frame = HuaweiLinkFrames.Decoder()
            .feed(HuaweiAuthCapabilities.statusRequest()).single()
        assertEquals(1, frame.service)
        assertEquals(0x16, frame.command)
        assertArrayEquals(byteArrayOf(1, 0), frame.payload)
        assertEquals(1, HuaweiAuthCapabilities.parseStatus(
            HuaweiLinkFrames.Message(1, 0x16, byteArrayOf(1, 1, 1))))
    }
}
