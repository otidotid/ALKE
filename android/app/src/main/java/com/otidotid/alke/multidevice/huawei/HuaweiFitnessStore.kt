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
    null, 1,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE minute (device TEXT NOT NULL, ts INTEGER NOT NULL, steps INTEGER, hr INTEGER, spo2 INTEGER, PRIMARY KEY(device, ts))")
        db.execSQL("CREATE TABLE totals (device TEXT NOT NULL, day TEXT NOT NULL, observed INTEGER NOT NULL, steps INTEGER, distance INTEGER, PRIMARY KEY(device, day))")
        db.execSQL("CREATE INDEX minute_ts ON minute(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Explicit Huawei fitness migration required")
    }

    fun saveMinutes(device: String, start: Long, end: Long, records: List<HuaweiFitnessRead.Minute>) {
        require(device.matches(Regex("[0-9a-f]{64}")))
        require(start < end && end - start <= 86400)
        require(records.all { it.epochSeconds in start until end })
        require(records.map { it.epochSeconds }.distinct().size == records.size)
        val db = writableDatabase
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
                if (merged != old) check(db.insertWithOnConflict("minute", null, values,
                    SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
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
