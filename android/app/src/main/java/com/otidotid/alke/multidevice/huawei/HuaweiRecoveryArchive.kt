package com.otidotid.alke.multidevice.huawei

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.otidotid.alke.data.ArchiveEncryption
import java.io.File

/**
 * Explicit encrypted recovery foundation, independent of WHOOP backups.
 * No binding credentials, device selection preferences, or session keys.
 * Restore merges missing rows only; current observations are never replaced.
 * Not yet wired to the Settings/Drive backup UI.
 */
internal object HuaweiRecoveryArchive {
    private const val VERSION = 5
    private const val MAX_BYTES = 256L * 1024 * 1024
    private val tables = linkedMapOf(
        "minute" to "device,ts,steps,hr,spo2",
        "totals" to "device,day,observed,steps,distance",
        "legacy_sleep" to "device,start,end,native_type",
        "workout_summary" to "device,start,number,end,native_type,status,distance,steps,calories_raw,elapsed,active",
        "workout_sample" to "device,start,number,ts,hr,speed_raw,step_rate_raw,altitude_raw,cadence_raw",
        "workout_pace" to "device,start,number,ordinal,distance_raw,native_type,pace_raw,point_index,correction_raw",
        "workout_gps_candidate" to "device,start,number,ts,latitude,longitude,altitude_raw",
        "trusleep_state_candidate" to "device,start,end,native_metadata",
        "trusleep_acc_candidate" to "device,ts,x_raw,y_raw,z_raw",
        "trusleep_ppg_candidate" to "device,ts_ms,amplitude_raw"
    )

    private fun validate(db: SQLiteDatabase) {
        require(db.version == VERSION) { "Unsupported Huawei archive version" }
        val objects = db.rawQuery("SELECT type,name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'",null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
        require(objects.filter { it.first == "table" }.map { it.second }.toSet() == tables.keys)
        require(objects.all { it.first == "table" || it == ("index" to "minute_ts") })
        for ((table,columns) in tables) {
            val names = db.rawQuery("PRAGMA table_info($table)",null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }
            require(names == columns.split(','))
        }
        db.rawQuery("PRAGMA quick_check",null).use { c ->
            require(c.moveToFirst() && c.getString(0) == "ok" && !c.moveToNext())
        }
    }

    private fun checkedFile(file: File) {
        require(file.isFile && file.length() in 1..MAX_BYTES)
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use(::validate)
    }

    fun export(context: Context, encryptedDestination: File, passphrase: CharArray) {
        val temp = File.createTempFile("huawei-snapshot-",".sqlite",context.cacheDir)
        try {
            HuaweiFitnessStore(context).use { store ->
                val db = store.writableDatabase
                db.beginTransaction()
                try {
                    // A rollback-journal database is stable while this writer lock is held.
                    // Do not copy a WAL database's main file and silently lose committed data.
                    db.rawQuery("PRAGMA journal_mode",null).use { c ->
                        require(c.moveToFirst() && c.getString(0).lowercase() == "delete")
                    }
                    val source = File(db.path)
                    require(source.length() in 1..MAX_BYTES)
                    source.copyTo(temp,overwrite=true)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            checkedFile(temp)
            ArchiveEncryption.encrypt(temp,encryptedDestination,passphrase)
        } finally { temp.delete() }
    }

    fun restoreMissing(context: Context, encryptedSource: File, passphrase: CharArray): Long {
        require(encryptedSource.isFile && encryptedSource.length() in 1..(MAX_BYTES+1024*1024))
        val temp = File.createTempFile("huawei-restore-",".sqlite",context.cacheDir)
        check(temp.delete())
        try {
            ArchiveEncryption.decrypt(encryptedSource,temp,passphrase)
            checkedFile(temp)
            var inserted = 0L
            SQLiteDatabase.openDatabase(temp.path,null,SQLiteDatabase.OPEN_READONLY).use { source ->
                HuaweiFitnessStore(context).use { store ->
                    val dest = store.writableDatabase
                    dest.beginTransaction()
                    try {
                        for ((table,columns) in tables) {
                            source.query(table,columns.split(',').toTypedArray(),null,null,null,null,null).use { c ->
                                while (c.moveToNext()) {
                                    require(c.getString(0).matches(Regex("[0-9a-f]{64}")))
                                    val values = android.content.ContentValues()
                                    DatabaseUtils.cursorRowToContentValues(c,values)
                                    if (dest.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_IGNORE) != -1L) inserted++
                                }
                            }
                        }
                        dest.setTransactionSuccessful()
                    } finally { dest.endTransaction() }
                }
            }
            if (inserted > 0) HuaweiTimelineSource.changed()
            return inserted
        } finally { temp.delete() }
    }
}
