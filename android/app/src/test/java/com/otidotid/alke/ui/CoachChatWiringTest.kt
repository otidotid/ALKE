package com.otidotid.alke.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Protects the Android Coach first-run and provider transport wiring.
 * No private credentials or network requests are required.
 */
class CoachChatWiringTest {
    private fun source(path: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(File(cwd, path), File(cwd, "app/$path"),
            File(cwd, "android/app/$path"), File(cwd, "../$path"))
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Coach source missing: $path")
        return file.readText()
    }

    @Test fun unconfiguredCoachShowsRealSetupInsteadOfDeadEnd() {
        val screen = source("src/main/java/com/otidotid/alke/ui/CoachScreen.kt")
        assertTrue(screen.contains("CoachSetup(vm)"))
        assertTrue(screen.contains("vm.keyVersion.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("vm.customConnected.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("vm.isConfigured()"))
        assertTrue(screen.contains("if (configured) CoachHandoff.consume()"))
    }

    @Test fun chatUsesExistingDirectProviderTransportWhenCloudIsAbsent() {
        val vm = source("src/main/java/com/otidotid/alke/ui/CoachViewModel.kt")
        assertTrue(vm.contains("AiKeyStore.read(context, _provider.value) != null"))
        assertTrue(vm.contains("_customConnected.value && _customBaseUrl.value.isNotBlank()"))
        assertTrue(vm.contains("if (usesCloudCoach())"))
        assertTrue(vm.contains("aiCoach.chatCloud("))
        assertTrue(vm.contains("aiCoach.chatStream("))
        assertTrue(vm.contains("provider = _provider.value"))
        assertTrue(vm.contains("consent = consentNow"))
        assertTrue(vm.contains("includeSignals = consentNow && AlkePrefs.coachSignals(appCtx)"))
    }
}
