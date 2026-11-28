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
    null, 6,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE minute (device TEXT NOT NULL, ts INTEGER NOT NULL, steps INTEGER, hr INTEGER, spo2 INTEGER, PRIMARY KEY(device, ts))")
        db.execSQL("CREATE TABLE totals (device TEXT NOT NULL, day TEXT NOT NULL, observed INTEGER NOT NULL, steps INTEGER, distance INTEGER, PRIMARY KEY(device, day))")
        db.execSQL("CREATE INDEX minute_ts ON minute(ts)")
        createHistoryTables(db)
        createWorkoutDetailTables(db)
        createGpsTable(db)
        createTruSleepTables(db)
        createSyncWindows(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..5 && newVersion == 6) { "Explicit Huawei fitness migration required" }
        if (oldVersion == 1) createHistoryTables(db)
        if (oldVersion < 3) createWorkoutDetailTables(db)
        if (oldVersion < 4) createGpsTable(db)
        if (oldVersion < 5) createTruSleepTables(db)
        createSyncWindows(db)
    }

    private fun createSyncWindows(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sync_window (device TEXT NOT NULL, kind TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, details_complete INTEGER NOT NULL, PRIMARY KEY(device,kind,start,end))")
    }

    /** Local acquisition ledger, never exported as health history. Only completed requests are marked.
     * Scan newest missing closed windows first, over a conservative bounded 7-day horizon. */
    data class Window(val start: Long,val end: Long)
    fun nextHistoryWindow(device: String,kind: String,now: Long,seconds: Long): Window? {
        require(kind in setOf("minute","sleep","workout") && seconds in setOf(3600L,86400L))
        val boundary = Math.floorDiv(now,seconds)*seconds
        val from = boundary - 7*86400
        val db = writableDatabase
        db.delete("sync_window","device=? AND end<?",arrayOf(device,from.toString()))
        val fetched = db.query("sync_window",arrayOf("start"),"device=? AND kind=? AND start>=? AND end<=?",
            arrayOf(device,kind,from.toString(),boundary.toString()),null,null,null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }
        // The current rolling read covers recent data; closed aligned windows are independently replayable.
        var start = boundary-seconds
        while (start>=from) {
            if (start !in fetched) return Window(start,start+seconds)
            start -= seconds
        }
        return null
    }

    private fun markWindow(db: SQLiteDatabase,device: String,kind: String,window: Window,details: Boolean = true) {
        val row = ContentValues().apply {
            put("device",device); put("kind",kind); put("start",window.start); put("end",window.end)
            put("details_complete",if(details) 1 else 0)
        }
        check(db.insertWithOnConflict("sync_window",null,row,SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
    }

    fun saveSleepWindow(device: String,window: Window,rows: List<HuaweiHistoryRead.SleepInterval>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            saveSleep(device,rows)
            markWindow(db,device,"sleep",window)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveWorkoutWindow(device: String,window: Window?,bundles: List<HuaweiFitnessSession.WorkoutBundle>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            saveWorkouts(device,bundles.map { it.summary })
            saveWorkoutDetails(device,bundles)
            if(window!=null) markWindow(db,device,"workout",window,bundles.all { it.detailsComplete })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        // Notify presentation only once the combined summary/detail transaction commits.
        if(bundles.isNotEmpty()) HuaweiTimelineSource.changed(hrChanged=true,stepsChanged=false)
    }

    fun workoutSummaries(device: String,from: Long,to: Long): List<HuaweiHistoryRead.WorkoutSummary> =
        readableDatabase.query("workout_summary",
            arrayOf("number","start","end","native_type","status","distance","steps","calories_raw","elapsed","active"),
            "device=? AND start<=? AND end>=?",arrayOf(device,to.toString(),from.toString()),
            null,null,"start DESC").use { c ->
            buildList {
                fun longOrNull(i: Int): Long? = if(c.isNull(i)) null else c.getLong(i)
                while(c.moveToNext()) add(HuaweiHistoryRead.WorkoutSummary(c.getInt(0),c.getLong(1),c.getLong(2),
                    c.intOrNull(3),c.intOrNull(4),longOrNull(5),longOrNull(6),longOrNull(7),longOrNull(8),longOrNull(9)))
            }
        }

    fun workoutHr(device: String,w: HuaweiHistoryRead.WorkoutSummary): Pair<Int?,Int?> =
        readableDatabase.rawQuery("SELECT AVG(hr),MAX(hr) FROM workout_sample WHERE device=? AND start=? AND number=? AND ts BETWEEN ? AND ? AND hr BETWEEN 25 AND 250",
            arrayOf(device,w.start.toString(),w.number.toString(),w.start.toString(),w.end.toString())).use { c ->
            if(c.moveToFirst() && !c.isNull(0)) kotlin.math.round(c.getDouble(0)).toInt() to c.getInt(1)
            else null to null
        }

    fun latestRecord(device: String): Long? {
        val db = readableDatabase
        return db.rawQuery("SELECT MAX(t) FROM (SELECT MAX(ts) t FROM minute WHERE device=? UNION ALL SELECT MAX(end) t FROM legacy_sleep WHERE device=? UNION ALL SELECT MAX(end) t FROM workout_summary WHERE device=?)",
            arrayOf(device,device,device)).use { c -> if(c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }

    private fun createTruSleepTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trusleep_state_candidate (device TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, native_metadata BLOB NOT NULL, PRIMARY KEY(device,start,end))")
        db.execSQL("CREATE TABLE trusleep_acc_candidate (device TEXT NOT NULL, ts INTEGER NOT NULL, x_raw INTEGER NOT NULL, y_raw INTEGER NOT NULL, z_raw INTEGER NOT NULL, PRIMARY KEY(device,ts))")
        db.execSQL("CREATE TABLE trusleep_ppg_candidate (device TEXT NOT NULL, ts_ms INTEGER NOT NULL, amplitude_raw INTEGER NOT NULL, PRIMARY KEY(device,ts_ms))")
    }

    /** Diagnostic-only staging, after complete authenticated transfers of both files. */
    fun saveTruSleepCandidate(device: String, stateFile: ByteArray, dataFile: ByteArray,
        start: Long, end: Long): HuaweiTruSleepRead.Counts {
        require(device.matches(Regex("[0-9a-f]{64}")))
        val decoded = HuaweiTruSleepRead.decode(stateFile,dataFile,start,end)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (row in decoded.states) {
                val values = ContentValues().apply {
                    put("device",device); put("start",row.start); put("end",row.end)
                    put("native_metadata",row.metadata)
                }
                check(db.insertWithOnConflict("trusleep_state_candidate",null,values,
                    SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            }
            for (row in decoded.acc) {
                val values = ContentValues().apply {
                    put("device",device); put("ts",row.timestamp)
                    put("x_raw",row.x); put("y_raw",row.y); put("z_raw",row.z)
                }
                check(db.insertWithOnConflict("trusleep_acc_candidate",null,values,
                    SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            }
            for (row in decoded.ppg) {
                val values = ContentValues().apply {
                    put("device",device); put("ts_ms",row.timestampMillis)
                    put("amplitude_raw",row.amplitudeRaw)
                }
                check(db.insertWithOnConflict("trusleep_ppg_candidate",null,values,
                    SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return HuaweiTruSleepRead.Counts(decoded.states.size,decoded.acc.size,decoded.ppg.size)
    }

    private fun createGpsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE workout_gps_candidate (device TEXT NOT NULL, start INTEGER NOT NULL, number INTEGER NOT NULL, ts INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, altitude_raw INTEGER, PRIMARY KEY(device,start,number,ts))")
    }

    /**
     * Diagnostic staging only. A verified complete file and matching stored workout
     * are required. An empty or invalid read must not erase a previously staged route.
     */
    fun saveGpsCandidate(device: String, summary: HuaweiHistoryRead.WorkoutSummary, completeFile: ByteArray): Int {
        require(device.matches(Regex("[0-9a-f]{64}")))
        val points = HuaweiGpsRead.decode(completeFile, summary.start, summary.end)
        if (points.isEmpty()) return 0
        val db = writableDatabase
        val args = arrayOf(device,summary.start.toString(),summary.number.toString())
        db.beginTransaction()
        try {
            db.query("workout_summary",arrayOf("end"),"device=? AND start=? AND number=?",
                args,null,null,null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getLong(0) == summary.end) {
                    "Huawei GPS workout identity mismatch"
                }
            }
            db.delete("workout_gps_candidate","device=? AND start=? AND number=?",args)
            for (point in points) {
                val values = ContentValues().apply {
                    put("device",device); put("start",summary.start); put("number",summary.number)
                    put("ts",point.timestamp); put("latitude",point.latitude); put("longitude",point.longitude)
                    put("altitude_raw",point.altitudeRaw)
                }
                check(db.insertOrThrow("workout_gps_candidate",null,values) != -1L)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        HuaweiTimelineSource.changed(hrChanged=true,stepsChanged=false)
        return points.size
    }

    fun gpsCandidate(device: String, start: Long, number: Int): List<HuaweiGpsRead.Point> {
        require(device.matches(Regex("[0-9a-f]{64}")) && start >= 0 && number in 0..32767)
        return readableDatabase.query("workout_gps_candidate",
            arrayOf("ts","latitude","longitude","altitude_raw"),"device=? AND start=? AND number=?",
            arrayOf(device,start.toString(),number.toString()),null,null,"ts ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(HuaweiGpsRead.Point(cursor.getLong(0),
                    cursor.getDouble(1),cursor.getDouble(2),cursor.intOrNull(3)))
            }
        }
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

    fun saveMinutes(device: String, start: Long, end: Long, records: List<HuaweiFitnessRead.Minute>, checkpoint: Boolean = false) {
        require(device.matches(Regex("[0-9a-f]{64}")))
        require(start < end && end - start <= 86400)
        require(records.all { it.epochSeconds in start until end })
        require(records.map { it.epochSeconds }.distinct().size == records.size)
        val db = writableDatabase
        var hrChanged = false
        var stepsChanged = false
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
                    hrChanged = hrChanged || merged.heartRate != old?.heartRate
                    stepsChanged = stepsChanged || merged.steps != old?.steps
                }
            }
            if(checkpoint) markWindow(db,device,"minute",Window(start,end))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        HuaweiTimelineSource.changed(hrChanged, stepsChanged)
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
