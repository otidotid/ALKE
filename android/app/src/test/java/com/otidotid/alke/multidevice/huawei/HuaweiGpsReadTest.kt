package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class HuaweiGpsReadTest {
    private fun fixture(altitude: Boolean = false, dx: Float = 100f, dt: Short = 10): ByteArray {
        val header = if (altitude) 70 else 62
        val stride = if (altitude) 19 else 15
        val b = ByteBuffer.allocate(header + stride*2).order(ByteOrder.LITTLE_ENDIAN)
        b.position(32)
        b.put(if (altitude) 3.toByte() else 0.toByte())
        b.putInt(1000).putDouble(106.0).putDouble(-6.0)
        b.position(header)
        repeat(2) {
            b.putShort(dt).putShort(0).putFloat(dx).putFloat(50f)
            b.put(byteArrayOf(0,0,0))
            if (altitude) b.putShort(42).putShort(0)
        }
        return b.array()
    }

    @Test fun decodesOrderedRelativeCoordinatesWithoutInventingOriginSample() {
        val result = HuaweiGpsRead.decode(fixture(),1000,1050)
        assertEquals(listOf(1010L,1020L),result.map { it.timestamp })
        assertEquals(-5.999551,result[0].latitude,0.00001)
        assertEquals(106.000902,result[0].longitude,0.00001)
        assertTrue(result[1].longitude > result[0].longitude)
        assertNull(result[0].altitudeRaw)
    }

    @Test fun altitudeRemainsRawAndOptional() {
        assertEquals(listOf(42,42),HuaweiGpsRead.decode(fixture(true),1000,1050).map { it.altitudeRaw })
    }

    @Test fun invalidTruncatedOrOutOfWindowFileDoesNotYieldPartialRoute() {
        val invalid = listOf(fixture().copyOf(63),fixture(dx=Float.NaN),
            fixture(dx=Float.MAX_VALUE),fixture(dt=0))
        for (file in invalid) assertThrows(IllegalArgumentException::class.java) {
            HuaweiGpsRead.decode(file,1000,1050)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiGpsRead.decode(fixture(),1000,1015)
        }
        assertTrue(HuaweiGpsRead.decode(byteArrayOf(),1000,1050).isEmpty())
    }
}
