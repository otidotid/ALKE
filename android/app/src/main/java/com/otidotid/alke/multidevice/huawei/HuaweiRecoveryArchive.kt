package com.otidotid.alke.multidevice.huawei

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import com.otidotid.alke.data.ArchiveEncryption
import java.io.File

/**
 * Explicit encrypted recovery foundation, independent of WHOOP backups.
 * No binding credentials, device selection preferences, or session keys.
 * Restore merges missing rows only; current observations are never replaced.
 * Manual document-provider export and restore. No automatic Drive upload.
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
        val actual = objects.filter { it.first == "table" }.map { it.second }.toSet()
        // Android's standard locale bookkeeping is not a sensor or credential table.
        require(actual - "android_metadata" == tables.keys)
        require(objects.all { it.first == "table" || it == ("index" to "minute_ts") })
        for ((table,columns) in tables) {
            val names = db.rawQuery("PRAGMA table_info($table)",null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }
            require(names == columns.split(','))
        }
        validateDomains(db)
        db.rawQuery("PRAGMA quick_check",null).use { c ->
            require(c.moveToFirst() && c.getString(0) == "ok" && !c.moveToNext())
        }
    }

    private fun validateDomains(db: SQLiteDatabase) {
        fun range(column: String, min: Long, max: Long, nullable: Boolean = false): String {
            val check = "(typeof($column)='integer' AND $column BETWEEN $min AND $max)"
            return if (nullable) "($column IS NULL OR $check)" else check
        }
        val timestamp = 0xffffffffL
        val time = { c: String -> range(c,0,timestamp) }
        val workout = time("start") + " AND " + range("number",0,32767)
        val checks = mapOf(
            "minute" to listOf(time("ts"), range("steps",0,500,true),range("hr",25,250,true),range("spo2",50,100,true)),
            "totals" to listOf("typeof(day)='text' AND length(day)=10 AND day GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'",
                range("observed",1,timestamp),range("steps",0,200000,true),range("distance",0,1000000,true)),
            "legacy_sleep" to listOf(time("start"),time("end"),"end>start AND end-start<=86400",range("native_type",0,255)),
            "workout_summary" to listOf(workout,time("end"),"end>start AND end-start<=604800",
                range("native_type",0,254,true),range("status",0,254,true)) +
                listOf("distance","steps","calories_raw","elapsed","active").map { range(it,0,timestamp-1,true) },
            "workout_sample" to listOf(workout,time("ts"),range("hr",25,250,true),range("speed_raw",0,65534,true),
                range("step_rate_raw",0,254,true),range("altitude_raw",Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong(),true),
                range("cadence_raw",0,65534,true)),
            "workout_pace" to listOf(workout,range("ordinal",0,1000000),range("distance_raw",0,65535),
                range("native_type",0,255),range("pace_raw",0,timestamp),
                range("point_index",0,65535,true),range("correction_raw",0,65535,true)),
            "workout_gps_candidate" to listOf(workout,time("ts"),
                "typeof(latitude) IN ('real','integer') AND latitude BETWEEN -90 AND 90",
                "typeof(longitude) IN ('real','integer') AND longitude BETWEEN -180 AND 180",
                range("altitude_raw",-32768,32767,true)),
            "trusleep_state_candidate" to listOf(time("start"),time("end"),"end>start AND end-start<=604800",
                "typeof(native_metadata)='blob' AND length(native_metadata)=8"),
            "trusleep_acc_candidate" to listOf(time("ts"),range("x_raw",0,255),range("y_raw",0,255),range("z_raw",0,255)),
            "trusleep_ppg_candidate" to listOf(range("ts_ms",0,timestamp*1000+999),range("amplitude_raw",-32768,32767))
        )
        for ((table, predicates) in checks) {
            val identity = "typeof(device)='text' AND length(device)=64 AND device NOT GLOB '*[^0-9a-f]*'"
            val condition = (listOf(identity)+predicates).joinToString(" AND ") { "($it)" }
            db.rawQuery("SELECT 1 FROM $table WHERE COALESCE(($condition),0)=0 LIMIT 1",null).use {
                require(!it.moveToFirst()) { "Invalid Huawei archive row" }
            }
        }
        for (table in listOf("workout_sample","workout_pace","workout_gps_candidate")) {
            val window = if (table == "workout_pace") "" else " AND d.ts BETWEEN s.start AND s.end"
            db.rawQuery("SELECT 1 FROM $table d WHERE NOT EXISTS (SELECT 1 FROM workout_summary s WHERE s.device=d.device AND s.start=d.start AND s.number=d.number$window) LIMIT 1",null).use {
                require(!it.moveToFirst()) { "Orphan Huawei workout detail" }
            }
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

    private fun boundedCopy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(64*1024)
        var bytes = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            bytes += n
            require(bytes <= MAX_BYTES+1024*1024)
            output.write(buffer,0,n)
        }
        return bytes
    }

    private fun digest(input: InputStream): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        boundedCopy(input,object : OutputStream() {
            override fun write(b: Int) { hash.update(b.toByte()) }
            override fun write(b: ByteArray,off: Int,len: Int) { hash.update(b,off,len) }
        })
        return hash.digest()
    }

    fun exportToUri(context: Context, uri: Uri, passphrase: CharArray) {
        val encrypted = File.createTempFile("huawei-export-",".alkeenc",context.cacheDir)
        check(encrypted.delete())
        try {
            export(context,encrypted,passphrase)
            val resolver = context.contentResolver
            requireNotNull(resolver.openOutputStream(uri,"wt")).use { out ->
                encrypted.inputStream().use { boundedCopy(it,out) }
            }
            val expected = encrypted.inputStream().use(::digest)
            val actual = requireNotNull(resolver.openInputStream(uri)).use(::digest)
            check(MessageDigest.isEqual(expected,actual)) { "Written Huawei backup failed verification" }
        } finally { encrypted.delete() }
    }

    fun restoreFromUri(context: Context, uri: Uri, passphrase: CharArray): Long {
        val encrypted = File.createTempFile("huawei-import-",".alkeenc",context.cacheDir)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                encrypted.outputStream().use { boundedCopy(input,it) }
            }
            return restoreMissing(context,encrypted,passphrase)
        } finally { encrypted.delete() }
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
