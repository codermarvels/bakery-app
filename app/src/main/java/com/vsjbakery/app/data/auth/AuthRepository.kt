package com.vsjbakery.app.data.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun signIn(email: String, password: String): AuthResult

    /** Self sign-up always creates a customer account; staff roles are granted by the owner. */
    suspend fun signUp(fullName: String, email: String, password: String): AuthResult

    suspend fun signOut()

    /** Reloads the profile, e.g. after a network error or a role change. */
    suspend fun refreshProfile()
}

/** Used when the build has no Supabase settings: everything reports "not configured". */
class UnconfiguredAuthRepository : AuthRepository {
    override val authState: StateFlow<AuthState> =
        kotlinx.coroutines.flow.MutableStateFlow(AuthState.NotConfigured)

    private val failure = AuthResult.Failure(
        "Sign-in is not set up in this version of the app. Please contact the bakery.",
    )

    override suspend fun signIn(email: String, password: String) = failure
    override suspend fun signUp(fullName: String, email: String, password: String) = failure
    override suspend fun signOut() = Unit
    override suspend fun refreshProfile() = Unit
}
