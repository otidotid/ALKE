package com.otidotid.alke.multidevice.huawei

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Private Huawei-native staging store. Deliberately separate from WHOOP's raw
 * counters, ADC samples and Room migrations. Only authenticated reads enter it.
 * One row per device and timestamp; repeated sync is an upsert, never addition.
 * Kept outside Android automatic backup pending explicit ALKE archive integration.
 */
internal class HuaweiFitnessStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    File(context.noBackupFilesDir, "alke-huawei-fitness-v1.db").absolutePath,
    null, 3,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE minute (device TEXT NOT NULL, ts INTEGER NOT NULL, steps INTEGER, hr INTEGER, spo2 INTEGER, PRIMARY KEY(device, ts))")
        db.execSQL("CREATE TABLE totals (device TEXT NOT NULL, day TEXT NOT NULL, observed INTEGER NOT NULL, steps INTEGER, distance INTEGER, PRIMARY KEY(device, day))")
        db.execSQL("CREATE INDEX minute_ts ON minute(ts)")
        createHistoryTables(db)
        createWorkoutDetailTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..2 && newVersion == 3) { "Explicit Huawei fitness migration required" }
        if (oldVersion == 1) createHistoryTables(db)
        createWorkoutDetailTables(db)
    }

    private fun createHistoryTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE legacy_sleep (device TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, native_type INTEGER NOT NULL, PRIMARY KEY(device, start))")
        db.execSQL("CREATE TABLE workout_summary (device TEXT NOT NULL, start INTEGER NOT NULL, number INTEGER NOT NULL, end INTEGER NOT NULL, native_type INTEGER, status INTEGER, distance INTEGER, steps INTEGER, calories_raw INTEGER, elapsed INTEGER, active INTEGER, PRIMARY KEY(device, start, number))")
    }

    private fun createWorkoutDetailTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE workout_sample (device TEXT NOT NULL, start INTEGER NOT NULL, number INTEGER NOT NULL, ts INTEGER NOT NULL, hr INTEGER, speed_raw INTEGER, step_rate_raw INTEGER, altitude_raw INTEGER, cadence_raw INTEGER, PRIMARY KEY(device,start,number,ts))")
        db.execSQL("CREATE TABLE workout_pace (device TEXT NOT NULL, start INTEGER NOT NULL, number INTEGER NOT NULL, ordinal INTEGER NOT NULL, distance_raw INTEGER NOT NULL, native_type INTEGER NOT NULL, pace_raw INTEGER NOT NULL, point_index INTEGER, correction_raw INTEGER, PRIMARY KEY(device,start,number,ordinal))")
    }

    fun saveWorkoutDetails(device: String, bundles: List<HuaweiFitnessSession.WorkoutBundle>) {
        require(device.matches(Regex("[0-9a-f]{64}")))
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (bundle in bundles) {
                val summary = bundle.summary
                for (sample in bundle.samples) {
                    require(sample.timestamp in summary.start..summary.end)
                    val values = ContentValues().apply {
                        put("device",device); put("start",summary.start); put("number",summary.number)
                        put("ts",sample.timestamp)
                        sample.heartRate?.let { put("hr",it) }
                        sample.speedRaw?.let { put("speed_raw",it) }
                        sample.stepRateRaw?.let { put("step_rate_raw",it) }
                        sample.altitudeRaw?.let { put("altitude_raw",it) }
                        sample.cadenceRaw?.let { put("cadence_raw",it) }
                    }
                    val updated = db.update("workout_sample", values, "device=? AND start=? AND number=? AND ts=?",
                        arrayOf(device,summary.start.toString(),summary.number.toString(),sample.timestamp.toString()))
                    if (updated == 0) check(db.insertOrThrow("workout_sample",null,values) != -1L)
                }
                // Nonempty lists are complete fetches, never a truncated prefix.
                if (bundle.splits.isNotEmpty()) {
                    db.delete("workout_pace","device=? AND start=? AND number=?",
                        arrayOf(device,summary.start.toString(),summary.number.toString()))
                    bundle.splits.forEachIndexed { i, split ->
                        val values = ContentValues().apply {
                            put("device",device); put("start",summary.start); put("number",summary.number)
                            put("ordinal",i); put("distance_raw",split.distanceRaw)
                            put("native_type",split.nativeType); put("pace_raw",split.paceRaw)
                            put("point_index",split.pointIndex); put("correction_raw",split.correctionRaw)
                        }
                        check(db.insertOrThrow("workout_pace",null,values) != -1L)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveSleep(device: String, rows: List<HuaweiHistoryRead.SleepInterval>) {
        require(device.matches(Regex("[0-9a-f]{64}")))
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (row in rows) {
                val values = ContentValues().apply {
                    put("device", device); put("start", row.start); put("end", row.end)
                    put("native_type", row.nativeType)
                }
                check(db.insertWithOnConflict("legacy_sleep", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveWorkouts(device: String, rows: List<HuaweiHistoryRead.WorkoutSummary>) {
        require(device.matches(Regex("[0-9a-f]{64}")))
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (row in rows) {
                val values = ContentValues().apply {
                    put("device", device); put("start", row.start); put("number", row.number)
                    put("end", row.end)
                    // Update present fields only; a sparse repeat must not erase prior metadata.
                    row.nativeType?.let { put("native_type", it) }
                    row.status?.let { put("status", it) }
                    row.distanceMeters?.let { put("distance", it) }
                    row.steps?.let { put("steps", it) }
                    row.caloriesRaw?.let { put("calories_raw", it) }
                    row.elapsedSeconds?.let { put("elapsed", it) }
                    row.activeSeconds?.let { put("active", it) }
                }
                val count = db.update("workout_summary", values, "device=? AND start=? AND number=?",
                    arrayOf(device, row.start.toString(), row.number.toString()))
                if (count == 0) check(db.insertOrThrow("workout_summary", null, values) != -1L)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveMinutes(device: String, start: Long, end: Long, records: List<HuaweiFitnessRead.Minute>) {
        require(device.matches(Regex("[0-9a-f]{64}")))
        require(start < end && end - start <= 86400)
        require(records.all { it.epochSeconds in start until end })
        require(records.map { it.epochSeconds }.distinct().size == records.size)
        val db = writableDatabase
        var changed = false
        db.beginTransaction()
        try {
            for (row in records) {
                val old = db.query("minute", arrayOf("steps", "hr", "spo2"), "device=? AND ts=?",
                    arrayOf(device, row.epochSeconds.toString()), null, null, null).use { c ->
                    if (c.moveToFirst()) HuaweiFitnessRead.Minute(row.epochSeconds, c.intOrNull(0),
                        c.intOrNull(1), c.intOrNull(2)) else null
                }
                val merged = mergeMinute(old, row)
                val values = ContentValues().apply {
                    put("device", device)
                    put("ts", merged.epochSeconds)
                    put("steps", merged.steps)
                    put("hr", merged.heartRate)
                    put("spo2", merged.spo2)
                }
                if (merged != old) {
                    check(db.insertWithOnConflict("minute", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                    changed = true
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        if (changed) HuaweiTimelineSource.changed()
    }

    fun saveTotals(device: String, day: String, observed: Long, totals: HuaweiFitnessRead.Totals) {
        require(device.matches(Regex("[0-9a-f]{64}")))
        require(day.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && observed > 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val prior = db.query("totals", arrayOf("observed", "steps", "distance"), "device=? AND day=?",
                arrayOf(device, day), null, null, null).use { c ->
                if (c.moveToFirst()) Pair(c.getLong(0), HuaweiFitnessRead.Totals(c.intOrNull(1),
                    if (c.isNull(2)) null else c.getLong(2))) else null
            }
            if (prior == null || observed >= prior.first) {
                val values = ContentValues().apply {
                    put("device", device); put("day", day); put("observed", observed)
                    put("steps", totals.steps ?: prior?.second?.steps)
                    put("distance", totals.distanceMeters ?: prior?.second?.distanceMeters)
                }
                check(db.insertWithOnConflict("totals", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun minutes(device: String, start: Long, end: Long): List<HuaweiFitnessRead.Minute> {
        require(start < end && end - start <= 86400)
        return readableDatabase.query("minute", arrayOf("ts", "steps", "hr", "spo2"),
            "device=? AND ts>=? AND ts<?", arrayOf(device, start.toString(), end.toString()),
            null, null, "ts ASC", "1441").use { c ->
            buildList {
                while (c.moveToNext()) add(HuaweiFitnessRead.Minute(c.getLong(0),
                    c.intOrNull(1), c.intOrNull(2), c.intOrNull(3)))
            }
        }
    }

    /** Indexed, allocation-light aggregation. Device/timestamp PK makes every aligned minute unique. */
    fun stepCoverage(device: String, start: Long, end: Long): HuaweiStepSource.Coverage {
        require(start < end && end-start <= 172800)
        return readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(steps),0) FROM minute WHERE device=? AND ts>=? AND ts<=? AND ts%60=0 AND steps BETWEEN 0 AND 500",
            arrayOf(device,start.toString(),(end-60).toString())).use { c ->
            check(c.moveToFirst())
            HuaweiStepSource.Coverage(start,end,c.getInt(0),c.getLong(1))
        }
    }

    fun firstStepTimestamp(device: String, from: Long, to: Long): Long? =
        readableDatabase.rawQuery(
            "SELECT MIN(ts) FROM minute WHERE device=? AND ts>=? AND ts<? AND steps IS NOT NULL",
            arrayOf(device,from.toString(),to.toString())).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    private fun Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

    companion object {
        fun deviceKey(address: String): String {
            require(address.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")))
            return MessageDigest.getInstance("SHA-256")
                .digest(("alke-huawei-fitness:" + address.uppercase(Locale.ROOT)).toByteArray(Charsets.US_ASCII))
                .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
        }

        /** A missing field in a repeated fetch cannot delete an earlier measured value. */
        fun mergeMinute(old: HuaweiFitnessRead.Minute?, new: HuaweiFitnessRead.Minute): HuaweiFitnessRead.Minute {
            require(old == null || old.epochSeconds == new.epochSeconds)
            return HuaweiFitnessRead.Minute(new.epochSeconds, new.steps ?: old?.steps,
                new.heartRate ?: old?.heartRate, new.spo2 ?: old?.spo2)
        }
    }
}
