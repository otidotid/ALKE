package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test

class HuaweiHandshakeTraceTest {
    @Test fun unrelatedPacketCannotBeAnAuthenticationReply() {
        val trace = HuaweiHandshakeTrace()
        trace.sent(700)
        trace.received(27)
        val payload = "DO_NOT_LOG_PAYLOAD".toByteArray()
        assertFalse(trace.matches(HuaweiLinkFrames.Message(1, 0x16, payload), 0x28))
        assertTrue(trace.matches(HuaweiLinkFrames.Message(1, 0x28, payload), 0x28))
        assertEquals(1, trace.unmatched)
        assertTrue(trace.summary().contains("01/16"))
        assertFalse(trace.summary().contains("DO_NOT_LOG_PAYLOAD"))
        trace.failure("socket EOF")
        assertTrue(trace.summary().contains("socket EOF"))
    }
}
