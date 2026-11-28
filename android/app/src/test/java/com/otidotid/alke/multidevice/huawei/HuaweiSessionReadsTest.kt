package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test

class HuaweiSessionReadsTest {
    private val key = ByteArray(32) { 42 }
    private val product = byteArrayOf(7, 3) + "1.0".toByteArray() +
        byteArrayOf(10, 4) + "GT 5".toByteArray()

    private fun encrypted(command: Int, data: ByteArray, counter: Int,
        secret: ByteArray = key): HuaweiLinkFrames.Message {
        val iv = ByteArray(12) { counter.toByte() }
        return HuaweiLinkFrames.Message(1, command,
            HuaweiEncryptedPayload.seal(HuaweiEncryptedPayload.Mode.GCM, secret, iv, data))
    }

    @Test fun requiresBothEncryptedReadsBeforeReadback() {
        HuaweiSessionReads(key, 512).use { reads ->
            val frame = HuaweiLinkFrames.Decoder().feed(reads.request(7).single()).single()
            assertEquals(7, frame.command)
            assertArrayEquals(byteArrayOf(7, 0, 10, 0),
                HuaweiEncryptedPayload.open(HuaweiEncryptedPayload.Mode.GCM, key, frame.payload))
            assertNull(reads.readback())
            reads.receive(encrypted(7, product, 1))
            assertNull(reads.readback())
            reads.request(8)
            reads.receive(encrypted(8, byteArrayOf(1, 1, 76), 2))
            val result = requireNotNull(reads.readback())
            assertEquals("GT 5", result.product.model)
            assertEquals("1.0", result.product.firmware)
            assertEquals(76, result.batteryPercent)
        }
    }

    @Test fun plaintextAndWrongKeyAreRejected() {
        HuaweiSessionReads(key, 512).use { reads ->
            reads.request(7)
            assertTrue(runCatching {
                reads.receive(HuaweiLinkFrames.Message(1, 7, product))
            }.isFailure)
            assertTrue(runCatching { reads.readback() }.isFailure)
        }
        HuaweiSessionReads(key, 512).use { reads ->
            reads.request(7)
            assertTrue(runCatching {
                reads.receive(encrypted(7, product, 1, ByteArray(32)))
            }.isFailure)
            assertTrue(runCatching { reads.readback() }.isFailure)
        }
    }

    @Test fun malformedBatteryAndRepeatedIvFailClosed() {
        for ((iv, percent) in listOf(2 to 101, 1 to 80)) {
            HuaweiSessionReads(key, 512).use { reads ->
                reads.request(7)
                reads.receive(encrypted(7, product, 1))
                reads.request(8)
                assertTrue(runCatching {
                    reads.receive(encrypted(8, byteArrayOf(1, 1, percent.toByte()), iv))
                }.isFailure)
                assertTrue(runCatching { reads.readback() }.isFailure)
            }
        }
    }

    @Test fun outOfOrderResponseAndConcurrentRequestAreRejected() {
        HuaweiSessionReads(key, 512).use { reads ->
            reads.request(7)
            assertTrue(runCatching {
                reads.receive(encrypted(8, byteArrayOf(1, 1, 60), 1))
            }.isFailure)
        }
        HuaweiSessionReads(key, 512).use { reads ->
            reads.request(7)
            assertTrue(runCatching { reads.request(8) }.isFailure)
        }
    }

    @Test fun discoveryReportsOnlyAdvertisedServices() {
        assertEquals(setOf(1, 7), HuaweiDeviceInfo.services(
            HuaweiLinkFrames.Message(1, 2, byteArrayOf(2, 4, 1, 7, 7, 0))))
    }
}
