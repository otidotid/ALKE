package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Experimental native TruSleep file decoder. These records are not sleep
 * stages, heart rate, RR intervals, HRV, or a clinical breathing assessment.
 * Keep the raw observations isolated pending physical GT5 validation.
 */
internal object HuaweiTruSleepRead {
    private const val MAX_RECORDS = 250_000
    data class State(val start: Long, val end: Long, val metadata: ByteArray)
    data class Acc(val timestamp: Long, val x: Int, val y: Int, val z: Int)
    data class Ppg(val timestampMillis: Long, val amplitudeRaw: Int)
    data class Decoded(val states: List<State>, val acc: List<Acc>, val ppg: List<Ppg>)
    data class Counts(val states: Int, val acc: Int, val ppg: Int)

    private fun ByteBuffer.u8(): Int {
        require(remaining() >= 1)
        return get().toInt() and 255
    }
    private fun ByteBuffer.i16(): Int {
        require(remaining() >= 2)
        return short.toInt()
    }
    private fun ByteBuffer.u32(): Long {
        require(remaining() >= 4)
        return int.toLong() and 0xffffffffL
    }
    private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    fun decode(stateFile: ByteArray, dataFile: ByteArray, start: Long, end: Long): Decoded {
        require(start >= 0 && end > start && end <= 0xffffffffL && end-start <= 7*86400L)
        require(stateFile.size <= HuaweiFileRead.MAX_FILE_BYTES && stateFile.size % 16 == 0)
        require(dataFile.size <= HuaweiFileRead.MAX_FILE_BYTES && stateFile.size / 16 <= MAX_RECORDS)
        val states = linkedMapOf<Pair<Long,Long>,State>()
        val sb = buffer(stateFile)
        while (sb.hasRemaining()) {
            val from = sb.u32()
            val to = sb.u32()
            val metadata = ByteArray(8).also { sb.get(it) }
            if (from == 0L && to == 0L && metadata.all { it == 0.toByte() }) continue
            require(from >= start && to <= end && to > from)
            val key = from to to
            val old = states[key]
            require(old == null || old.metadata.contentEquals(metadata))
            states[key] = State(from,to,metadata)
        }

        val acc = sortedMapOf<Long,Acc>()
        val ppg = sortedMapOf<Long,Ppg>()
        val db = buffer(dataFile)
        var records = states.size
        while (db.hasRemaining()) {
            val tag = db.u8()
            if (tag == 0) continue // only known zero padding
            require(tag == 1 || tag == 2) { "Unknown TruSleep packet type" }
            val size = if (tag == 1) db.u8() else db.i16() and 0xffff
            require(size > 0 && size <= db.remaining())
            val packet = db.slice().order(ByteOrder.LITTLE_ENDIAN)
            packet.limit(size)
            db.position(db.position()+size)
            val base = packet.u32()
            require(base in start until end)
            if (tag == 1) {
                require(size == 7)
                val row = Acc(base,packet.u8(),packet.u8(),packet.u8())
                require(++records <= MAX_RECORDS)
                val old = acc.put(base,row)
                require(old == null || old == row)
            } else {
                val n = packet.u8()
                require(n in 1..127 && records+n <= MAX_RECORDS)
                records += n
                val peaks = samples(packet,n,true)
                val amplitudes = samples(packet,n,false)
                require(!packet.hasRemaining())
                var prior = -1
                for (i in 0 until n) {
                    require(peaks[i] >= 0 && peaks[i] > prior)
                    prior = peaks[i]
                    val timestamp = base*1000 + peaks[i]*10L
                    require(timestamp in start*1000 until end*1000)
                    val row = Ppg(timestamp,amplitudes[i])
                    val old = ppg.put(timestamp,row)
                    require(old == null || old == row)
                }
            }
        }
        return Decoded(states.values.sortedBy { it.start },acc.values.toList(),ppg.values.toList())
    }

    /** Documented signed 16-bit raw values and bounded delta/offset compression. */
    private fun samples(packet: ByteBuffer, n: Int, cumulative: Boolean): IntArray {
        val mode = packet.u8()
        require(mode == 0xaa || mode == 0xbb)
        if (mode == 0xaa) return IntArray(n) { packet.i16() }
        val first = packet.i16()
        var prior = first
        return IntArray(n) { index ->
            if (cumulative && index == 0) first else {
                val octet = packet.u8()
                val value = if (octet == 255) {
                    val literal = packet.i16()
                    if (cumulative) literal else first + literal
                } else {
                    val delta = octet.toByte().toInt()
                    (if (cumulative) prior else first) + delta
                }
                require(value in Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt())
                prior = value
                value
            }
        }
    }
}
