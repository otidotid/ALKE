package com.otidotid.alke.multidevice.huawei

import org.junit.Assert.*
import org.junit.Test

class HuaweiWorkoutDetailTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun data(header: ByteArray, body: ByteArray) = HuaweiTlv.encode(
        HuaweiTlv.Field(0x81, HuaweiTlv.encode(HuaweiTlv.Field(2,b(0,4)),
            HuaweiTlv.Field(3,b(0,0)), HuaweiTlv.Field(4,header), HuaweiTlv.Field(5,body))))

    @Test fun validatesHeaderAndReadsNativeSamples() {
        val header = b(0,4,0,0,0,0,0,60,5,0,2,3,0,3)
        val page = HuaweiWorkoutDetail.data(data(header,b(72,0,120,80,0,130)))
        assertEquals(4,page.number)
        assertEquals(5,page.intervalSeconds)
        assertEquals(listOf(60L,65L),page.samples.map { it.timestamp })
        assertEquals(listOf(72,80),page.samples.map { it.heartRate })
        assertEquals(listOf(120,130),page.samples.map { it.speedRaw })
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiWorkoutDetail.data(data(header,b(72,0,120)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HuaweiWorkoutDetail.data(data(header.copyOf().also { it[1]=5 },b(72,0,120,80,0,130)))
        }
    }
    @Test fun preservesRawPaceAndCorrectCommandTags() {
        assertArrayEquals(b(0x81,8,2,2,0,4,3,2,0,2),HuaweiWorkoutDetail.request(4,2,false))
        assertArrayEquals(b(0x81,8,2,2,0,4,8,2,0,2),HuaweiWorkoutDetail.request(4,2,true))
        val split = b(4,2,3,232,5,1,1,6,4,0,0,1,44)
        val payload = HuaweiTlv.encode(HuaweiTlv.Field(0x81,HuaweiTlv.encode(
            HuaweiTlv.Field(2,b(0,4)),HuaweiTlv.Field(8,b(0,2)),HuaweiTlv.Field(0x83,split))))
        val page = HuaweiWorkoutDetail.pace(payload)
        assertEquals(2,page.page)
        assertEquals(listOf(HuaweiWorkoutDetail.Split(1000,1,300,null,null)),page.splits)
    }
}
