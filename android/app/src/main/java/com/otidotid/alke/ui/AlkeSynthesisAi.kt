package com.otidotid.alke.ui

import android.content.Context
import com.otidotid.alke.ai.AiCoach
import com.otidotid.alke.ai.AiKeyStore
import com.otidotid.alke.ai.ChatMsg
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared bridge from the original ALKE Synthesis signals to ALKE's opt-in AI Coach.
 *
 * Does not score or modify health data. The existing Synthesis rule remains the offline
 * fallback. Health signals are sent to the configured provider ONLY with explicit consent,
 * Coach enabled, and signals permission. Same-day identical inputs reuse the stored result.
 */
internal object AlkeSynthesisAi {
    private const val PREFS = "alke_synthesis_ai"
    private const val KEY_SIGNATURE = "alke_signature"
    private const val KEY_TEXT = "alke_last_valid"

    fun clean(text: String): String =
        text.replace(Regex("""[*_#`>]"""), "")
            .replace(Regex("""(?i)this advice is informational[^.]*\."""), "")
            .replace(Regex("""(?i)this is not medical advice[^.]*\."""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    suspend fun generate(
        context: Context,
        vm: AppViewModel,
        dayKey: String,
        charge: Double?,
        effort: Double?,
        rest: Double?,
    ): String? {
        if (!AiKeyStore.readConsent(context)) return null
        if (!AlkePrefs.coachEnabled(context) || !AlkePrefs.coachSignals(context)) return null
        if (listOf(charge, effort, rest).none { it?.isFinite() == true }) return null

        val signature = listOf(
            dayKey,
            charge?.takeIf(Double::isFinite)?.roundToInt()?.toString() ?: "-",
            effort?.takeIf(Double::isFinite)?.roundToInt()?.toString() ?: "-",
            rest?.takeIf(Double::isFinite)?.roundToInt()?.toString() ?: "-",
        ).joinToString("|")
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SIGNATURE, null) == signature) {
            return prefs.getString(KEY_TEXT, null)?.takeIf { it.isNotBlank() }
        }

        val prompt = buildString {
            append("Create a concise ALKE Today's Focus insight from the latest Synthesis health recap. ")
            append("Charge=")
            append(charge?.takeIf(Double::isFinite)?.roundToInt() ?: "unavailable")
            append(", Effort=")
            append(effort?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.1f", it) } ?: "unavailable")
            append(", Rest=")
            append(rest?.takeIf(Double::isFinite)?.roundToInt() ?: "unavailable")
            append(". Use the current health context available with this request. ")
            append("Give one evidence-grounded focus and one practical action in 2 to 4 sentences. ")
            append("Do not diagnose, invent readings, add headings, bullets, or Markdown.")
        }
        val answer = AiCoach(vm.repo, activeStrapId = { vm.activeStrapId }).chatCloud(
            ctx = context,
            history = listOf(ChatMsg(role = "user", text = prompt)),
            conversationId = null,
            consent = true,
            includeSignals = true,
        ).answer
        val cleaned = clean(answer).takeIf { it.isNotBlank() } ?: return null
        prefs.edit().putString(KEY_SIGNATURE, signature).putString(KEY_TEXT, cleaned).apply()
        return cleaned
    }
}
