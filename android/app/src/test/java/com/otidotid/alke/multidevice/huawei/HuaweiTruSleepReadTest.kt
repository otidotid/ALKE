package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class HuaweiTruSleepReadTest {
    private fun bytes(vararg n: Int) = ByteArray(n.size) { n[it].toByte() }
    private fun states() = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(1000).putInt(1060).putLong(42).array()
    private fun acc() = bytes(1,7,232,3,0,0,7,8,255)
    private fun ppg(body: ByteArray) = bytes(2,body.size,0) + body

    @Test fun rawStateAndSensorPacketsPreserveUnitsAndAxes() {
        val data = acc() + bytes(0,0) +
            ppg(bytes(232,3,0,0,2,0xaa,10,0,20,0,0xaa,100,0,156,255))
        val result = HuaweiTruSleepRead.decode(states(),data,1000,1100)
        assertEquals(1000L,result.states.single().start)
        assertEquals(1060L,result.states.single().end)
        assertEquals(HuaweiTruSleepRead.Acc(1000,7,8,255),result.acc.single())
        assertEquals(listOf(HuaweiTruSleepRead.Ppg(1000100,100),
            HuaweiTruSleepRead.Ppg(1000200,-100)),result.ppg)
    }

    @Test fun compressedPeaksAndOffsetAmplitudes() {
        val body = bytes(232,3,0,0,3,0xbb,10,0,10,255,40,0,
            0xbb,100,0,5,254,255,20,0)
        val result = HuaweiTruSleepRead.decode(states(),ppg(body),1000,1100)
        assertEquals(listOf(1000100L,1000200L,1000400L),result.ppg.map { it.timestampMillis })
        assertEquals(listOf(105,98,120),result.ppg.map { it.amplitudeRaw })
    }

    @Test fun duplicateEqualSamplesAreIdempotentButConflictFails() {
        assertEquals(1,HuaweiTruSleepRead.decode(states()+states(),acc()+acc(),1000,1100).acc.size)
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiTruSleepRead.decode(states(),acc()+acc().apply { this[lastIndex]=1 },1000,1100)
        }
    }

    @Test fun unknownTruncatedTrailingAndOutOfWindowRecordsFailClosed() {
        val malformed = listOf(bytes(3),acc().copyOf(8),
            ppg(bytes(232,3,0,0,1,0xaa,10,0,0xaa,1,0,9)),
            ppg(bytes(232,3,0,0,2,0xaa,20,0,10,0,0xaa,1,0,2,0)))
        for (file in malformed) assertThrows(IllegalArgumentException::class.java) {
            HuaweiTruSleepRead.decode(states(),file,1000,1100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiTruSleepRead.decode(states().copyOf(15),acc(),1000,1100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiTruSleepRead.decode(states(),acc(),1001,1100)
        }
    }
}
