package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer

/**
 * Native legacy sleep (07/0C, 07/0D) and workout summary (17/07, 17/08).
 * Wire layouts verified against public protocol references. Sleep type and workout
 * type remain vendor codes, not ALKE stages/sport labels. No synthetic sensor data.
 */
internal object HuaweiHistoryRead {
    const val SLEEP_COUNT = 0x0c
    const val SLEEP_PAGE = 0x0d
    const val WORKOUT_SERVICE = 0x17
    const val WORKOUT_LIST = 7
    const val WORKOUT_TOTALS = 8

    data class SleepInterval(val start: Long, val end: Long, val nativeType: Int)
    data class SleepPage(val index: Int, val rows: List<SleepInterval>)
    data class WorkoutRef(val number: Int, val dataPages: Int, val pacePages: Int, val segmentPages: Int)
    data class WorkoutSummary(val number: Int, val start: Long, val end: Long,
        val nativeType: Int?, val status: Int?, val distanceMeters: Long?, val steps: Long?,
        val caloriesRaw: Long?, val elapsedSeconds: Long?, val activeSeconds: Long?)

    private fun fields(payload: ByteArray): List<HuaweiTlv.Field> {
        val outer = HuaweiTlv.decode(payload)
        val status = HuaweiTlv.one(outer, 0x7f)
        require(status == null || status.contentEquals(byteArrayOf(0, 1, 0x86.toByte(), 0xa0.toByte())))
        return HuaweiTlv.decode(requireNotNull(HuaweiTlv.one(outer, 0x81)))
    }
    private fun uint(bytes: ByteArray, size: Int): Long {
        require(bytes.size == size)
        return bytes.fold(0L) { n, b -> (n shl 8) or (b.toLong() and 255L) }
    }
    private fun value(fields: List<HuaweiTlv.Field>, tag: Int, size: Int): Long =
        uint(requireNotNull(HuaweiTlv.one(fields, tag)), size)
    private fun optional(fields: List<HuaweiTlv.Field>, tag: Int, size: Int): Long? =
        HuaweiTlv.one(fields, tag)?.let { uint(it, size) }
    private fun number(fields: List<HuaweiTlv.Field>, tag: Int): Int =
        value(fields, tag, 2).toInt().also { require(it <= 32767) }

    fun sleepPage(payload: ByteArray): SleepPage {
        val f = fields(payload)
        val index = number(f, 2)
        val rows = f.filter { it.tag == 0x83 }.map { container ->
            val row = HuaweiTlv.decode(container.value)
            val type = value(row, 4, 1).toInt()
            val stamp = requireNotNull(HuaweiTlv.one(row, 5))
            require(stamp.size == 6)
            val start = uint(stamp.copyOfRange(0, 4), 4)
            val duration = uint(stamp.copyOfRange(4, 6), 2) * 60
            require(duration in 60L..86400L)
            SleepInterval(start, start + duration, type)
        }
        return SleepPage(index, rows)
    }

    fun workoutListRequest(start: Long, end: Long): ByteArray {
        require(start in 0..0xffffffffL && end in 0..0xffffffffL && start < end)
        fun seconds(n: Long) = ByteBuffer.allocate(4).putInt(n.toInt()).array()
        return HuaweiTlv.encode(HuaweiTlv.Field(0x81, HuaweiTlv.encode(
            HuaweiTlv.Field(3, seconds(start)), HuaweiTlv.Field(4, seconds(end)))))
    }
    fun workoutTotalsRequest(number: Int) = HuaweiFitnessRead.pageRequest(number)
    fun workoutList(payload: ByteArray): List<WorkoutRef> {
        val f = fields(payload)
        val count = number(f, 2)
        val rows = f.filter { it.tag == 0x85 }.map {
            val row = HuaweiTlv.decode(it.value)
            WorkoutRef(number(row, 6), number(row, 7), number(row, 8),
                optional(row, 9, 2)?.toInt()?.also { n -> require(n <= 32767) } ?: 0)
        }
        require(count == rows.size && rows.map { it.number }.distinct().size == count)
        return rows.sortedBy { it.number }
    }
    fun workoutSummary(payload: ByteArray): WorkoutSummary {
        val f = fields(payload)
        val start = value(f, 4, 4)
        val end = value(f, 5, 4)
        require(end > start && end - start <= 7 * 86400L)
        fun nonSentinel(tag: Int): Long? = optional(f, tag, 4)?.takeIf { it != 0xffffffffL }
        return WorkoutSummary(number(f, 2), start, end,
            optional(f, 0x14, 1)?.toInt()?.takeIf { it != 255 },
            optional(f, 3, 1)?.toInt()?.takeIf { it != 255 },
            nonSentinel(7), nonSentinel(8), nonSentinel(6), nonSentinel(9), nonSentinel(0x12))
    }
}
