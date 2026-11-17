package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guard the single production Coach route without requiring a connected device. */
class AlkeManagedCoachRoutingTest {
    private fun read(path: String): String {
        val root = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(root, path),
            File(root, "app/$path"),
            File(root, "android/app/$path"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Missing source: $path")
    }

    @Test fun screenAlwaysShowsConversationWithoutProviderSetup() {
        val screen = read("src/main/java/com/otidotid/alke/ui/CoachScreen.kt")
        val entry = screen.substringAfter("fun CoachScreen(").substringBefore("// The provider/API-key setup")
        assertTrue(entry.contains("CoachChat("))
        assertFalse(entry.contains("if (configured)"))
        assertFalse(screen.contains("private fun CoachSetup("))
        assertFalse(screen.contains("Connect a provider"))
        assertFalse(screen.contains("AI provider configured"))
    }

    @Test fun viewModelUsesExistingCloudTransportAndConsent() {
        val vm = read("src/main/java/com/otidotid/alke/ui/CoachViewModel.kt")
        val send = vm.substringAfter("fun send(ctx: Context, text: String)")
            .substringBefore("fun dismissNutritionEstimate(")
        assertTrue(send.contains("aiCoach.chatCloud("))
        assertFalse(send.contains("aiCoach.chatStream("))
        assertTrue(send.contains("consent = consentNow"))
        val client = read("src/main/java/com/otidotid/alke/ai/CloudCoachClient.kt")
        assertTrue(client.contains("BuildConfig.CLOUD_COACH_BASE_URL"))
        assertTrue(client.contains("/v1/coach/chat"))
        assertTrue(client.contains("/v1/labs/interpret"))
        assertTrue(client.contains("/v1/labs/metabolic"))
    }
}
