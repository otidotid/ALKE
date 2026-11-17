package com.otidotid.alke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleOAuthClientIdTest {
    @Test fun acceptsActualWebOAuthId() {
        assertTrue(isValidGoogleWebClientId("1234567890-abc_def-123.apps.googleusercontent.com"))
        assertTrue(isValidGoogleWebClientId(" 1234567890-abc.apps.googleusercontent.com "))
    }

    @Test fun rejectsMissingOrWrongCredentialTypes() {
        assertFalse(isValidGoogleWebClientId(""))
        assertFalse(isValidGoogleWebClientId("YOUR_WEB_CLIENT_ID"))
        assertFalse(isValidGoogleWebClientId("AIzaSyExampleNotAWebOAuthClientId"))
        assertFalse(isValidGoogleWebClientId("1234567890-abc.apps.googleusercontent.com.evil.com"))
        assertFalse(isValidGoogleWebClientId("1234567890-abc.apps.googleusercontent.com\nspoof"))
    }
}
