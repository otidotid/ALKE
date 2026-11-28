package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test

class HuaweiFileReadTest {
    private fun bytes(vararg data: Int) = ByteArray(data.size) { data[it].toByte() }

    @Test fun gpsInitAndBlockRequestHaveExpectedWireFields() {
        assertArrayEquals(bytes(1,9,52,95,103,112,115,46,98,105,110,2,1,17,
            5,4,0,0,0,0,6,4,0,0,0,0),
            HuaweiFileRead.initRequest(HuaweiFileRead.Kind.GPS,0,0,4))
        assertArrayEquals(bytes(1,1,7,2,4,0,0,0,2,3,4,0,0,0,3),
            HuaweiFileRead.blockRequest(7,2,3))
    }

    @Test fun manifestRequiresExactNameTypeAndBoundedSize() {
        val valid = bytes(1,9,52,95,103,112,115,46,98,105,110,2,1,17,3,1,7,4,4,0,0,0,5)
        assertEquals(HuaweiFileRead.Manifest(7,5),HuaweiFileRead.manifest(valid,HuaweiFileRead.Kind.GPS,4))
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiFileRead.manifest(valid,HuaweiFileRead.Kind.GPS,5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiFileRead.manifest(valid.copyOf().apply { this[size-4] = 127 },HuaweiFileRead.Kind.GPS,4)
        }
    }

    @Test fun exactAssemblyAndIncompleteFileRejection() {
        HuaweiFileRead.Assembly(HuaweiFileRead.Manifest(7,5)).use { a ->
            a.append(HuaweiFileRead.block(bytes(7,0,0,0,0,0,1,2)))
            assertEquals(2,a.nextOffset)
            assertThrows(IllegalStateException::class.java) { a.finish() }
            a.append(HuaweiFileRead.block(bytes(7,0,0,0,2,0,3,4,5)))
            assertArrayEquals(bytes(1,2,3,4,5),a.finish())
        }
    }

    @Test fun wrongIdentityGapDuplicateOrOverflowAbortsAssembly() {
        for (b in listOf(
            HuaweiFileRead.Block(8,0,bytes(1)),
            HuaweiFileRead.Block(7,2,bytes(1)),
            HuaweiFileRead.Block(7,0,bytes(1,2,3)),
            HuaweiFileRead.Block(7,0,byteArrayOf())
        )) {
            HuaweiFileRead.Assembly(HuaweiFileRead.Manifest(7,2)).use { a ->
                assertThrows(IllegalArgumentException::class.java) { a.append(b) }
                assertThrows(IllegalStateException::class.java) { a.finish() }
            }
        }
        HuaweiFileRead.Assembly(HuaweiFileRead.Manifest(7,2)).use { a ->
            a.append(HuaweiFileRead.Block(7,0,bytes(1)))
            assertThrows(IllegalArgumentException::class.java) { a.append(HuaweiFileRead.Block(7,0,bytes(1))) }
        }
    }

    @Test fun parametersRejectCleartextAndInvalidBounds() {
        val p = bytes(1,1,7,2,1,5,3,2,0,64,4,4,0,0,1,0,5,1,0)
        assertEquals(HuaweiFileRead.Parameters(7,64,256),HuaweiFileRead.parameters(p,7))
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiFileRead.parameters(p.copyOf().apply { this[lastIndex] = 1 },7)
        }
        assertThrows(IllegalArgumentException::class.java) { HuaweiFileRead.parameters(p,8) }
    }
}
