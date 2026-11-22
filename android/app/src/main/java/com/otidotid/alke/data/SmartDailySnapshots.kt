package com.otidotid.alke.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * PR #10: a compact, lossless *presentation-only* cache of the already resolved
 * DailyMetric rows. It is never a scoring input, a retention authority, or a
 * substitute for the full Room history or verified Drive archive.
 *
 * Snapshot rows are collected from WhoopRepository.daysMerged, meaning imported
 * precedence, activity-file steps and user-edited sleep have ALREADY been
 * resolved by the canonical repository, not reimplemented here.
 *
 * Every nullable scalar is persisted with its type (no rounded JSON number),
 * including null vs non-null and all Double bits. GZIP has its own CRC. A
 * truncated/corrupted/stale file is ignored and the normal database read wins.
 */
internal object SmartDailySnapshotCodec {
    private const val MAGIC = 0x414C4B37   // ALK7, deliberately versioned
    private const val VERSION = 1
    private const val MAX_ROWS = 20_000

    data class Snapshot(
        val activeSource: String,
        val createdAtMs: Long,
        val rows: List<DailyMetric>,
    )

    fun encode(destination: OutputStream, snapshot: Snapshot) {
        require(snapshot.activeSource.isNotBlank() && snapshot.activeSource.length <= 256)
        require(snapshot.rows.size <= MAX_ROWS) { "History too large for the optional snapshot" }
        DataOutputStream(GZIPOutputStream(destination)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(snapshot.activeSource)
            out.writeLong(snapshot.createdAtMs)
            out.writeInt(snapshot.rows.size)
            snapshot.rows.forEach { row ->
                out.writeUTF(row.deviceId)
                out.writeUTF(row.day)
            writeDoubleOrNull(out, row.totalSleepMin)
            writeDoubleOrNull(out, row.efficiency)
            writeDoubleOrNull(out, row.deepMin)
            writeDoubleOrNull(out, row.remMin)
            writeDoubleOrNull(out, row.lightMin)
            writeIntOrNull(out, row.disturbances)
            writeIntOrNull(out, row.restingHr)
            writeDoubleOrNull(out, row.avgHrv)
            writeDoubleOrNull(out, row.recovery)
            writeDoubleOrNull(out, row.strain)
            writeIntOrNull(out, row.exerciseCount)
            writeDoubleOrNull(out, row.spo2Pct)
            writeDoubleOrNull(out, row.skinTempDevC)
            writeDoubleOrNull(out, row.respRateBpm)
            writeIntOrNull(out, row.steps)
            writeDoubleOrNull(out, row.activeKcalEst)
            writeIntOrNull(out, row.spo2Red)
            writeIntOrNull(out, row.spo2Ir)
            writeDoubleOrNull(out, row.avgSdnn)
            writeDoubleOrNull(out, row.skinTempC)
            writeBooleanOrNull(out, row.sleepHrOnly)
            }
        }
    }

    fun decode(source: InputStream): Snapshot =
        DataInputStream(GZIPInputStream(source)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "Snapshot schema mismatch" }
            val activeSource = input.readUTF()
            require(activeSource.isNotBlank() && activeSource.length <= 256)
            val createdAtMs = input.readLong()
            val count = input.readInt()
            require(count in 0..MAX_ROWS) { "Snapshot row count outside supported bounds" }
            val rows = ArrayList<DailyMetric>(count)
            repeat(count) {
                val row = DailyMetric(
                    deviceId = input.readUTF(),
                    day = input.readUTF(),
            totalSleepMin = readDoubleOrNull(input),
            efficiency = readDoubleOrNull(input),
            deepMin = readDoubleOrNull(input),
            remMin = readDoubleOrNull(input),
            lightMin = readDoubleOrNull(input),
            disturbances = readIntOrNull(input),
            restingHr = readIntOrNull(input),
            avgHrv = readDoubleOrNull(input),
            recovery = readDoubleOrNull(input),
            strain = readDoubleOrNull(input),
            exerciseCount = readIntOrNull(input),
            spo2Pct = readDoubleOrNull(input),
            skinTempDevC = readDoubleOrNull(input),
            respRateBpm = readDoubleOrNull(input),
            steps = readIntOrNull(input),
            activeKcalEst = readDoubleOrNull(input),
            spo2Red = readIntOrNull(input),
            spo2Ir = readIntOrNull(input),
            avgSdnn = readDoubleOrNull(input),
            skinTempC = readDoubleOrNull(input),
            sleepHrOnly = readBooleanOrNull(input)
                )
                rows.add(row)
            }
            // Force GZIP checksum/truncation verification before accepting the cache.
            require(input.read() == -1) { "Unexpected bytes after cached daily rows" }
            Snapshot(activeSource, createdAtMs, rows)
        }

    private fun writeDoubleOrNull(out: DataOutputStream, value: Double?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeLong(value.toRawBits())
    }
    private fun readDoubleOrNull(input: DataInputStream): Double? =
        if (input.readBoolean()) Double.fromBits(input.readLong()) else null

    private fun writeIntOrNull(out: DataOutputStream, value: Int?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeInt(value)
    }
    private fun readIntOrNull(input: DataInputStream): Int? =
        if (input.readBoolean()) input.readInt() else null

    private fun writeBooleanOrNull(out: DataOutputStream, value: Boolean?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeBoolean(value)
    }
    private fun readBooleanOrNull(input: DataInputStream): Boolean? =
        if (input.readBoolean()) input.readBoolean() else null
}

/**
 * One small, disposable file per app, source-tagged to prevent multi-strap
 * cross-contamination. Reading never blocks a DB writer or changes a score.
 * All calls here are blocking file operations: call on Dispatchers.IO.
 */
internal class SmartDailySnapshotStore(directory: File) {
    private val output = File(directory, "smart-daily-history-v1.bin")

    /** Twelve-hour cache-first window; the authoritative DB is always re-read. */
    fun read(activeSource: String, nowMs: Long = System.currentTimeMillis()): List<DailyMetric>? =
        synchronized(fileLock) {
            if (!output.isFile || output.length() > MAX_FILE_BYTES) return@synchronized null
            runCatching {
                val snapshot = output.inputStream().buffered().use(SmartDailySnapshotCodec::decode)
                require(snapshot.activeSource == activeSource)
                require(snapshot.createdAtMs in (nowMs - MAX_AGE_MS)..(nowMs + CLOCK_SKEW_MS))
                snapshot.rows
            }.getOrNull()
        }

    /** Replaces only the optional snapshot, never a source database or Drive archive. */
    fun publish(
        activeSource: String,
        rows: List<DailyMetric>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(fileLock) {
        val temp = File(output.parentFile, "${output.name}.tmp")
        try {
            temp.parentFile?.mkdirs()
            temp.outputStream().buffered().use { stream ->
                SmartDailySnapshotCodec.encode(stream, SmartDailySnapshotCodec.Snapshot(activeSource, nowMs, rows))
            }
            check(temp.length() <= MAX_FILE_BYTES) { "Snapshot exceeded its safe size" }
            try {
                Files.move(temp.toPath(), output.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) {
            // Optional cache only: never fail an app screen or daily backup because of it.
            false
        } finally {
            temp.delete()
        }
    }

    companion object {
        private val fileLock = Any()
        private const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        private const val MAX_AGE_MS = 12L * 60L * 60L * 1000L
        private const val CLOCK_SKEW_MS = 60L * 1000L
    }
}
