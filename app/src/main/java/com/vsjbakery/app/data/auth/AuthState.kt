package com.vsjbakery.app.data.auth

import com.vsjbakery.app.domain.model.UserProfile

/** What the app currently knows about who is signed in. */
sealed interface AuthState {
    /** This build has no Supabase project configured, so sign-in is unavailable. */
    data object NotConfigured : AuthState

    /** Restoring a saved session or loading the profile. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val profile: UserProfile) : AuthState

    /** Signed in, but the profile (and so the role) could not be loaded, e.g. offline. */
    data class ProfileUnavailable(val email: String, val message: String) : AuthState
}

sealed interface AuthResult {
    data class SignedIn(val profile: UserProfile) : AuthResult

    /** Account created; the user must click the link in the confirmation email first. */
    data class ConfirmationEmailSent(val email: String) : AuthResult

    data class Failure(val message: String) : AuthResult
}
