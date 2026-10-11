package com.vsjbakery.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsjbakery.app.data.auth.AuthRepository
import com.vsjbakery.app.data.auth.AuthResult
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.CredentialsValidator
import com.vsjbakery.app.domain.model.RoleAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the sign-in / sign-up forms. */
data class AuthFormState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Set after sign-up when Supabase sent a confirmation email. */
    val confirmationSentTo: String? = null,
    /** Set once sign-in succeeds; the screen navigates and then calls [AuthViewModel.onCompletionHandled]. */
    val signedInAs: AppRole? = null,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.authState

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    fun signIn(email: String, password: String, entry: AppRole) {
        val invalid = CredentialsValidator.emailError(email)
            ?: if (password.isEmpty()) "Enter your password" else null
        if (invalid != null) {
            _form.update { it.copy(errorMessage = invalid) }
            return
        }
        submit {
            when (val result = repository.signIn(email, password)) {
                is AuthResult.SignedIn -> {
                    val roleError = RoleAccess.entryError(entry, result.profile.role)
                    if (roleError != null) {
                        repository.signOut()
                        AuthFormState(errorMessage = roleError)
                    } else {
                        AuthFormState(signedInAs = result.profile.role)
                    }
                }
                is AuthResult.Failure -> AuthFormState(errorMessage = result.message)
                is AuthResult.ConfirmationEmailSent -> AuthFormState(confirmationSentTo = result.email)
            }
        }
    }

    fun signUp(fullName: String, email: String, password: String, confirmPassword: String) {
        val invalid = CredentialsValidator.nameError(fullName)
            ?: CredentialsValidator.emailError(email)
            ?: CredentialsValidator.passwordError(password)
            ?: if (password != confirmPassword) "Passwords do not match" else null
        if (invalid != null) {
            _form.update { it.copy(errorMessage = invalid) }
            return
        }
        submit {
            when (val result = repository.signUp(fullName, email, password)) {
                is AuthResult.SignedIn -> AuthFormState(signedInAs = result.profile.role)
                is AuthResult.ConfirmationEmailSent -> AuthFormState(confirmationSentTo = result.email)
                is AuthResult.Failure -> AuthFormState(errorMessage = result.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }

    fun retryProfile() {
        viewModelScope.launch { repository.refreshProfile() }
    }

    fun onCompletionHandled() {
        _form.value = AuthFormState()
    }

    /** Clears messages when a form is opened or closed. */
    fun resetForm() {
        if (!_form.value.isSubmitting) _form.value = AuthFormState()
    }

    private fun submit(action: suspend () -> AuthFormState) {
        if (_form.value.isSubmitting) return
        _form.value = AuthFormState(isSubmitting = true)
        viewModelScope.launch { _form.value = action() }
    }
}
