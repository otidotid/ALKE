package com.otidotid.alke.multidevice.huawei

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos

/**
 * Experimental decoder for the documented little-endian Huawei GPS delta layout.
 * Candidate only: trailer conventions and coordinate reconstruction must be
 * compared with a known real GT5 recording before exposing a route or distance.
 * No interpolated points or inferred altitude.
 */
internal object HuaweiGpsRead {
    private const val MAX_POINTS = 100_000
    private const val RADIUS_METERS = 6_383_807.0
    data class Point(val timestamp: Long, val latitude: Double, val longitude: Double, val altitudeRaw: Int?)

    fun decode(file: ByteArray, workoutStart: Long, workoutEnd: Long): List<Point> {
        require(workoutStart >= 0 && workoutEnd > workoutStart && workoutEnd-workoutStart <= 7*86400L)
        if (file.isEmpty()) return emptyList()
        require(file.size in 62..HuaweiFileRead.MAX_FILE_BYTES)
        val buf = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN)
        val hasAltitude = (file[32].toInt() and 3) == 3
        val header = if (hasAltitude) 70 else 62
        val stride = if (hasAltitude) 19 else 15
        require(file.size >= header && (file.size-header)%stride == 0) {
            "Unknown or truncated Huawei GPS layout"
        }
        val count = (file.size-header)/stride
        require(count <= MAX_POINTS)
        buf.position(33)
        var time = buf.int.toLong() and 0xffffffffL
        val originLon = buf.double
        val originLat = buf.double
        require(originLat.isFinite() && originLat in -90.0..90.0 &&
            originLon.isFinite() && originLon in -180.0..180.0)
        require(time in workoutStart..workoutEnd)
        val latitudeScale = cos(Math.toRadians(originLat))
        require(abs(latitudeScale) > 1e-6)
        var east = 0.0
        var north = 0.0
        buf.position(header)
        return buildList(count) {
            repeat(count) {
                val delta = buf.short.toInt()
                require(delta > 0)
                time += delta.toLong()
                require(time in workoutStart..workoutEnd)
                buf.short // reserved
                val dx = buf.float.toDouble()
                val dy = buf.float.toDouble()
                require(dx.isFinite() && dy.isFinite())
                east += dx
                north += dy
                buf.position(buf.position()+3)
                val altitude = if (hasAltitude) {
                    val raw = buf.short.toInt()
                    buf.short // reserved
                    raw
                } else null
                val lat = originLat + Math.toDegrees(north / RADIUS_METERS)
                val lon = originLon + Math.toDegrees(east / (RADIUS_METERS * latitudeScale))
                require(lat.isFinite() && lat in -90.0..90.0 &&
                    lon.isFinite() && lon in -180.0..180.0)
                add(Point(time,lat,lon,altitude))
            }
        }
    }
}
