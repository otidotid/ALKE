package com.otidotid.alke.ai

import com.otidotid.alke.BuildConfig
import com.otidotid.alke.data.GravitySample
import com.otidotid.alke.data.HrSample
import com.otidotid.alke.data.StepSample
import com.otidotid.alke.analytics.StepsCounter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Transport and numeric snapshot compression only. No local classifier or fallback. */
internal object CloudActivityProtocol {
    const val SCHEMA = "activity-detection-v1"
    const val WINDOW = 900L
    const val BIN = 30L
    const val WORKOUT_SOURCE = "cloud-ai-alke"
    const val DAILY_SOURCE = "alke-cloud-activity"
    const val DAILY_KIND = "ALKE_AI_ACTIVITY"
    const val DISMISSED_KIND = "ALKE_AI_ACTIVITY_DISMISSED"
    val types = mapOf("WALKING" to "Walking", "RUNNING" to "Running",
        "CLIMBING_STAIRS" to "Climbing stairs", "CYCLING" to "Cycling",
        "STRENGTH_TRAINING" to "Strength training", "HIIT" to "HIIT", "WORKOUT" to "Workout")
    data class Event(val id: String, val type: String, val start: Long, val end: Long, val workout: Boolean) {
        val sport: String get() = types.getValue(type)
    }
    data class Snapshot(val start: Long, val bins: List<List<Number?>>, val restingHr: Int?,
        val excluded: List<Pair<Long, Long>>) {
        val end get() = start + bins.size * BIN
        fun body(): JSONObject = JSONObject().apply {
            put("schemaVersion", SCHEMA)
            put("activityConsent", true)
            put("startTs", start)
            put("restingHr", restingHr?.takeIf { it in 30..120 } ?: JSONObject.NULL)
            put("bins", JSONArray(bins.map { b -> JSONArray(b.map { it ?: JSONObject.NULL }) }))
            put("excludedIntervals", JSONArray(excluded.map { JSONArray(listOf(it.first, it.second)) }))
            put("requestId", sha256(toString()))
        }
    }
    data class Response(val status: String, val events: List<Event>)
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun overlap(a: Long, b: Long, c: Long, d: Long): Boolean = a < d && c < b
    fun excludes(spans: List<Pair<Long, Long>>, from: Long, to: Long): List<Pair<Long, Long>> {
        val result = ArrayList<Pair<Long, Long>>()
        for ((a,b) in spans.filter { it.first < it.second && overlap(it.first,it.second,from,to) }
            .map { maxOf(it.first,from) to minOf(it.second,to) }.sortedBy { it.first }) {
            val prior = result.lastOrNull()
            if (prior != null && a <= prior.second) result[result.lastIndex] = prior.first to maxOf(prior.second,b)
            else result.add(a to b)
        }
        // Fail closed rather than omit known exclusion intervals if pathological input exceeds protocol.
        return if (result.size <= 64) result else listOf(from to to)
    }
    fun bins(start: Long, hr: List<HrSample>, motion: List<GravitySample>, steps: List<StepSample>,
        ticksPerStep: Double): List<List<Number?>> {
        val h = hr.filter { it.bpm in 25..250 }.distinctBy { it.ts }.groupBy { (it.ts-start)/BIN }
        val ordered = motion.sortedBy { it.ts }.distinctBy { it.ts }
        val m = ArrayList<Pair<Long, Double>>()
        ordered.forEachIndexed { i, row ->
            // Native dynamic acceleration is preferred. The legacy 1 Hz gravity vector delta is a
            // motion-magnitude proxy in g, not a reconstructed high-rate accelerometer waveform.
            val prev = ordered.getOrNull(i-1)
            val v = row.dynAccel ?: prev?.takeIf { row.ts-it.ts == 1L && row.deviceId == it.deviceId }?.let {
                sqrt((row.x-it.x)*(row.x-it.x)+(row.y-it.y)*(row.y-it.y)+(row.z-it.z)*(row.z-it.z))
            }
            if (v != null && v.isFinite() && v in 0.0..20.0 && row.ts in start until start+WINDOW) m.add(row.ts to v)
        }
        val motionBins = m.groupBy { (it.first-start)/BIN }
        val s = steps.filter { it.ts in start-1 until start+WINDOW }.sortedBy { it.ts }.distinctBy { it.ts }
        return List(30) { i ->
            val t = start + i*BIN
            val hs = h[i.toLong()].orEmpty().filter { it.ts in t until t+BIN }
            val ms = motionBins[i.toLong()].orEmpty()
            val stepRows = s.filter { it.ts in t-1 until t+BIN }
            val stepCount = if (ticksPerStep.isFinite() && ticksPerStep >= 0.5 &&
                stepRows.count { it.ts >= t } >= 21) {
                StepsCounter.stepsInWindow(stepRows)?.let { (it/ticksPerStep).roundToInt().takeIf { n -> n in 0..150 } }
                // A zero-tick/unknown counter result is not promoted to a measured zero.
            } else null
            listOf(hs.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.let { rounded(it) },
                ms.takeIf { it.isNotEmpty() }?.let { rounded(sqrt(it.map { p -> p.second*p.second }.average())) },
                stepCount, null, rounded(hs.size/30.0), rounded(ms.size/30.0))
        }
    }
    private fun rounded(v: Double): Double = kotlin.math.round(v*1000.0)/1000.0
    fun usable(b: List<Number?>): Boolean = b[1] != null && (b[5]?.toDouble() ?: 0.0) >= 0.7 &&
        ((b[0] != null && (b[4]?.toDouble() ?: 0.0) >= 0.7) || b[2] != null)
    fun parse(raw: String, expected: Snapshot, requestId: String): Response? = runCatching {
        val obj = JSONObject(raw).getJSONObject("activityDetection")
        require(obj.getString("schemaVersion") == SCHEMA && obj.getString("requestId") == requestId)
        require(obj.getLong("startTs") == expected.start && obj.getLong("endTs") == expected.end)
        require(obj.getString("model") in setOf("openai/gpt-oss-20b","openai/gpt-oss-120b"))
        val status = obj.getString("status")
        require(status in setOf("COMPLETE","INSUFFICIENT_DATA"))
        val arr = obj.getJSONArray("activities")
        require(arr.length() <= 10 && (status == "COMPLETE" || arr.length() == 0))
        val events = (0 until arr.length()).map { i ->
            val v = arr.getJSONObject(i)
            val type = v.getString("type")
            val a = v.getLong("startTs")
            val b = v.getLong("endTs")
            val kind = v.getString("kind")
            val workout = type !in setOf("WALKING","CLIMBING_STAIRS")
            require(type in types && v.getString("confidence") == "HIGH" && v.getString("source") == "cloud-ai")
            require(kind == if (workout) "WORKOUT" else "ACTIVITY")
            require(a >= expected.start && b <= expected.end && b > a && a % BIN == 0L && b % BIN == 0L)
            val expectedId = sha256("$SCHEMA:$a:$b:$type")
            require(v.getString("id") == expectedId)
            require(expected.excluded.none { overlap(a,b,it.first,it.second) })
            val slice = expected.bins.subList(((a-expected.start)/BIN).toInt(),((b-expected.start)/BIN).toInt())
            require(slice.all(::usable))
            val minBins = when(type) { "CLIMBING_STAIRS" -> 2; "WALKING","RUNNING" -> 3; else -> 10 }
            require(slice.size >= minBins)
            if (type == "CLIMBING_STAIRS") require(slice.count { it[3] != null } >= kotlin.math.ceil(slice.size*0.8).toInt())
            Event(expectedId,type,a,b,workout)
        }.sortedBy { it.start }
        require(events.zipWithNext().none { (a,b) -> a.end > b.start })
        Response(status,events)
    }.getOrNull()
}

internal class CloudActivityClient(
    private val base: String = BuildConfig.CLOUD_COACH_BASE_URL,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS)
        .readTimeout(30,TimeUnit.SECONDS).writeTimeout(8,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
) {
    data class Outcome(val response: CloudActivityProtocol.Response? = null, val code: Int = 0)
    suspend fun detect(snapshot: CloudActivityProtocol.Snapshot, token: String): Outcome = withContext(Dispatchers.IO) {
        val url = (base.trimEnd('/') + "/v1/activity/detect").toHttpUrlOrNull()
        if (url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || token.length < 32) return@withContext Outcome(code = 400)
        try {
            val body = snapshot.body()
            val req = Request.Builder().url(url).header("Authorization","Bearer $token")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use Outcome(code = res.code)
                Outcome(CloudActivityProtocol.parse(res.body?.string().orEmpty(),snapshot,body.getString("requestId")),res.code)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Outcome(code = 503) }
    }
}
