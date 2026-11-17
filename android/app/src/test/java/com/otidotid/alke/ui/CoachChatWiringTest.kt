package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract: all Coach chat is managed through Cloudflare; no device provider setup. */
class CoachChatWiringTest {
    private fun source(path: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(File(cwd, path), File(cwd, "app/$path"),
            File(cwd, "android/app/$path"), File(cwd, "../$path"))
        return (candidates.firstOrNull(File::isFile)
            ?: error("Coach source missing: $path")).readText()
    }

    @Test fun coachDirectlyOpensCloudChatWithoutProviderOrApiKeySetup() {
        val screen = source("src/main/java/com/otidotid/alke/ui/CoachScreen.kt")
        val route = screen.substringAfter("fun CoachScreen(")
            .substringBefore("// Coach is a managed Cloudflare service:")
        assertTrue(route.contains("CoachChat("))
        assertFalse(route.contains("CoachSetup("))
        assertFalse(route.contains("if (configured)"))
        assertFalse(screen.contains("private fun CoachSetup("))
        assertTrue(screen.contains("Cloudflare AI"))
    }

    @Test fun outboundMessagesUseCloudCoachOnlyAndPreserveExplicitConsent() {
        val vm = source("src/main/java/com/otidotid/alke/ui/CoachViewModel.kt")
        val send = vm.substringAfter("fun send(ctx: Context, text: String)")
            .substringBefore("fun dismissNutritionEstimate()")
        assertTrue(send.contains("aiCoach.chatCloud("))
        assertFalse(send.contains("aiCoach.chatStream("))
        assertTrue(send.contains("consent = consentNow"))
        assertTrue(send.contains("includeSignals = consentNow && AlkePrefs.coachSignals(appCtx)"))
        assertTrue(send.contains("AlkePrefs.coachEnabled(ctx.applicationContext)"))
        val cloud = source("src/main/java/com/otidotid/alke/ai/CloudCoachClient.kt")
        assertTrue(cloud.contains("BuildConfig.CLOUD_COACH_BASE_URL"))
        assertTrue(cloud.contains("/v1/coach/chat"))
        assertTrue(cloud.contains("CloudCoachIdentity.id(context)"))
    }
}
