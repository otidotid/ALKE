package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiSecurityNegotiationTest {
    private val capabilities = HuaweiAuthCapabilities.Parameters(2, 1022, 990, 1, 4, 0, 0, 0)
    private val identity = "00112233445566778899aabbccddeeff"

    @Test fun requestMatchesDocumentedTypeFourFields() {
        val frame = HuaweiLinkFrames.Decoder().feed(
            HuaweiSecurityNegotiation.request(capabilities, identity, "Test phone")
        ).single()
        assertEquals(1, frame.service)
        assertEquals(0x33, frame.command)
        val fields = HuaweiTlv.decode(frame.payload)
        assertEquals(listOf(1, 2, 5, 3, 4, 6, 7), fields.map { it.tag })
        assertArrayEquals(byteArrayOf(4), HuaweiTlv.one(fields, 1))
        assertArrayEquals(identity.toByteArray(Charsets.US_ASCII), HuaweiTlv.one(fields, 5))
        assertArrayEquals(byteArrayOf(), HuaweiTlv.one(fields, 6))
        assertNull(HuaweiTlv.one(fields, 13))
    }

    @Test fun encryptionExtensionOnlyWhenAdvertised() {
        val frame = HuaweiLinkFrames.Decoder().feed(
            HuaweiSecurityNegotiation.request(capabilities.copy(encryptMethod = 1), identity, "Android")
        ).single()
        assertArrayEquals(byteArrayOf(1), HuaweiTlv.one(HuaweiTlv.decode(frame.payload), 13))
        assertTrue(runCatching {
            HuaweiSecurityNegotiation.request(capabilities.copy(supportType = 0), identity, "Android")
        }.isFailure)
    }

    @Test fun hiChainModesAndUnknownAreDistinct() {
        fun response(vararg fields: Byte) = HuaweiSecurityNegotiation.parse(
            HuaweiLinkFrames.Message(1, 0x33, fields)
        )
        val first = byteArrayOf(1, 1, 4, 2, 1, 1)
        assertEquals(HuaweiSecurityNegotiation.AuthMode.HICHAIN3_VARIANT_5, response(*first))
        assertEquals(HuaweiSecurityNegotiation.AuthMode.HICHAIN3_VARIANT_6, response(1, 1, 4, 2, 1, 2))
        assertEquals(HuaweiSecurityNegotiation.AuthMode.UNSUPPORTED, response(1, 1, 4, 2, 1, 3))
        assertEquals(HuaweiSecurityNegotiation.AuthMode.UNSUPPORTED, response(*first, 0x7f, 1, 1))
        assertTrue(runCatching { response(*first, 2, 1, 2) }.isFailure)
    }

    @Test fun firstBindingRequiresConsentAndReconnectRequiresLocalCredential() {
        val first = HuaweiSecurityNegotiation.AuthMode.HICHAIN3_VARIANT_5
        val reconnect = HuaweiSecurityNegotiation.AuthMode.HICHAIN3_VARIANT_6
        for (mode in listOf(first, reconnect)) {
            assertNull(HuaweiSecurityNegotiation.operation(mode, false, false))
            assertEquals(HuaweiIsoExchange.Operation.BIND, HuaweiSecurityNegotiation.operation(mode, false, true))
        }
        assertEquals(HuaweiIsoExchange.Operation.RECONNECT, HuaweiSecurityNegotiation.operation(first, true, false))
        assertEquals(HuaweiIsoExchange.Operation.BIND, HuaweiSecurityNegotiation.operation(first, true, true))
        assertEquals(HuaweiIsoExchange.Operation.RECONNECT, HuaweiSecurityNegotiation.operation(reconnect, true, false))
        assertEquals(HuaweiIsoExchange.Operation.RECONNECT, HuaweiSecurityNegotiation.operation(reconnect, true, true))
        assertNull(HuaweiSecurityNegotiation.operation(HuaweiSecurityNegotiation.AuthMode.UNSUPPORTED, true, true))
    }
}
