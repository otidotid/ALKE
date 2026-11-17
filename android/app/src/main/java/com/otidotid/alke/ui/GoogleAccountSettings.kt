package com.otidotid.alke.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.otidotid.alke.BuildConfig

/**
 * Google OAuth Web client IDs are public identifiers, not credentials or tokens.
 * The build can supply one; an owner may configure one locally when a CI APK
 * was compiled without it. Neither OAuth tokens nor signing keys are stored here.
 */
internal object GoogleOAuthClientConfig {
    private const val PREFERENCES = "alke_google_oauth"
    private const val KEY_WEB_CLIENT_ID = "web_client_id"

    fun current(context: Context): String {
        val saved = context.applicationContext.getSharedPreferences(
            PREFERENCES, Context.MODE_PRIVATE,
        ).getString(KEY_WEB_CLIENT_ID, null).orEmpty().trim()
        if (isValidGoogleWebClientId(saved)) return saved
        return BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
            .takeIf(::isValidGoogleWebClientId).orEmpty()
    }

    fun save(context: Context, value: String): Boolean {
        val normalized = value.trim()
        if (!isValidGoogleWebClientId(normalized)) return false
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEB_CLIENT_ID, normalized).apply()
        return true
    }
}

/** Keep invalid/placeholder OAuth IDs out of the Google credential request. */
internal fun isValidGoogleWebClientId(value: String): Boolean =
    Regex("^[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com$")
        .matches(value.trim())

internal data class GoogleAccountState(
    val accountId: String,
    val displayName: String?,
    val email: String,
)

internal sealed interface GoogleSignInResult {
    data class Success(
        val account: GoogleAccountState,
    ) : GoogleSignInResult

    data object Cancelled : GoogleSignInResult

    data class Failure(
        val message: String,
    ) : GoogleSignInResult
}

/**
 * Stores account display metadata only.
 *
 * Google ID tokens and OAuth access tokens are never persisted.
 */
internal class GoogleAccountStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(
        "alke_google_account",
        Context.MODE_PRIVATE,
    )

    fun read(): GoogleAccountState? {
        val accountId = preferences.getString("account_id", null) ?: return null
        val email = preferences.getString("email", null) ?: return null

        return GoogleAccountState(
            accountId = accountId,
            displayName = preferences.getString("display_name", null),
            email = email,
        )
    }

    fun write(account: GoogleAccountState) {
        preferences.edit()
            .putString("account_id", account.accountId)
            .putString("display_name", account.displayName)
            .putString("email", account.email)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}

internal class GoogleSignInController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val accountStore = GoogleAccountStore(appContext)

    fun storedAccount(): GoogleAccountState? {
        return accountStore.read()
    }

    suspend fun signIn(
        hostContext: Context,
    ): GoogleSignInResult {
        val activity = hostContext.findComponentActivity()
            ?: return GoogleSignInResult.Failure(
                "Google Sign-In requires an active ALKE screen.",
            )

        val webClientId = GoogleOAuthClientConfig.current(appContext)
        if (webClientId.isBlank()) {
            return GoogleSignInResult.Failure(
                "Google Sign-In needs a Google OAuth Web Client ID. " +
                    "Open ALKE Settings > Backup & History > Google Drive to configure it. " +
                    "The Google Cloud Android OAuth client must also match this APK package and signing SHA-1.",
            )
        }

        val googleOption = GetSignInWithGoogleOption.Builder(
            webClientId,
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = activity,
                request = request,
            )

            val customCredential = response.credential as? CustomCredential
                ?: return GoogleSignInResult.Failure(
                    "Google returned an unsupported credential.",
                )

            if (
                customCredential.type !=
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleSignInResult.Failure(
                    "Google returned an unsupported credential.",
                )
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(
                customCredential.data,
            )

            val account = GoogleAccountState(
                accountId = googleCredential.id,
                displayName = googleCredential.displayName,
                email = googleCredential.id,
            )

            accountStore.write(account)

            GoogleSignInResult.Success(account)
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (failure: GetCredentialException) {
            val detail = failure.message?.takeIf { it.isNotBlank() }
            GoogleSignInResult.Failure(
                buildString {
                    append("Google Sign-In failed for ${appContext.packageName}. Verify the Google Cloud Android OAuth client package and APK signing SHA-1, plus the Web Client ID configured in ALKE.")
                    if (detail != null) append(" Details: $detail")
                },
            )
        } catch (failure: Exception) {
            GoogleSignInResult.Failure(
                failure.message ?: "Google Sign-In did not complete.",
            )
        }
    }

    suspend fun signOut() {
        runCatching {
            credentialManager.clearCredentialState(
                ClearCredentialStateRequest(),
            )
        }

        accountStore.clear()
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}