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

/** The "forgot password" form: first the email, then the emailed code and a new password. */
data class PasswordResetState(
    val email: String = "",
    val codeSent: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

data class DeleteAccountState(
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    /** True once the account is gone, so the Account tab can say so. */
    val deleted: Boolean = false,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.authState

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    private val _reset = MutableStateFlow(PasswordResetState())
    val reset: StateFlow<PasswordResetState> = _reset.asStateFlow()

    private val _delete = MutableStateFlow(DeleteAccountState())
    val delete: StateFlow<DeleteAccountState> = _delete.asStateFlow()

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

    /** Opens the forgot-password form, keeping an email already typed on the sign-in form. */
    fun startPasswordReset(email: String = "") {
        if (!_reset.value.isSubmitting) _reset.value = PasswordResetState(email = email.trim())
        resetForm()
    }

    fun sendResetCode(email: String) {
        val invalid = CredentialsValidator.emailError(email)
        if (invalid != null) {
            _reset.update { it.copy(errorMessage = invalid) }
            return
        }
        if (_reset.value.isSubmitting) return
        _reset.value = PasswordResetState(email = email.trim(), codeSent = _reset.value.codeSent, isSubmitting = true)
        viewModelScope.launch {
            val error = repository.sendPasswordResetCode(email)
            _reset.update {
                it.copy(isSubmitting = false, codeSent = error == null || it.codeSent, errorMessage = error)
            }
        }
    }

    fun resetPassword(code: String, password: String, confirmPassword: String, entry: AppRole) {
        val email = _reset.value.email
        val invalid = CredentialsValidator.resetCodeError(code)
            ?: CredentialsValidator.passwordError(password)
            ?: if (password != confirmPassword) "Passwords do not match" else null
        if (invalid != null) {
            _reset.update { it.copy(errorMessage = invalid) }
            return
        }
        if (_reset.value.isSubmitting) return
        _reset.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.resetPassword(email, code, password)) {
                is AuthResult.SignedIn -> {
                    val roleError = RoleAccess.entryError(entry, result.profile.role)
                    if (roleError != null) {
                        // The password was changed; only this way in is wrong for the account.
                        repository.signOut()
                        _reset.update {
                            it.copy(isSubmitting = false, errorMessage = "Password changed. $roleError")
                        }
                    } else {
                        _reset.value = PasswordResetState()
                        _form.value = AuthFormState(signedInAs = result.profile.role)
                    }
                }
                is AuthResult.Failure -> _reset.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                is AuthResult.ConfirmationEmailSent -> _reset.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteAccount() {
        if (_delete.value.isDeleting) return
        _delete.value = DeleteAccountState(isDeleting = true)
        viewModelScope.launch {
            val error = repository.deleteAccount()
            _delete.value = DeleteAccountState(errorMessage = error, deleted = error == null)
        }
    }

    fun clearDeleteError() {
        _delete.update { it.copy(errorMessage = null) }
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
        _delete.value = DeleteAccountState()
        viewModelScope.launch { _form.value = action() }
    }
}
