package com.otidotid.alke.data

import com.otidotid.alke.ai.CloudActivityProtocol
import com.otidotid.alke.ai.CloudSleepProtocol
import org.json.JSONObject

/** Immutable detection history is kept separately from editable sessions and sensor streams. */
internal class CloudSleepLedger(
    private val dao: WhoopDao,
    private val transaction: WhoopRepository.Transactor,
) {
    private fun source(id: String) = "alke-sleep-ai/$id"

    data class Mode(val time: Long, val enabled: Boolean)
    data class History(val modes: List<Mode>, val episodes: List<CloudSleepProtocol.Episode>) {
        fun owns(start: Long): Boolean = modes.lastOrNull { it.time <= start }?.enabled == true
        fun covers(from: Long, to: Long): Boolean =
            owns(from) && modes.none { it.time > from && it.time < to && !it.enabled }
        fun touches(from: Long, to: Long): Boolean =
            owns(from) || modes.any { it.enabled && it.time in from until to }
    }

    suspend fun configure(id: String, enabled: Boolean, now: Long) {
        require(id.isNotBlank())
        val changed = transaction.run {
            if (modes(id).lastOrNull()?.enabled == enabled) return@run false
            dao.upsertSessionCorrection(EventRow(source(id), now, MODE, enabled.toString(), synced = 1))
            true
        }
        if (changed) SleepDataRevision.changed()
    }

    private suspend fun modes(id: String) = dao.eventsByKind(source(id), MODE, 0, Long.MAX_VALUE, Int.MAX_VALUE)
        .map { Mode(it.ts, it.payloadJSON == "true") }

    suspend fun history(id: String, from: Long, to: Long): History {
        val modes = modes(id)
        if (modes.isEmpty()) return History(emptyList(), emptyList())
        val rows = dao.eventsByKind(source(id), EPISODE, (from - DAY).coerceAtLeast(0), to, Int.MAX_VALUE)
        val episodes = rows.mapNotNull { row ->
            val json = runCatching { JSONObject(row.payloadJSON) }.getOrNull() ?: return@mapNotNull null
            val type = json.optString("type")
            val end = json.optLong("end")
            if (type !in CloudSleepProtocol.types || end <= from || end <= row.ts) return@mapNotNull null
            CloudSleepProtocol.Episode(json.optString("id"), type, row.ts, end)
        }
        return History(modes, episodes)
    }

    suspend fun exclusions(id: String, from: Long, to: Long): List<Pair<Long,Long>> {
        val workouts = dao.workoutsOverlapping(from, to).filter {
            it.source != CloudActivityProtocol.WORKOUT_SOURCE && it.source != "auto-detected"
        }.map { it.startTs to it.endTs }
        val recorded = dao.sleepSessionsOverlapping(from, to).filter {
            it.userEdited || !it.deviceId.endsWith("-alke")
        }.map { it.effectiveStartTs to it.endTs }
        val removed = dao.dismissedSleeps(id).map { it.startTs to it.endTs } +
            dao.dismissedSleeps("my-whoop").map { it.startTs to it.endTs } +
            dao.dismissedSleeps("$id-alke").map { it.startTs to it.endTs } +
            dao.dismissedSleeps("my-whoop-alke").map { it.startTs to it.endTs }
        return CloudActivityProtocol.excludes(workouts + recorded + removed, from, to)
    }

    suspend fun save(id: String, snapshot: CloudSleepProtocol.Snapshot, response: CloudSleepProtocol.Response): Int {
        if (response.episodes.isEmpty()) return 0
        val added = transaction.run {
            val history = history(id, snapshot.start, snapshot.end)
            if (history.modes.lastOrNull()?.enabled != true) return@run 0
            val reserved = exclusions(id, snapshot.start, snapshot.end)
            val stored = history.episodes.toMutableList()
            val rows = response.episodes.mapNotNull { episode ->
                if (!history.covers(episode.start, episode.end) ||
                    reserved.any { (a,b) -> a < episode.end && episode.start < b }) return@mapNotNull null
                val conflicts = stored.filter { it.start < episode.end && episode.start < it.end }
                if (conflicts.isNotEmpty()) {
                    // A later complete main sleep may enclose an earlier provisional nap.
                    // Protect edited sessions, previous main sleeps and ambiguous partial overlap.
                    if (episode.type != "MAIN_SLEEP" || conflicts.any { it.type == "MAIN_SLEEP" ||
                            it.start < episode.start || it.end > episode.end }) return@mapNotNull null
                    for (old in conflicts) {
                        dao.removeCloudSleepEstimate(source(id), old.start)
                        val derived = dao.sleepSession("$id-alke", old.start)
                        if (derived != null && !derived.userEdited && derived.endTs == old.end)
                            dao.deleteSleepSession(derived.deviceId, derived.startTs)
                    }
                    stored.removeAll(conflicts.toSet())
                }
                stored += episode
                val payload = JSONObject().put("id",episode.id).put("type",episode.type).put("end",episode.end)
                    .put("schema",CloudSleepProtocol.SCHEMA).put("source","cloud-ai")
                EventRow(source(id), episode.start, EPISODE, payload.toString(), synced = 1)
            }
            if (rows.isEmpty()) 0 else dao.insertEvents(rows).count { it != -1L }
        }
        if (added > 0) SleepDataRevision.changed()
        return added
    }

    companion object {
        const val DAY = 86400L
        private const val MODE = "sleep-ai-mode-v1"
        private const val EPISODE = "sleep-ai-episode-v1"
    }
}
