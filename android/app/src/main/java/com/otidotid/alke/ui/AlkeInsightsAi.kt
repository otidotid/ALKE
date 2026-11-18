package com.otidotid.alke.ui

import android.content.Context
import com.otidotid.alke.ai.AiKeyStore
import com.otidotid.alke.ai.CloudCoachClient
import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * Exactly the physiological evidence visible on the ALKE Insights screen.
 * No repository, body-age, workout, lab, nutrition or Today-Synthesis references.
 */
internal data class AlkeInsightsEvidence(
    val dayKey: String?,
    val recovery: Double?,
    val hrvMs: Double?,
    val restingHrBpm: Int?,
    val skinAbsoluteC: Double?,
    val skinDeviationC: Double?,
) {
    private fun valid(value: Double?): Double? = value?.takeIf { it.isFinite() }

    fun hasEvidence(): Boolean =
        listOf(recovery, hrvMs, restingHrBpm?.toDouble(), skinAbsoluteC, skinDeviationC)
            .any { valid(it) != null }

    /** Whitelist of Insights-visible metrics. Used as the ONLY cloud healthContext. */
    fun context(): String = buildList {
        add("ALKE Insights evidence for day " + (dayKey ?: "not recorded"))
        valid(recovery)?.let { add(String.format(Locale.US, "Recovery: %.1f%%", it)) }
        valid(hrvMs)?.let { add(String.format(Locale.US, "HRV: %.1f ms", it)) }
        restingHrBpm?.takeIf { it > 0 }?.let { add("Resting HR: " + it + " bpm") }
        valid(skinAbsoluteC)?.let { add(String.format(Locale.US, "Skin temperature: %.1f C", it)) }
        valid(skinDeviationC)?.let { add(String.format(Locale.US, "Skin temperature deviation: %+.1f C", it)) }
    }.joinToString("\n")
}

/**
 * Isolated cloud interpretation of Insight-visible evidence, not a score engine.
 * Uses an ephemeral server identity so the Coach's other-module memory cannot be
 * retrieved. No direct repository or Labs access; Today Synthesis remains unchanged.
 */
internal object AlkeInsightsAi {
    private const val PREFS = "alke_insights_ai"
    private const val KEY_EVIDENCE = "evidence"
    private const val KEY_SUMMARY = "summary"

    suspend fun generate(context: Context, evidence: AlkeInsightsEvidence): String? {
        if (!evidence.hasEvidence()) return null
        if (!AiKeyStore.readConsent(context) ||
            !AlkePrefs.coachEnabled(context) ||
            !AlkePrefs.coachSignals(context)) return null

        val allowed = evidence.context()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_EVIDENCE, null) == allowed) {
            return prefs.getString(KEY_SUMMARY, null)?.takeIf { it.isNotBlank() }
        }
        val cloud = CloudCoachClient()
        if (!cloud.configured) return null

        val prompt = "Produce Your Insight Summary based exclusively on the Insights evidence block. " +
            "Explain the current signals, any relationship actually supported by the provided metrics, " +
            "and one measured conclusion. Missing evidence must be declared unavailable. " +
            "Write 2 to 4 concise sentences without headings, bullets, diagnoses or invented facts."
        val rules = "You are the isolated ALKE Insights evidence summarizer. " +
            "The supplied healthContext is your entire allowed factual universe. " +
            "Never retrieve, infer or use other app modules, history, prior conversations, " +
            "user memory, Labs, Fitness Age, Coach, nutrition or Today Synthesis. " +
            "No cross-metric causal claim without evidence; never assign a fitness-age number."

        val answer = try {
            cloud.ask(
                context = context,
                conversationId = null,
                question = prompt,
                recentMessages = emptyList(),
                healthContext = allowed,
                systemPrompt = rules,
                isolatedContext = true,
            ).answer
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        val cleaned = AlkeSynthesisAi.clean(answer).take(500).takeIf { it.isNotBlank() }
            ?: return null
        prefs.edit().putString(KEY_EVIDENCE, allowed).putString(KEY_SUMMARY, cleaned).apply()
        return cleaned
    }
}
