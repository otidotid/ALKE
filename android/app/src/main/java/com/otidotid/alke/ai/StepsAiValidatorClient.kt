package com.otidotid.alke.ai

import com.otidotid.alke.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional post-validation for an existing ALKE Steps result.
 * The local StepsFusionProduction result remains authoritative. Any network, schema, confidence, or
 * range failure returns KEEP with the raw value unchanged.
 */
class StepsAiValidatorClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = BuildConfig.CLOUD_COACH_BASE_URL.trim().trimEnd('/'),
) {
    enum class Status { KEEP, ADJUST, PARTIAL }

    data class Context(
        val day: String,
        val rawSteps: Int,
        val motionVolume: Double,
        val walkMinutes: Double,
        val runMinutes: Double,
        val stillMinutes: Double,
        val workoutType: String,
        val workoutMinutes: Double,
        val meanHeartRate: Double?,
        val sensorCoverage: Double,
        val recentDailySteps: List<Int>,
        val algorithmConfidence: Double,
    )

    data class Result(
        val rawSteps: Int,
        val finalSteps: Int,
        val status: Status,
        val factor: Double,
        val confidence: Double,
        val reason: String,
        val model: String,
        val remoteAccepted: Boolean,
    ) {
        companion object {
            fun keep(rawSteps: Int, reason: String, model: String = "local-fallback") = Result(
                rawSteps = rawSteps,
                finalSteps = rawSteps,
                status = Status.KEEP,
                factor = 1.0,
                confidence = 0.0,
                reason = reason,
                model = model,
                remoteAccepted = false,
            )
        }
    }

    suspend fun validate(context: Context): Result = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || !baseUrl.startsWith("https://")) {
            return@withContext Result.keep(context.rawSteps, "Cloud validator is not configured")
        }
        runCatching {
            val requestJson = JSONObject().apply {
                put("schemaVersion", "steps-validator-v1")
                put("day", context.day)
                put("rawSteps", context.rawSteps.coerceIn(0, 100_000))
                put("motionVolume", context.motionVolume.coerceAtLeast(0.0))
                put("walkMinutes", context.walkMinutes.coerceIn(0.0, 1440.0))
                put("runMinutes", context.runMinutes.coerceIn(0.0, 1440.0))
                put("stillMinutes", context.stillMinutes.coerceIn(0.0, 1440.0))
                put("workoutType", context.workoutType.take(80))
                put("workoutMinutes", context.workoutMinutes.coerceIn(0.0, 1440.0))
                put("meanHeartRate", context.meanHeartRate?.coerceIn(30.0, 240.0) ?: JSONObject.NULL)
                put("sensorCoverage", context.sensorCoverage.coerceIn(0.0, 1.0))
                put("recentDailySteps", JSONArray(context.recentDailySteps.takeLast(14).map { it.coerceIn(0, 100_000) }))
                put("algorithmConfidence", context.algorithmConfidence.coerceIn(0.0, 1.0))
            }
            val request = Request.Builder()
                .url("$baseUrl/v1/steps/validate")
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                parse(response.body?.string().orEmpty(), context.rawSteps)
                    ?: error("Invalid steps validator response")
            }
        }.getOrElse {
            Result.keep(context.rawSteps, "AI validation unavailable; local steps retained")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun parse(json: String, expectedRawSteps: Int): Result? {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val value = root.optJSONObject("stepsValidation") ?: return null
            if (value.optString("schemaVersion") != "steps-validator-v1") return null
            if (value.optInt("rawSteps", -1) != expectedRawSteps) return null
            val status = runCatching { Status.valueOf(value.optString("status")) }.getOrNull() ?: return null
            val factor = value.optDouble("factor", Double.NaN)
            val confidence = value.optDouble("confidence", Double.NaN)
            val finalSteps = value.optInt("finalSteps", -1)
            val reason = value.optString("reason").trim()
            val model = value.optString("model").trim()
            if (!factor.isFinite() || factor !in 0.70..1.15 || !confidence.isFinite() || confidence !in 0.0..1.0) return null
            if (reason.isBlank() || reason.length > 240 || finalSteps !in 0..100_000) return null
            if ((status == Status.KEEP || status == Status.PARTIAL) && factor != 1.0) return null
            if ((status == Status.KEEP || status == Status.PARTIAL) && finalSteps != expectedRawSteps) return null
            if (status == Status.ADJUST) {
                if (confidence < 0.80) return null
                val expectedFinal = kotlin.math.round(expectedRawSteps * factor).toInt().coerceAtLeast(0)
                if (finalSteps != expectedFinal) return null
            }
            // Cloudflare can return a valid KEEP/PARTIAL envelope without ever calling the AI model.
            // Such responses must remain retryable after AI is enabled or an outage resolves.
            val validatedByAi = model == "openai/gpt-oss-20b"
            return Result(expectedRawSteps, finalSteps, status, factor, confidence, reason, model, remoteAccepted = validatedByAi)
        }
    }
}
