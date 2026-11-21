package com.otidotid.alke.location

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.Locale
import com.otidotid.alke.analytics.RouteMath
import com.otidotid.alke.analytics.RouteMath.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level holder for the in-flight GPS workout's route, owned by [com.otidotid.alke.AlkeApplication]
 * and driven by [com.otidotid.alke.ble.WhoopConnectionService] — NOT by the Activity-scoped AppViewModel.
 *
 * Why this exists: the route used to be collected in `AppViewModel.viewModelScope`, which Android
 * cancels the moment the ViewModel is cleared (screen off / Activity backgrounded). The collection
 * stopped mid-ride and distance froze (#215 — a 2.84 km ride banked as 0.38 km). The track now lives
 * here, at the process level, and the always-on foreground service feeds it from the platform
 * LocationManager, so it survives the UI going away. The ViewModel observes [state] for live display
 * and reads the final [State.track] when ending the workout; it no longer owns the location stream.
 *
 * Distance/pace are derived here (off the same [RouteMath] helpers AppViewModel used) so the running
 * totals are correct even across periods when no UI is observing.
 */
object GpsSession {

    /** A GPS workout's accumulated route. [startMs] anchors pace; [active] gates the service collector.
     *  [sportName] lets the UI rehydrate the active-workout card if the ViewModel was cleared mid-ride. */
    data class State(
        val active: Boolean = false,
        val startMs: Long = 0L,
        val sportName: String = "",
        val track: List<LatLng> = emptyList(),
        val distanceM: Double = 0.0,
        val paceSecPerKm: Double? = null,
        val paused: Boolean = false,
        val pausedAtMs: Long? = null,
        val pausedDurationMs: Long = 0L,
    )

    private const val PREFS_NAME = "alke_active_gps_session"
    private const val FILE_NAME = "alke_active_gps_track.txt"
    private const val MAX_RECOVERY_POINTS = 100_000
    private const val MAX_SESSION_AGE_MS = 3L * 24 * 60 * 60 * 1000

    private var prefs: SharedPreferences? = null
    private var routeFile: File? = null

    /** Restore an in-flight workout after Android killed the process. */
    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val file = File(app.noBackupFilesDir, FILE_NAME)
        prefs = p
        routeFile = file
        if (!p.getBoolean("active", false)) return
        val start = p.getLong("startMs", 0L)
        val elapsed = System.currentTimeMillis() - start
        if (start <= 0L || elapsed !in 0..MAX_SESSION_AGE_MS) {
            clearPersistedSession()
            return
        }
        val sport = p.getString("sportName", "") ?: ""
        val points = runCatching {
            if (!file.isFile) emptyList() else file.useLines { lines ->
                lines.take(MAX_RECOVERY_POINTS).mapNotNull { line ->
                    val parts = line.split(',')
                    if (parts.size != 2) null else {
                        val lat = parts[0].toDoubleOrNull()
                        val lon = parts[1].toDoubleOrNull()
                        if (lat != null && lon != null && lat.isFinite() && lon.isFinite()
                            && lat in -90.0..90.0 && lon in -180.0..180.0
                        ) LatLng(lat, lon) else null
                    }
                }.toList()
            }
        }.getOrDefault(emptyList())
        val distance = RouteMath.totalMeters(points)
        val pausedDuration = p.getLong("pausedDurationMs", 0L).coerceAtLeast(0L)
        val pausedAt = p.getLong("pausedAtMs", 0L).takeIf { it > 0L }
        val seconds = ((System.currentTimeMillis() - start - pausedDuration).coerceAtLeast(0L)) / 1000.0
        _state.value = State(true, start, sport, points, distance,
            RouteMath.paceSecPerKm(distance, seconds), pausedAt != null, pausedAt, pausedDuration)
    }

    private fun persistMetadata(s: State) {
        prefs?.edit()
            ?.putBoolean("active", s.active)
            ?.putLong("startMs", s.startMs)
            ?.putString("sportName", s.sportName)
            ?.putLong("pausedAtMs", s.pausedAtMs ?: 0L)
            ?.putLong("pausedDurationMs", s.pausedDurationMs)
            ?.commit()
    }

    private fun clearPersistedSession() {
        prefs?.edit()?.clear()?.commit()
        runCatching { routeFile?.delete() }
    }

    private val _state = MutableStateFlow(State())
    /** The live route, observed by the UI (via AppViewModel) and the service's collect-gate. */
    val state: StateFlow<State> = _state.asStateFlow()

    /** Workouts & GPS test mode (Test Centre): the tagged sink for the .workouts GPS-fix lines, wired by
     *  [com.otidotid.alke.ble.WhoopConnectionService] (which holds the BLE client + the gate). Default null (inert) so
     *  the route fold is byte-identical when the mode is off. The service ALWAYS checks the WORKOUTS gate
     *  before setting this, so [append] pays nothing extra when off. Diagnostic only - it never changes the
     *  route. The Android LocationTracker pre-filters UPSTREAM, so every appended fix is already ACCEPTED and
     *  the raw pre-filter count is not available at this seam; the gps line passes rawFixes = null (reads
     *  `n/a`) rather than imply an accept rate the platform never measured (the macOS recorder, which sees
     *  the raw stream, passes a real count). L4. */
    var workoutsLog: ((String) -> Unit)? = null

    /** Begin a route for [sportName]'s workout started at [startMs]. A re-arm just resets the track. */
    @Synchronized
    fun start(startMs: Long, sportName: String) {
        runCatching { routeFile?.writeText("") }
        val fresh = State(active = true, startMs = startMs, sportName = sportName)
        persistMetadata(fresh)
        _state.value = fresh
    }

    /** Fold one accepted fix into the route, recomputing distance + pace. No-op when not active. */
    @Synchronized
    fun append(pt: LatLng) {
        val s = _state.value
        if (!s.active || s.paused) return
        if (!pt.lat.isFinite() || !pt.lon.isFinite()
            || pt.lat !in -90.0..90.0 || pt.lon !in -180.0..180.0
        ) return
        // Append one coordinate rather than rewriting the entire track for every fix.
        // Recovery ignores a torn final line.
        runCatching {
            routeFile?.appendText(String.format(Locale.ROOT, "%.7f,%.7f\n", pt.lat, pt.lon))
        }
        val track = s.track + pt
        val dist = RouteMath.totalMeters(track)
        val secs = (System.currentTimeMillis() - s.startMs - s.pausedDurationMs) / 1000.0
        _state.value = s.copy(track = track, distanceM = dist, paceSecPerKm = RouteMath.paceSecPerKm(dist, secs))
        // Workouts & GPS test mode: one GPS-fix-progress line per accepted fix, only when the service wired a
        // sink (the WORKOUTS gate was on). The LocationTracker pre-filters UPSTREAM, so the raw pre-filter
        // count is not available at this seam (every fix here is already accepted). Pass rawFixes = null so
        // the line reads `rawFixes=n/a` instead of `rawFixes == accepted`, which would falsely imply a 100%
        // accept rate the platform never actually measured (macOS, which sees the raw stream, passes a real
        // count). L4.
        workoutsLog?.invoke(
            com.otidotid.alke.analytics.WorkoutsTrace.gpsLine(
                rawFixes = null, acceptedPoints = track.size, distanceM = dist,
            ),
        )
    }

    /** End the route and clear it. Returns the final accumulated track for the saved WorkoutRow. */
    @Synchronized
    fun stop(): List<LatLng> {
        val track = _state.value.track
        _state.value = State()
        clearPersistedSession()
        return track
    }

    @Synchronized
    fun pause() {
        val s = _state.value
        if (s.active && !s.paused) {
            val next = s.copy(paused = true, pausedAtMs = System.currentTimeMillis())
            persistMetadata(next)
            _state.value = next
        }
    }

    @Synchronized
    fun resume() {
        val s = _state.value
        if (s.active && s.paused) {
            val added = s.pausedAtMs?.let { System.currentTimeMillis() - it } ?: 0L
            val next = s.copy(paused = false, pausedAtMs = null,
                pausedDurationMs = s.pausedDurationMs + added)
            persistMetadata(next)
            _state.value = next
        }
    }
}
