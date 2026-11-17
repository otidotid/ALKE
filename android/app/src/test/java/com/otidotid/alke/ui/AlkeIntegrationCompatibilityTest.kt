package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards rebrand-critical integration contracts. We intentionally retain internal
 * ALKE/ALKE class names, saved preference keys and cloud metadata identifiers:
 * those are persistence/protocol contracts, not the ALKE product name shown to users.
 */
class AlkeIntegrationCompatibilityTest {
    private fun source(relative: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val options = listOf(
            File(cwd, relative),
            File(cwd, "app/$relative"),
            File(cwd, "android/app/$relative"),
            File(cwd, "../$relative"),
            File(cwd, "../app/$relative"),
        )
        val resolved = options.firstOrNull { it.isFile }
            ?: error("ALKE compatibility source missing: $relative from ${cwd.absolutePath}")
        return resolved.readText()
    }

    @Test fun demoAndNormalApplicationIdsRemainIsolated() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("applicationId = \"com.otidotid.alke\""))
        assertTrue(gradle.contains("namespace = \"com.otidotid.alke\""))
        assertTrue(gradle.contains("applicationIdSuffix = \".demo\""))
        assertTrue(gradle.contains("applicationIdSuffix = \".debug\""))
        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"true\")"))
        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"ENABLE_DEMO\", \"false\")"))
        val activity = source("src/main/java/com/otidotid/alke/ui/MainActivity.kt")
        assertTrue(activity.contains("if (BuildConfig.ENABLE_DEMO)"))
        assertTrue(activity.contains("DemoSeeder.seedIfEmpty"))
    }

    @Test fun manifestComponentsAndProviderStillResolveWithLegacyCodeNamespace() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:name=\".AlkeApplication\""))
        assertTrue(manifest.contains("android:name=\"com.otidotid.alke.ui.MainActivity\""))
        assertTrue(manifest.contains("android:authorities=\"${'$'}{applicationId}.fileprovider\""))
        assertTrue(manifest.contains("com.otidotid.alke.ble.WhoopConnectionService"))
        // The rebranded manifest must point to the existing ALKE activity.
        assertTrue(manifest.contains("com.otidotid.alke.ui.MainActivity"))
    }

    @Test fun existingWorkManagerAndBackupIdentifiersAreNeverBlindlyRenamed() {
        val googleWorker = source("src/main/java/com/otidotid/alke/data/GoogleCloudBackupScheduler.kt")
        assertTrue(googleWorker.contains("class GoogleCloudBackupWorker"))
        assertTrue(googleWorker.contains("\"alke-google-cloud-backup\""))
        assertTrue(googleWorker.contains("\"alke_google_cloud_backup\""))
        assertTrue(googleWorker.contains("Open ALKE and re-authorize"))
        val push = source("src/main/java/com/otidotid/alke/push/SelfHostedPushScheduler.kt")
        assertTrue(push.contains("\"self-hosted-health-push\""))
        val aiKeys = source("src/main/java/com/otidotid/alke/ai/AiKeyStore.kt")
        assertTrue(aiKeys.contains("\"alke_ai_secure_prefs\""))
    }

    @Test fun googleAndAiAllowAlkeConfigAndExistingLegacyConfig() {
        val gradle = source("build.gradle.kts")
        for (key in listOf(
            "ALKE_GOOGLE_WEB_CLIENT_ID", "ALKE_GOOGLE_WEB_CLIENT_ID",
            "ALKE_CLOUD_COACH_URL", "ALKE_CLOUD_COACH_URL",
        )) assertTrue("Missing config: $key", gradle.contains(key))
        val signin = source("src/main/java/com/otidotid/alke/ui/GoogleAccountSettings.kt")
        // Google OAuth can now be configured at runtime for a CI APK without a compiled-in ID.
        // The Gradle config key remains supported (asserted above); the UI controller should
        // read validated runtime configuration and retain the original BuildConfig fallback.
        assertTrue(signin.contains("GoogleOAuthClientConfig.current(appContext)"))
        assertTrue(signin.contains("BuildConfig.GOOGLE_WEB_CLIENT_ID"))
        assertTrue(signin.contains("isValidGoogleWebClientId"))
        val cloud = source("src/main/java/com/otidotid/alke/ai/CloudCoachClient.kt")
        assertTrue(cloud.contains("ALKE Cloud Coach"))
        assertTrue(cloud.contains("BuildConfig.CLOUD_COACH_BASE_URL"))
        val coach = source("src/main/java/com/otidotid/alke/ai/AiCoach.kt")
        assertTrue(coach.contains("ALKE AGE AND BODY-COMPOSITION CONTEXT"))
    }

    @Test fun newDriveBackupNamesAndOldEncryptedBackupsRemainReadable() {
        val drive = source("src/main/java/com/otidotid/alke/data/GoogleDriveBackupClient.kt")
        assertTrue(drive.contains("alke-backup-"))
        assertTrue(drive.contains("alkebak"))
        assertTrue(drive.contains("alke-archive-"))
        assertTrue(drive.contains("alkeenc"))
        assertTrue(drive.contains("endsWith(\".alkeenc\")"))
        assertTrue(drive.contains("endsWith(\".alkeenc\")"))
        // Existing appDataFolder lookup and encryption descriptors are protocol IDs.
        assertTrue(drive.contains("alke_full_backup_v1"))
        assertTrue(drive.contains("alke_monthly_archive_v1"))
        assertTrue(drive.contains("ALKEENC-v1"))
    }

    @Test fun demoSeedsTimelineWithNoFutureOrRealSamples() {
        val seeder = source("src/main/java/com/otidotid/alke/data/DemoSeeder.kt")
        assertTrue(seeder.contains("private const val DAYS = 120"))
        assertTrue(seeder.contains("seedRecentHeartRateIfMissing(repo)"))
        assertTrue(seeder.contains("if (ts > now) break"))
        assertTrue(seeder.contains("repo.hrSamplesForDevice"))
        assertTrue(seeder.contains("repo.insertHr(samples)"))
    }
}
