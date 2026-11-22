package com.otidotid.alke.data

import android.content.Context
import com.otidotid.alke.ui.HostedCardPrefs
import com.otidotid.alke.ui.AlkePrefs
import com.otidotid.alke.ui.ProfileStore
import com.otidotid.alke.ui.ProfileAvatarStore
import com.otidotid.alke.ui.UnitPrefs
import org.json.JSONObject

/**
 * The `settings.json` payload inside a `.alkebak` backup (#1000) — the Android twin of the Apple
 * `BackupSettings` in Packages/WhoopStore.
 *
 * A `.alkebak` is a ZIP whose first entry is the SQLite database. That round-trips every row, but the
 * user's profile (age / sex / weight / height / HR-max override) and display preferences live in
 * SharedPreferences (UserDefaults on Apple), so a restore onto a fresh device silently reset them —
 * the "restore doesn't bring back settings/weight/height" half of #1000. This adds a SECOND, optional
 * ZIP entry — `settings.json`, a flat JSON object — carrying exactly one WHITELISTED set of keys.
 *
 * The whitelist is the contract, defined once per platform and mirrored byte-for-byte by the Apple
 * `BackupSettings.whitelist` (same canonical key strings, same JSON kinds). Only stable, user-set,
 * non-device-specific values are allowed. NEVER add device ids, peripheral ids, tokens, sync cursors,
 * or anything anonymity-sensitive: backups get copied into cloud folders and attached to GitHub
 * issues, so this file must stay safe to share. Unknown keys in an incoming `settings.json` are
 * dropped; a backup with no `settings.json` (every pre-#1000 backup) is a DB-only restore, as before.
 *
 * [BackupSettingsCodec] is pure JSON + whitelist (plain-JVM unit-testable, no Context); the
 * SharedPreferences boundary lives in [BackupSettingsBridge] below.
 */
object BackupSettingsCodec {

    /** Canonical entry name inside the `.alkebak` ZIP. Matches the Apple exporter byte-for-byte. */
    const val ENTRY_NAME = "settings.json"

    /** The JSON kind a whitelisted key must decode to. Anything else is dropped, never guessed at. */
    enum class Kind { INT, DOUBLE, STRING, BOOLEAN, LONG }

    /**
     * ALKE Android-only personal preferences. Explicitly allowlisted rather than copying an entire
     * SharedPreferences XML: never export tokens, Google account credentials, BLE identifiers, sync
     * cursors, calibration outputs, permissions, pairing state or WorkManager runtime state.
     * Prefixes keep this extension distinct from portable iOS/Android settings.json keys.
     */
    val ANDROID_MIGRATION_PREFS: Map<String, Map<String, Kind>> = linkedMapOf(
        "alke_prefs" to linkedMapOf(
            "today.keyMetricsDetailed" to Kind.BOOLEAN,
            "appearance.gaugeNumerals" to Kind.STRING,
            "today.totalCaloriesTargetKcal" to Kind.INT,
            "workout.recentSports" to Kind.STRING,
            "workoutKeepScreenOn" to Kind.BOOLEAN,
            "alke.napDetectionEnabled" to Kind.BOOLEAN,
            "alke.journalCatalogV2" to Kind.STRING,
            "biofeedback.stressCheckIn" to Kind.BOOLEAN,
            "biofeedback.stressAutoNudge" to Kind.BOOLEAN,
            "biofeedback.stressQuietHours" to Kind.BOOLEAN,
            "biofeedback.stressUseResonancePace" to Kind.BOOLEAN,
            "biofeedback.stressQuietStartMin" to Kind.INT,
            "biofeedback.stressQuietEndMin" to Kind.INT,
            "biofeedback.resonanceBpm" to Kind.DOUBLE,
        ),
        "alke_inactivity_prefs" to linkedMapOf(
            "inactivity.enabled" to Kind.BOOLEAN,
            "inactivity.thresholdMinutes" to Kind.INT,
            "inactivity.reNudgeMinutes" to Kind.INT,
            "inactivity.buzzLoops" to Kind.INT,
            "inactivity.activeHoursEnabled" to Kind.BOOLEAN,
            "inactivity.activeStartMinutes" to Kind.INT,
            "inactivity.activeEndMinutes" to Kind.INT,
        ),
        "alke_haptics_prefs" to linkedMapOf(
            "haptics.breathing" to Kind.BOOLEAN,
            "haptics.intervals" to Kind.BOOLEAN,
            "haptics.liveSession" to Kind.BOOLEAN,
            "haptics.workout" to Kind.BOOLEAN,
        ),
        "alke_notif_prefs" to linkedMapOf(
            "notif.masterEnabled" to Kind.BOOLEAN,
            "notif.allOtherApps" to Kind.BOOLEAN,
            "notif.onlyWhenWorn" to Kind.BOOLEAN,
            "notif.quietHoursEnabled" to Kind.BOOLEAN,
            "notif.quietStartMinutes" to Kind.INT,
            "notif.quietEndMinutes" to Kind.INT,
            "notif.calls.masterEnabled" to Kind.BOOLEAN,
            "notif.calls.phoneEnabled" to Kind.BOOLEAN,
            "notif.calls.voipEnabled" to Kind.BOOLEAN,
            "notif.calls.pattern" to Kind.STRING,
            "notif.alarmTimer" to Kind.BOOLEAN,
        ),
    )

    fun androidBackupKey(file: String, key: String): String = "android.$file.$key"

    /**
     * THE whitelist — the only keys `settings.json` may carry, keyed by their CANONICAL
     * (platform-neutral) names. Mirrors the Apple `BackupSettings.whitelist` exactly.
     *
     * Profile: the body metrics that power HR zones / calories / recovery baselines, plus the manual
     * HR-max override (`profile.hrMax`, 0 = auto/Tanaka). Display: the body and exercise-distance
     * systems, separate temperature override ("" = follow body), and the Effort axis (#268). Deliberately
     * EXCLUDED: step calibration (per-strap, not per-person), the steps-engine fitted outputs
     * (derived), and every alke.* toggle that is device- or install-specific.
     */
    val WHITELIST: Map<String, Kind> = linkedMapOf(
        "profile.age" to Kind.INT,
        "profile.sex" to Kind.STRING,
        "profile.weightKg" to Kind.DOUBLE,
        "profile.heightCm" to Kind.DOUBLE,
        "profile.waistCm" to Kind.DOUBLE,
        "profile.hrMax" to Kind.INT,
        "profile.hrZoneThresholds" to Kind.STRING,
        "profile.dateOfBirthMillis" to Kind.LONG,
        "profile.avatarPresent" to Kind.INT,
        "profile.avatarJpegBase64" to Kind.STRING,
        "units.system" to Kind.STRING,
        "units.distance" to Kind.STRING,
        "units.temperature" to Kind.STRING,
        "units.skinTempDisplay" to Kind.STRING,   // #1846, carried like the other display units
        "effort.scale" to Kind.STRING,
        "dayCycle.mode" to Kind.STRING,
        // The ONE layout pref carried (#today-hosted-cards): the Trends/Sleep cards the user chose to host
        // in Today, a JSON [String] of ids. Unlike section order, this is a deliberate composition the user
        // built and expects across a restore. Its POSITION (the addedCards slot in today.sectionOrder) is
        // not carried, so on restore the set + internal order return but the section sits at its default.
        HostedCardPrefs.KEY_SELECTION to Kind.STRING,
        "today.sectionOrder" to Kind.STRING,
        "today.hiddenSections" to Kind.STRING,
        "today.dashboardCards" to Kind.STRING,
        "today.keyMetrics" to Kind.STRING,
        "today.keyMetricsWindowDays" to Kind.INT,

        // Stable layout and appearance choices that should follow the user's ALKE profile to a new phone.
        // All are plain strings in alke_prefs; device/runtime state remains deliberately excluded.
        "sleep.sectionOrder" to Kind.STRING,
        "sleep.hiddenSections" to Kind.STRING,
        "theme.appearance" to Kind.STRING,
        "accent.color" to Kind.STRING,
        "accent.customHex" to Kind.STRING,
        "chart.style" to Kind.STRING,
        "alke.appLanguage" to Kind.STRING,

        // #1361: the user's own custom journal BEHAVIOURS (a newline-joined list of names). The journal
        // EFFECTS ride the DB backup, but the behaviour DEFINITIONS live only in prefs, so a restore left
        // the entries referencing behaviours the logging catalog no longer offered. Platform-neutral key;
        // the same newline value is bridged out of the catalog on both platforms.
        // SCOPE: NAMES only — the wire carries no kind/group, so a numeric custom behaviour restores as a
        // plain .bool toggle (identical on both platforms; historical entries keep their DB numericValue).
        "journal.customBehaviors" to Kind.STRING,
    ).apply {
        for ((file, keys) in ANDROID_MIGRATION_PREFS) {
            for ((key, kind) in keys) {
                put(androidBackupKey(file, key), kind)
            }
        }
    }

    /**
     * Encode the whitelisted subset of [values] as the flat `settings.json` object, or null when
     * nothing whitelisted is present (the exporter then writes a DB-only backup — indistinguishable
     * from a legacy one, which is exactly the right degrade).
     */
    fun encode(values: Map<String, Any?>): String? {
        val obj = JSONObject()
        for ((key, kind) in WHITELIST) {
            val coerced = coerce(values[key], kind) ?: continue
            obj.put(key, coerced)
        }
        return if (obj.length() == 0) null else obj.toString()
    }

    /**
     * Decode a `settings.json` payload down to its whitelisted, correctly-typed subset. Malformed
     * JSON, unknown keys and wrong-typed values all degrade to "fewer keys" — never an error, because
     * a bad settings entry must not fail a restore whose DB half is fine.
     */
    fun decode(json: String): Map<String, Any> {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for ((key, kind) in WHITELIST) {
            if (!obj.has(key)) continue
            coerce(obj.opt(key), kind)?.let { out[key] = it }
        }
        return out
    }

    /**
     * Coerce a JSON-decoded (or caller-supplied) value to the whitelist's declared kind, or null.
     * JSON booleans are not [Number]s on the JVM, so `true` can never become age 1 (the Apple side
     * refuses NSNumber-booleans explicitly for the same reason).
     */
    private fun coerce(value: Any?, kind: Kind): Any? = when (kind) {
        Kind.STRING -> value as? String
        Kind.INT -> (value as? Number)?.toInt()
        Kind.DOUBLE -> (value as? Number)?.toDouble()
        Kind.BOOLEAN -> value as? Boolean
        Kind.LONG -> (value as? Number)?.toLong()
    }
}

/**
 * The SharedPreferences boundary for [BackupSettingsCodec]: snapshot this device's whitelisted
 * settings for export, and re-apply a restored payload. Kept separate from the codec so the codec
 * stays plain-JVM testable (this object needs a real Context).
 *
 * Storage mapping (canonical key → where it actually lives here):
 *  - `profile.*`  → the `alke_profile` prefs via [ProfileStore.backupSnapshot]/[ProfileStore.applyBackup]
 *                   (canonical `profile.hrMax` ↔ ProfileStore's `hr_max_override`).
 *  - `units.*` / `effort.scale` → [AlkePrefs] under the SAME literal key strings as the canonical names
 *                   (they were already kept identical to the Apple @AppStorage keys).
 */
object BackupSettingsBridge {

    /** Stable string preferences stored under the same canonical key in alke_prefs. */
    private val STABLE_STRING_PREFS = listOf(
        "sleep.sectionOrder",
        "sleep.hiddenSections",
        "theme.appearance",
        "accent.color",
        "accent.customHex",
        "chart.style",
        "alke.appLanguage",
        "today.sectionOrder",
        "today.hiddenSections",
        "today.dashboardCards",
        "today.keyMetrics",
    )

    /** Journal-catalog storage keys in `alke_prefs` (#1361). The v2 items blob is the LIVE store; the
     *  legacy custom/hidden keys are read ONCE by `JournalCatalog.loadJournalCatalogItems` to migrate into
     *  v2, then never again. So export pulls custom names out of the v2 blob (parsed raw here to avoid a
     *  data→ui dependency — the JSON shape mirrors `JournalCatalog.encodeJournalCatalog`), and restore
     *  writes the names to the legacy custom key and CLEARS the v2 blob so the next load re-migrates them
     *  (a restore requires an app restart, #57). Must match `JournalCatalog`. Mirrors the iOS bridge. */
    private const val JOURNAL_CATALOG_V2_PREF = "alke.journalCatalogV2"
    private const val JOURNAL_LEGACY_CUSTOM_PREF = "alke.journalCustomQuestions"
    private const val JOURNAL_LEGACY_HIDDEN_PREF = "alke.journalHiddenQuestions"

    /** The whitelisted, user-SET settings of this device as the `settings.json` string, or null. */
    fun snapshotJson(context: Context): String? {
        val values = LinkedHashMap<String, Any>()
        val profile = ProfileStore.from(context)
        values.putAll(profile.backupSnapshot())
        values["profile.dateOfBirthMillis"] = profile.dateOfBirthMillis
        val avatar = ProfileAvatarStore.snapshotForBackup(context)
        values["profile.avatarPresent"] = if (avatar == null) 0 else 1
        if (avatar != null) values["profile.avatarJpegBase64"] = avatar
        val alke = AlkePrefs.of(context)
        if (alke.contains(AlkePrefs.KEY_UNIT_SYSTEM)) {
            alke.getString(AlkePrefs.KEY_UNIT_SYSTEM, null)?.let { values["units.system"] = it }
        }
        if (alke.contains(AlkePrefs.KEY_DISTANCE_UNIT_SYSTEM)) {
            alke.getString(AlkePrefs.KEY_DISTANCE_UNIT_SYSTEM, null)?.let { values["units.distance"] = it }
        }
        if (alke.contains(AlkePrefs.KEY_TEMPERATURE_UNIT)) {
            alke.getString(AlkePrefs.KEY_TEMPERATURE_UNIT, null)?.let { values["units.temperature"] = it }
        }
        // #1846: carried like the other display units. The whitelist entry alone does NOTHING — export and
        // import are both explicit per key here, so a whitelisted-but-unwired pref silently never restores.
        if (alke.contains(AlkePrefs.KEY_SKIN_TEMP_DISPLAY)) {
            alke.getString(AlkePrefs.KEY_SKIN_TEMP_DISPLAY, null)?.let { values["units.skinTempDisplay"] = it }
        }
        if (alke.contains(UnitPrefs.KEY_EFFORT_SCALE)) {
            alke.getString(UnitPrefs.KEY_EFFORT_SCALE, null)?.let { values["effort.scale"] = it }
        }
        if (alke.contains(AlkePrefs.KEY_DAY_CYCLE_MODE)) {
            alke.getString(AlkePrefs.KEY_DAY_CYCLE_MODE, null)?.let { values["dayCycle.mode"] = it }
        }
        if (alke.contains(HostedCardPrefs.KEY_SELECTION)) {
            alke.getString(HostedCardPrefs.KEY_SELECTION, null)?.let { values[HostedCardPrefs.KEY_SELECTION] = it }
        }
        for (key in STABLE_STRING_PREFS) {
            if (alke.contains(key)) alke.getString(key, null)?.let { values[key] = it }
        }
        if (alke.contains("today.keyMetricsWindowDays")) {
            values["today.keyMetricsWindowDays"] = alke.getInt("today.keyMetricsWindowDays", 14)
        }
        // Preserve ONLY known, user-edited settings. Never wholesale-copy preference files.
        for ((file, keys) in BackupSettingsCodec.ANDROID_MIGRATION_PREFS) {
            val prefs = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)
            for ((key, kind) in keys) {
                val raw = prefs.all[key] ?: continue
                val mapped: Any? = when (kind) {
                    BackupSettingsCodec.Kind.BOOLEAN -> raw as? Boolean
                    BackupSettingsCodec.Kind.INT -> raw as? Int
                    BackupSettingsCodec.Kind.LONG -> raw as? Long
                    BackupSettingsCodec.Kind.DOUBLE -> (raw as? Number)?.toDouble()
                    BackupSettingsCodec.Kind.STRING -> raw as? String
                }
                if (mapped != null) values[BackupSettingsCodec.androidBackupKey(file, key)] = mapped
            }
        }
        // #1361: custom journal behaviours from the LIVE v2 catalog blob (the legacy key is read-once-
        // then-stale). Parse raw to avoid a data→ui dependency; shape mirrors JournalCatalog.
        alke.getString(JOURNAL_CATALOG_V2_PREF, null)?.let { blob ->
            val names = runCatching {
                val arr = org.json.JSONArray(blob)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.takeIf { it.optBoolean("custom", false) }
                        ?.optString("canonical", "")?.takeIf { it.isNotEmpty() }
                }
            }.getOrDefault(emptyList())
            if (names.isNotEmpty()) values["journal.customBehaviors"] = names.joinToString("\n")
        }
        return BackupSettingsCodec.encode(values)
    }

    /**
     * Re-apply a restored `settings.json` to this device. The caller ([DataBackup.importFrom]) invokes
     * this only AFTER the DB swap succeeded — never on a failed or rolled-back restore. Keys absent
     * from the payload leave the device's current values alone; the profile setters clamp to their
     * normal ranges, so a hand-edited payload can't write absurd values.
     */
    fun apply(context: Context, json: String) {
        val values = BackupSettingsCodec.decode(json)
        if (values.isEmpty()) return

        val profile = ProfileStore.from(context)
        profile.applyBackup(values)
        (values["profile.dateOfBirthMillis"] as? Long)?.let { dob ->
            // Preserve the actual date of birth, not merely the age rounded to a year.
            if (dob in 1L..System.currentTimeMillis()) profile.dateOfBirthMillis = dob
        }

        val editor = AlkePrefs.of(context).edit()
        (values["units.system"] as? String)?.let { editor.putString(AlkePrefs.KEY_UNIT_SYSTEM, it) }
        (values["units.distance"] as? String)?.let { editor.putString(AlkePrefs.KEY_DISTANCE_UNIT_SYSTEM, it) }
        (values["units.temperature"] as? String)?.let { raw ->
            // "" is the Apple side's "follow body measurements"; here that state is key-absent.
            if (raw.isEmpty()) editor.remove(AlkePrefs.KEY_TEMPERATURE_UNIT)
            else editor.putString(AlkePrefs.KEY_TEMPERATURE_UNIT, raw)
        }
        (values["units.skinTempDisplay"] as? String)?.let { raw ->
            // Absent/"" is "lead with a temperature", stored as key-absent — same convention as the
            // temperature override above, and what an older backup without this key restores to.
            if (raw.isEmpty()) editor.remove(AlkePrefs.KEY_SKIN_TEMP_DISPLAY)
            else editor.putString(AlkePrefs.KEY_SKIN_TEMP_DISPLAY, raw)
        }
        (values["effort.scale"] as? String)?.let { editor.putString(UnitPrefs.KEY_EFFORT_SCALE, it) }
        (values["dayCycle.mode"] as? String)?.let { editor.putString(AlkePrefs.KEY_DAY_CYCLE_MODE, it) }
        (values[HostedCardPrefs.KEY_SELECTION] as? String)?.let { editor.putString(HostedCardPrefs.KEY_SELECTION, it) }
        (values["today.sectionOrder"] as? String)?.let {
            editor.putString("today.sectionOrder", it)
        }
        (values["today.hiddenSections"] as? String)?.let {
            editor.putString("today.hiddenSections", it)
        }
        (values["today.dashboardCards"] as? String)?.let {
            editor.putString("today.dashboardCards", it)
        }
        (values["today.keyMetrics"] as? String)?.let {
            editor.putString("today.keyMetrics", it)
        }
        (values["today.keyMetricsWindowDays"] as? Int)?.let {
            editor.putInt("today.keyMetricsWindowDays", it)
        }
        for (key in STABLE_STRING_PREFS) {
            (values[key] as? String)?.let { editor.putString(key, it) }
        }
        // #1361: restore custom behaviours — write the names to the legacy custom key, clear stale hidden,
        // and drop the v2 blob so the next catalog load re-migrates them (restart-gated, #57). Mirrors iOS.
        (values["journal.customBehaviors"] as? String)?.let { joined ->
            editor.putString(JOURNAL_LEGACY_CUSTOM_PREF, joined)
            editor.remove(JOURNAL_LEGACY_HIDDEN_PREF)
            editor.remove(JOURNAL_CATALOG_V2_PREF)
        }
        editor.apply()
        for ((file, keys) in BackupSettingsCodec.ANDROID_MIGRATION_PREFS) {
            val prefs = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            for ((key, kind) in keys) {
                val saved = values[BackupSettingsCodec.androidBackupKey(file, key)] ?: continue
                when (kind) {
                    BackupSettingsCodec.Kind.BOOLEAN -> (saved as? Boolean)?.let { prefs.putBoolean(key, it) }
                    BackupSettingsCodec.Kind.INT -> (saved as? Int)?.let { prefs.putInt(key, it) }
                    BackupSettingsCodec.Kind.LONG -> (saved as? Long)?.let { prefs.putLong(key, it) }
                    BackupSettingsCodec.Kind.DOUBLE -> (saved as? Double)?.let { prefs.putFloat(key, it.toFloat()) }
                    BackupSettingsCodec.Kind.STRING -> (saved as? String)?.let { prefs.putString(key, it) }
                }
            }
            prefs.apply()
        }
        when (values["profile.avatarPresent"] as? Int) {
            0 -> ProfileAvatarStore.clearAvatar(context)
            1 -> {
                val encoded = values["profile.avatarJpegBase64"] as? String
                if (encoded == null || !ProfileAvatarStore.restoreFromBackup(context, encoded)) {
                    // No user photo is overwritten on an invalid avatar. Health/Food restore stays safe.
                    throw IllegalArgumentException("Profile photo could not be restored")
                }
            }
        }
    }
}
