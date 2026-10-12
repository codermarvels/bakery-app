package com.vsjbakery.app.data.auth

import com.vsjbakery.app.data.RemoteErrors
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class ProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val role: String? = null,
)

/**
 * Sign-in backed by Supabase Auth. The role always comes from the `profiles` table on the
 * server; the app never decides a user's role itself.
 */
class SupabaseAuthRepository(
    private val client: SupabaseClient,
    scope: CoroutineScope,
    /** Runs while still signed in, e.g. to stop new-order alerts on this phone. */
    private val beforeSignOut: suspend () -> Unit = {},
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Follow the library's session status: restored at app start, refreshed in the
        // background, cleared on sign-out.
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Initializing -> _authState.value = AuthState.Loading
                    is SessionStatus.NotAuthenticated -> _authState.value = AuthState.SignedOut
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        val current = _authState.value
                        val alreadyLoaded = current is AuthState.SignedIn && current.profile.id == user?.id
                        if (user != null && !alreadyLoaded) loadProfile(user)
                    }
                    // Token refresh failed (usually offline). Keep the current state; the
                    // library retries automatically.
                    is SessionStatus.RefreshFailure -> Unit
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult = guard {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val user = client.auth.currentUserOrNull()
            ?: return@guard AuthResult.Failure("Sign-in did not complete. Please try again.")
        when (val state = loadProfile(user)) {
            is AuthState.SignedIn -> AuthResult.SignedIn(state.profile)
            is AuthState.ProfileUnavailable -> AuthResult.Failure(state.message)
            else -> AuthResult.Failure("Sign-in did not complete. Please try again.")
        }
    }

    override suspend fun signUp(fullName: String, email: String, password: String): AuthResult = guard {
        val trimmedEmail = email.trim()
        client.auth.signUpWith(Email) {
            this.email = trimmedEmail
            this.password = password
            // Only the name is sent. The database gives every new account the customer
            // role and ignores anything else in this data.
            data = buildJsonObject { put("full_name", fullName.trim()) }
        }
        val user = client.auth.currentUserOrNull()
        if (client.auth.currentSessionOrNull() == null || user == null) {
            // Supabase "Confirm email" is on: no session until the link is clicked.
            AuthResult.ConfirmationEmailSent(trimmedEmail)
        } else {
            when (val state = loadProfile(user)) {
                is AuthState.SignedIn -> AuthResult.SignedIn(state.profile)
                is AuthState.ProfileUnavailable -> AuthResult.Failure(state.message)
                else -> AuthResult.Failure("Account created, but sign-in did not complete.")
            }
        }
    }

    override suspend fun signOut() {
        beforeSignOut()
        try {
            client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Even if the server cannot be reached, the local session must be cleared.
            client.auth.clearSession()
        }
        _authState.value = AuthState.SignedOut
    }

    override suspend fun refreshProfile() {
        client.auth.currentUserOrNull()?.let { loadProfile(it) }
    }

    override suspend fun sendPasswordResetCode(email: String): String? = try {
        // Supabase answers the same way whether or not the email has an account, so this
        // cannot be used to find out who is a customer.
        client.auth.resetPasswordForEmail(email.trim())
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        friendlyMessage(e)
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult = guard {
        client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email.trim(), token = code.trim())
        client.auth.updateUser { password = newPassword }
        val user = client.auth.currentUserOrNull()
            ?: return@guard AuthResult.Failure("Password changed. Please sign in with your new password.")
        when (val state = loadProfile(user)) {
            is AuthState.SignedIn -> AuthResult.SignedIn(state.profile)
            is AuthState.ProfileUnavailable -> AuthResult.Failure(state.message)
            else -> AuthResult.Failure("Password changed. Please sign in with your new password.")
        }
    }

    override suspend fun deleteAccount(): String? = try {
        client.postgrest.rpc("delete_my_account")
        // The account no longer exists on the server, so only the phone's copy is cleared.
        client.auth.clearSession()
        _authState.value = AuthState.SignedOut
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RemoteErrors.message(e)
    }

    private suspend fun loadProfile(user: UserInfo): AuthState {
        val email = user.email.orEmpty()
        val state = try {
            val row = client.from("profiles")
                .select(Columns.list("id", "full_name", "role")) {
                    filter { eq("id", user.id) }
                }
                .decodeSingleOrNull<ProfileRow>()
            if (row == null) {
                AuthState.ProfileUnavailable(email, "Your account profile was not found. Please contact the bakery.")
            } else {
                AuthState.SignedIn(
                    UserProfile(
                        id = row.id,
                        email = email,
                        fullName = row.fullName,
                        role = AppRole.fromDatabase(row.role),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthState.ProfileUnavailable(email, friendlyMessage(e))
        }
        _authState.value = state
        return state
    }

    private inline fun guard(block: () -> AuthResult): AuthResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(friendlyMessage(e))
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is AuthRestException -> AuthErrorMessages.forCode(e.errorCode?.value, e.description ?: e.error)
        is HttpRequestException -> "Could not reach the bakery server. Check your internet connection."
        is RestException -> "The server could not complete the request. Please try again."
        else -> "Something went wrong. Please try again."
    }
}

/** Turns Supabase Auth error codes into plain-language messages. */
object AuthErrorMessages {
    fun forCode(code: String?, fallback: String?): String = when (code) {
        "invalid_credentials" -> "Incorrect email or password."
        "email_not_confirmed" -> "Please confirm your email first. Check your inbox for the link."
        "user_already_exists", "email_exists" -> "An account with this email already exists. Try signing in."
        "weak_password" -> "Please choose a stronger password."
        "over_email_send_rate_limit", "over_request_rate_limit" ->
            "Too many attempts. Please wait a few minutes and try again."
        "signup_disabled" -> "New sign-ups are currently turned off."
        "user_banned" -> "This account has been blocked. Please contact the bakery."
        "otp_expired" -> "That code is wrong or has expired. Ask for a new code."
        "same_password" -> "Choose a password different from your old one."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Sign-in failed. Please try again."
    }
}
