package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer

/** Read-only Huawei fitness wire formats. Missing fields remain null. No WHOOP conversion. */
internal object HuaweiFitnessRead {
    const val SERVICE = 7
    const val TOTALS = 3
    const val COUNT = 10
    const val PAGE = 11

    data class Totals(val steps: Int?, val distanceMeters: Long?)
    data class Minute(val epochSeconds: Long, val steps: Int?, val heartRate: Int?, val spo2: Int?)
    data class Page(val index: Int, val minutes: List<Minute>)

    fun totalsRequest() = byteArrayOf(1, 0)
    fun countRequest(start: Long, end: Long): ByteArray {
        require(start in 0..0xffffffffL && end in 0..0xffffffffL && start < end)
        fun seconds(n: Long) = ByteBuffer.allocate(4).putInt(n.toInt()).array()
        return HuaweiTlv.encode(HuaweiTlv.Field(0x81, byteArrayOf()),
            HuaweiTlv.Field(3, seconds(start)), HuaweiTlv.Field(4, seconds(end)))
    }
    fun pageRequest(index: Int): ByteArray {
        require(index in 0..32767)
        return HuaweiTlv.encode(HuaweiTlv.Field(0x81, HuaweiTlv.encode(
            HuaweiTlv.Field(2, byteArrayOf((index shr 8).toByte(), index.toByte())))))
    }

    private fun container(payload: ByteArray): List<HuaweiTlv.Field> {
        val outer = HuaweiTlv.decode(payload)
        val status = HuaweiTlv.one(outer, 0x7f)
        require(status == null || status.contentEquals(byteArrayOf(0, 1, 0x86.toByte(), 0xa0.toByte())))
        return HuaweiTlv.decode(requireNotNull(HuaweiTlv.one(outer, 0x81)))
    }
    private fun uint(data: ByteArray, size: Int): Long {
        require(data.size == size)
        return data.fold(0L) { n, b -> (n shl 8) or (b.toLong() and 255) }
    }
    fun totals(payload: ByteArray): Totals {
        var steps: Long? = null
        var distance: Long? = null
        for (row in container(payload).filter { it.tag == 0x83 }) {
            val fields = HuaweiTlv.decode(row.value)
            HuaweiTlv.one(fields, 5)?.let { steps = (steps ?: 0) + uint(it, 4) }
            HuaweiTlv.one(fields, 7)?.let { distance = (distance ?: 0) + uint(it, 4) }
        }
        steps?.let { require(it in 0L..200_000L) }
        distance?.let { require(it in 0L..1_000_000L) }
        require(steps != null || distance != null) { "Fitness totals absent" }
        return Totals(steps?.toInt(), distance)
    }
    fun count(payload: ByteArray): Int =
        uint(requireNotNull(HuaweiTlv.one(container(payload), 2)), 2).toInt().also { require(it <= 32767) }

    fun page(payload: ByteArray): Page {
        val fields = container(payload)
        val index = uint(requireNotNull(HuaweiTlv.one(fields, 2)), 2).toInt()
        val base = uint(requireNotNull(HuaweiTlv.one(fields, 3)), 4)
        val records = fields.filter { it.tag == 0x84 }.map { row ->
            val sample = HuaweiTlv.decode(row.value)
            val offset = uint(requireNotNull(HuaweiTlv.one(sample, 5)), 1)
            // The reference stores a signed minute offset; reject unknown negative-offset encoding.
            require(offset <= 127)
            val bytes = requireNotNull(HuaweiTlv.one(sample, 6))
            minute(base + 60 * offset, bytes)
        }
        require(records.map { it.epochSeconds }.distinct().size == records.size)
        return Page(index, records)
    }

    fun minute(timestamp: Long, bytes: ByteArray): Minute {
        var pos = 0
        fun octet(): Int {
            require(pos < bytes.size) { "Truncated fitness bitmap" }
            return bytes[pos++].toInt() and 255
        }
        val first = octet()
        val second = if ((first and 128) != 0) octet() else 0
        var steps: Int? = null
        var hr: Int? = null
        var spo2: Int? = null
        for (i in 0..6) {
            val bit = 1 shl i
            if ((first and bit) == 0) continue
            val value = if (bit in setOf(0x20, 0x40)) octet() else (octet() shl 8) or octet()
            when (bit) {
                2 -> steps = value.takeIf { it in 0..500 }
                0x40 -> hr = value.takeIf { it in 25..250 }
            }
        }
        for (i in 0..7) {
            if ((second and (1 shl i)) == 0) continue
            val value = octet()
            if (i == 0) spo2 = value.takeIf { it in 50..100 }
        }
        require(pos == bytes.size) { "Unknown fitness payload extension" }
        return Minute(timestamp, steps, hr, spo2)
    }
}
