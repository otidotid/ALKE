package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiLinkProbeTest {
    @Test fun diagnosticRedactsUnknownPayloadAndDoesNotClaimPairing() {
        val secret = "secret-identifier"
        val data = secret.toByteArray()
        val payload = byteArrayOf(0x55, data.size.toByte()) + data
        val report = HuaweiLinkProbe.describe(HuaweiLinkFrames.Message(1, 1, payload))
        assertTrue(report.contains("55:${data.size}"))
        assertFalse(report.contains(secret))
        assertTrue(report.contains("Not paired or authenticated"))
        assertTrue(report.contains("No health data read"))
    }

    @Test fun malformedFieldDoesNotLeakBytes() {
        val report = HuaweiLinkProbe.describe(
            HuaweiLinkFrames.Message(1, 1, byteArrayOf(5, 100, 0x41, 0x42)))
        assertTrue(report.contains("unsupported TLV length"))
        assertFalse(report.contains("AB"))
    }
}
