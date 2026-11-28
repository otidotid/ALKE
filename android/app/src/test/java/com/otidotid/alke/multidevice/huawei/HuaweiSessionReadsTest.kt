package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test

class HuaweiSessionReadsTest {
    private val key = ByteArray(32) { 42 }
    private val product = byteArrayOf(7, 3) + "1.0".toByteArray() +
        byteArrayOf(10, 4) + "GT 5".toByteArray()

    private fun encrypted(command: Int, data: ByteArray, counter: Int,
        secret: ByteArray = key): HuaweiLinkFrames.Message {
        val iv = ByteArray(16) { counter.toByte() }
        return HuaweiLinkFrames.Message(1, command,
            HuaweiEncryptedPayload.seal(HuaweiEncryptedPayload.Mode.GCM, secret, iv, data))
    }

    private fun requestPayload(reads: HuaweiSessionReads, command: Int): ByteArray {
        val frame = HuaweiLinkFrames.Decoder().feed(reads.request(command).single()).single()
        assertEquals(command, frame.command)
        val iv = HuaweiTlv.one(HuaweiTlv.decode(frame.payload), 0x7d)
        assertEquals(16, requireNotNull(iv).size)
        return HuaweiEncryptedPayload.open(HuaweiEncryptedPayload.Mode.GCM, key, frame.payload)
    }

    @Test fun requiresBothEncryptedReadsBeforeReadback() {
        HuaweiSessionReads(key, 512).use { reads ->
            assertArrayEquals(byteArrayOf(7, 0, 10, 0), requestPayload(reads, 7))
            assertNull(reads.readback())
            reads.receive(encrypted(7, product, 1))
            assertNull(reads.readback())
            assertArrayEquals(byteArrayOf(1, 0), requestPayload(reads, 8))
            reads.receive(encrypted(8, byteArrayOf(1, 1, 76), 2))
            val result = requireNotNull(reads.readback())
            assertEquals("GT 5", result.product.model)
            assertEquals("1.0", result.product.firmware)
            assertEquals(76, result.batteryPercent)
        }
    }

    @Test fun batteryVariantsAndConnectStatusRequireAuthenticatedReplies() {
        HuaweiSessionReads(key, 512).use { reads ->
            reads.request(7)
            reads.receive(encrypted(7, product, 1))
            reads.request(8)
            reads.receive(encrypted(8, byteArrayOf(1, 2, 76, 0, 2, 1, 75,
                0x7f, 4, 0, 1, 0x86.toByte(), 0xa0.toByte()), 2))
            assertEquals(75, requireNotNull(reads.readback()).batteryPercent)
            assertArrayEquals(byteArrayOf(0x81.toByte(), 6, 2, 1, 1, 3, 1, 0x35),
                requestPayload(reads, 3))
            reads.receive(encrypted(3, byteArrayOf(0x81.toByte(), 6, 2, 1, 1, 4, 1, 1), 3))
            assertEquals(true, reads.connectStatusSupported)
            assertArrayEquals(byteArrayOf(1, 1, 1), requestPayload(reads, 0x35))
            reads.receive(encrypted(0x35, byteArrayOf(0x7f, 4, 0, 1, 0x86.toByte(), 0xa0.toByte()), 4))
            assertTrue(reads.connectStatusAcknowledged)
        }
    }

    @Test fun observedGt5PlainBatteryAndInitializationAfterEncryptedProduct() {
        HuaweiSessionReads(key, 512).use { reads ->
            reads.request(7)
            reads.receive(encrypted(7, product, 1))
            reads.request(5)
            reads.receive(HuaweiLinkFrames.Message(1, 5, byteArrayOf(1, 4, 0, 0, 0, 1)))
            reads.request(8)
            reads.receive(HuaweiLinkFrames.Message(1, 8, byteArrayOf(1, 1, 73)))
            assertEquals(73, requireNotNull(reads.readback()).batteryPercent)
            assertFalse(requireNotNull(reads.readback()).batteryEncrypted)
            reads.request(2)
            reads.receive(HuaweiLinkFrames.Message(1, 2, byteArrayOf(2, 3, 1, 1, 0)))
            reads.request(3)
            reads.receive(HuaweiLinkFrames.Message(1, 3,
                byteArrayOf(0x81.toByte(), 6, 2, 1, 1, 4, 1, 1)))
            assertEquals(true, reads.connectStatusSupported)
            reads.request(0x35)
            reads.receive(HuaweiLinkFrames.Message(1, 0x35,
                byteArrayOf(0x7f, 4, 0, 1, 0x86.toByte(), 0xa0.toByte())))
            val result = requireNotNull(reads.readback())
            assertTrue(result.connectStatusAcknowledged)
            assertFalse(result.connectStatusEncrypted)
        }
    }

    @Test fun plaintextBatteryDoesNotPermitInvalidCiphertextOrMalformedReply() {
        val invalid = listOf(
            HuaweiLinkFrames.Message(1, 8, byteArrayOf(1, 1, 101)),
            HuaweiLinkFrames.Message(1, 8, byteArrayOf(1, 1, 73, 0x7c, 1, 1)),
            HuaweiLinkFrames.Message(1, 8, byteArrayOf(1, 1, 73, 1, 1, 74)),
            encrypted(8, byteArrayOf(1, 1, 73), 2, ByteArray(32)),
        )
        for (reply in invalid) {
            HuaweiSessionReads(key, 512).use { reads ->
                reads.request(7)
                reads.receive(encrypted(7, product, 1))
                reads.request(8)
                assertTrue(runCatching { reads.receive(reply) }.isFailure)
                assertTrue(runCatching { reads.readback() }.isFailure)
            }
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

    @Test fun acceptsTrailingProductPaddingAndRejectsInteriorNul() {
        val paddedProduct = byteArrayOf(7, 4) + "1.0\u0000".toByteArray() +
            byteArrayOf(10, 7) + "GT 5 \u0000\u0000".toByteArray()
        assertEquals(HuaweiDeviceInfo.Product("GT 5", "1.0"), HuaweiDeviceInfo.product(
            HuaweiLinkFrames.Message(1, 7, paddedProduct)))
        val malformedProduct = byteArrayOf(7, 3) + "1.0".toByteArray() +
            byteArrayOf(10, 4) + "GT\u00005".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiDeviceInfo.product(HuaweiLinkFrames.Message(1, 7, malformedProduct))
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
        assertArrayEquals(byteArrayOf(1, 3, 1, 7, 0x24), HuaweiDeviceInfo.query(2))
        assertEquals(setOf(1, 0x24), HuaweiDeviceInfo.services(
            HuaweiLinkFrames.Message(1, 2, byteArrayOf(2, 3, 1, 0, 1))))
        assertEquals(setOf(7), HuaweiDeviceInfo.services(
            HuaweiLinkFrames.Message(1, 2, byteArrayOf(2, 2, 0, 1))))
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiDeviceInfo.services(HuaweiLinkFrames.Message(1, 2, byteArrayOf(2, 4, 1, 0, 1, 1)))
        }
    }
}
