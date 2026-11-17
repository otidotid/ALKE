package com.otidotid.alke.ui

import com.otidotid.alke.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector

// MARK: - AppChangelog (ported byte-for-byte from Strand/System/AppChangelog.swift)
//
// Single source of truth for the in-app "What's New" sheet and the expectation-setting
// copy used in onboarding. Mirrored from the macOS `AppChangelog` enum so every surface
// — macOS, Android, the repo CHANGELOG.md — tells the exact same story.
//
// Icon mapping (SF Symbol → Material, all verified to resolve in material-icons-extended):
//   flask           → Icons.Outlined.Science        (independent / experimental)
//   checkmark.seal  → Icons.Outlined.VerifiedUser   (the supported path)
//   hourglass       → Icons.Outlined.HourglassEmpty (scores build over time)
//   lock.shield     → Icons.Outlined.Shield         (everything stays on-device)

object AppChangelog {

    /**
     * Bump this when you add a release below. The "What's New" sheet shows automatically when the
     * stored last-seen version is behind this. (Decoupled from the bundle version on purpose.)
     */
    const val CURRENT_VERSION = "1.0.0-alpha.9"

    data class Release(
        val version: String,
        val title: String,
        val date: String,
        val items: List<String>,
    )

    /** Newest first. */
    /**
     * ALKE-only release notes. Historical upstream release notes are available
     * through the immutable pre-lock source snapshot and original licensing,
     * but are not part of ALKE's own public changelog.
     */
    val releases: List<Release> = listOf(
        Release(
            version = "1.0.0-alpha.9",
            title = "Independent ALKE technical identity",
            date = "November 2026",
            items = listOf(
                "Complete technical rebrand including source namespaces, resources, persistent storage identifiers, app help and documents. Old source and rollback points remain on a separate protected backup branch.",
                "Fixed premium navy-and-cyan appearance. Only language and clock-format preferences remain in General.",
                "Retains all analytics engines and hardware logic. Existing installed database and encrypted app credentials do not automatically follow renamed storage keys: export a backup and verify migration before replacing a working install.",
                "Google OAuth, Cloudflare endpoint and AI provider configuration require new ALKE credentials or user-entered keys. Do not embed secrets in APKs.",
                "Normal APK and synthetic demonstration APK use separate Android application IDs.",
            ),
        ),
        Release(
            version = "1.0.0-alpha.8",
            title = "ALKE design lock and migration readiness",
            date = "November 2026",
            items = listOf(
                "ALKE's approved dark navy, cyan and gold design is locked. Appearance, background-image and app-icon customization controls are no longer shown; stored settings and recovery paths are preserved.",
                "Reviewed all main-screen navigation, with Today's Activity ordered Day Strain, Steps, Active Time, and Skin Temperature below Resting HR in Insight.",
                "Cleaned legacy product names from user-visible settings, device help, reports, alerts and the in-app release history.",
                "Google Sign-In and Drive need ALKE-specific OAuth configuration; AI provider credentials are entered securely by the user. Existing local backups remain importable.",
                "Normal and synthetic demo APKs use separate package IDs. Test builds do not include personal health records or API secrets.",
            ),
        ),
        Release(
            version = "1.0.0-alpha.7",
            title = "Today's Activity and focused Train",
            date = "November 2026",
            items = listOf(
                "Today's Activity shows Day Strain, Steps and Active Time in one row.",
                "Train presents Calories, Most Active Sports, Add Workout and the Active Calories calendar. Full workout recording and session management remain available through History.",
                "Skin Temperature is discoverable in Insight, directly below Resting HR.",
            ),
        ),
        Release(
            version = "1.0.0-alpha.6",
            title = "Activity and navigation refinements",
            date = "November 2026",
            items = listOf(
                "Refined activity-centered navigation, workout entry and screen grouping while retaining existing health metrics and algorithms.",
            ),
        ),
        Release(
            version = "1.0.0-alpha.5",
            title = "Heart rate and workout timeline",
            date = "November 2026",
            items = listOf(
                "Added the ALKE heart-rate timeline with workout context, preserving the recorded session data and its detailed views.",
            ),
        ),
    )

    /**
     * Expectation-setting points shown during onboarding and at the top of "What's New". This is the
     * “what is this and what should I expect” story, so people don't have to go read GitHub.
     */
    data class Expectation(
        val icon: ImageVector,
        val title: String,
        val body: String,
    )

    val expectations: List<Expectation> = listOf(
        Expectation(
            icon = Icons.Outlined.Science,
            title = uiString(R.string.l10n_app_changelog_independent_and_experimental_f9b65317),
            body = uiString(R.string.onboarding_expectation_independent_body),
        ),
        Expectation(
            icon = Icons.Outlined.VerifiedUser,
            title = uiString(R.string.l10n_app_changelog_whoop_4_0_is_the_supported_16893d9d),
            body = uiString(R.string.onboarding_expectation_whoop_support_body),
        ),
        Expectation(
            icon = Icons.Outlined.HourglassEmpty,
            title = uiString(R.string.l10n_app_changelog_your_scores_build_over_a_few_41388c54),
            body = uiString(R.string.onboarding_expectation_scores_body),
        ),
        Expectation(
            icon = Icons.Outlined.Shield,
            title = uiString(R.string.l10n_app_changelog_everything_stays_on_your_device_575125e9),
            body = uiString(R.string.onboarding_expectation_local_body),
        ),
    )
}
