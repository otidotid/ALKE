package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test

class HuaweiFitnessReadTest {
    private fun bytes(vararg n: Int) = ByteArray(n.size) { n[it].toByte() }
    @Test fun totalsPreserveMissingAndSumOnlyDistinctProtocolRows() {
        val body = bytes(0x81, 16, 0x83, 6, 5, 4, 0, 0, 0, 100,
            0x83, 6, 5, 4, 0, 0, 0, 23)
        assertEquals(HuaweiFitnessRead.Totals(123, null), HuaweiFitnessRead.totals(body))
    }
    @Test fun minuteBitmapHasIndependentUnitsAndMissingValues() {
        assertEquals(HuaweiFitnessRead.Minute(60, 42, 72, 98),
            HuaweiFitnessRead.minute(60, bytes(0xc2, 1, 0, 42, 72, 98)))
        assertEquals(HuaweiFitnessRead.Minute(60, null, 70, null),
            HuaweiFitnessRead.minute(60, bytes(0x40, 70)))
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiFitnessRead.minute(60, bytes(0xc2, 1, 0, 42, 72))
        }
    }
    @Test fun pageIndexAndTimestampAreReadFromWire() {
        val body = bytes(0x81, 20, 2, 2, 0, 2, 3, 4, 0, 0, 0, 60,
            0x84, 8, 5, 1, 1, 6, 3, 2, 0, 10)
        val page = HuaweiFitnessRead.page(body)
        assertEquals(2, page.index)
        assertEquals(listOf(HuaweiFitnessRead.Minute(120, 10, null, null)), page.minutes)
    }
    @Test fun countAndRequestsUseNetworkByteOrder() {
        assertEquals(2, HuaweiFitnessRead.count(bytes(0x81, 4, 2, 2, 0, 2)))
        assertArrayEquals(bytes(0x81, 4, 2, 2, 1, 2), HuaweiFitnessRead.pageRequest(258))
        assertArrayEquals(bytes(0x81, 0, 3, 4, 0, 0, 0, 1, 4, 4, 0, 0, 1, 0),
            HuaweiFitnessRead.countRequest(1, 256))
    }
}
