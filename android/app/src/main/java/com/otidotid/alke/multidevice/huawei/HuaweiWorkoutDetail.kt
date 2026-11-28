package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer

/** Huawei 17/0A workout sample and 17/0C pace wire formats.
 * Unconfirmed units remain native values, and an unknown bitmap is rejected.
 */
internal object HuaweiWorkoutDetail {
    const val DATA = 0x0a
    const val PACE = 0x0c
    data class Sample(val timestamp: Long, val heartRate: Int?, val speedRaw: Int?,
        val stepRateRaw: Int?, val altitudeRaw: Int?, val cadenceRaw: Int?)
    data class DataPage(val number: Int, val page: Int, val intervalSeconds: Int, val samples: List<Sample>)
    data class Split(val distanceRaw: Int, val nativeType: Int, val paceRaw: Long,
        val pointIndex: Int?, val correctionRaw: Int?)
    data class PacePage(val number: Int, val page: Int, val splits: List<Split>)
    private val widths = intArrayOf(1,2,1,2,2,4,0,2,2,2)
    private val innerWidths = intArrayOf(2,2,2,1,2,1,1,1,1,2,2,1,2,2,2,2)

    fun request(number: Int, page: Int, pace: Boolean): ByteArray {
        fun short(v: Int): ByteArray {
            require(v in 0..32767)
            return byteArrayOf((v shr 8).toByte(), v.toByte())
        }
        return HuaweiTlv.encode(HuaweiTlv.Field(0x81, HuaweiTlv.encode(
            HuaweiTlv.Field(2, short(number)), HuaweiTlv.Field(if (pace) 8 else 3, short(page)))))
    }
    private fun fields(payload: ByteArray): List<HuaweiTlv.Field> {
        val f = HuaweiTlv.decode(payload)
        val status = HuaweiTlv.one(f, 0x7f)
        require(status == null || status.contentEquals(byteArrayOf(0,1,0x86.toByte(),0xa0.toByte())))
        return HuaweiTlv.decode(requireNotNull(HuaweiTlv.one(f, 0x81)))
    }
    private fun uint(bytes: ByteArray, size: Int): Long {
        require(bytes.size == size)
        return bytes.fold(0L) { n,b -> (n shl 8) or (b.toLong() and 255L) }
    }
    private fun get(f: List<HuaweiTlv.Field>, tag: Int, size: Int): Long =
        uint(requireNotNull(HuaweiTlv.one(f,tag)),size)
    private fun optional(f: List<HuaweiTlv.Field>, tag: Int, size: Int): Long? =
        HuaweiTlv.one(f,tag)?.let { uint(it,size) }
    fun data(payload: ByteArray): DataPage {
        val f = fields(payload)
        val number = get(f,2,2).toInt()
        val page = get(f,3,2).toInt()
        val header = requireNotNull(HuaweiTlv.one(f,4))
        val body = requireNotNull(HuaweiTlv.one(f,5))
        require(header.size == 14)
        val h = ByteBuffer.wrap(header)
        fun short(b: ByteBuffer) = b.short.toInt() and 65535
        fun byte(b: ByteBuffer) = b.get().toInt() and 255
        require(short(h) == number && short(h) == page)
        val start = h.int.toLong() and 0xffffffffL
        val interval = byte(h)
        val count = short(h)
        val length = byte(h)
        val bitmap = short(h)
        require(interval in 1..127 && count <= 8192 && length > 0 && bitmap and 0xfc00 == 0)
        val inner = optional(f,9,2)?.toInt() ?: 0x1ff
        val innerSize = innerWidths.indices.sumOf { if (inner and (1 shl it) != 0) innerWidths[it] else 0 }
        val expected = widths.indices.sumOf { if (bitmap and (1 shl it) != 0) {
            if (it == 6) innerSize else widths[it]
        } else 0 }
        require(expected == length && count * length == body.size)
        val b = ByteBuffer.wrap(body)
        val samples = List(count) { index ->
            var hr: Int? = null
            var speed: Int? = null
            var rate: Int? = null
            var altitude: Int? = null
            var cadence: Int? = null
            for (bit in widths.indices) {
                if (bitmap and (1 shl bit) == 0) continue
                when (bit) {
                    0 -> hr = byte(b).takeIf { it in 25..250 }
                    1 -> speed = short(b).takeIf { it != 65535 }
                    2 -> rate = byte(b).takeIf { it != 255 }
                    5 -> altitude = b.int.takeIf { it != -1 }
                    6 -> for (innerBit in innerWidths.indices) {
                        if (inner and (1 shl innerBit) == 0) continue
                        if (innerBit == 0) cadence = short(b).takeIf { it != 65535 }
                        else repeat(innerWidths[innerBit]) { b.get() }
                    }
                    else -> repeat(widths[bit]) { b.get() }
                }
            }
            Sample(start + index.toLong() * interval,hr,speed,rate,altitude,cadence)
        }
        require(!b.hasRemaining())
        return DataPage(number,page,interval,samples)
    }
    fun pace(payload: ByteArray): PacePage {
        val f = fields(payload)
        return PacePage(get(f,2,2).toInt(),get(f,8,2).toInt(),f.filter { it.tag == 0x83 }.map {
            val r = HuaweiTlv.decode(it.value)
            Split(get(r,4,2).toInt(),get(r,5,1).toInt(),get(r,6,4),
                optional(r,7,2)?.toInt(),optional(r,9,2)?.toInt())
        })
    }
}
