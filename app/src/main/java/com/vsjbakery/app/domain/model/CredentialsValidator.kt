package com.vsjbakery.app.domain.model

/** Quick checks before sending a form to the server. The server re-checks everything. */
object CredentialsValidator {

    const val MIN_PASSWORD_LENGTH = 8
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email address"
        !emailPattern.matches(email.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Enter your password"
        password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
        else -> null
    }

    fun nameError(name: String): String? = when {
        name.isBlank() -> "Enter your name"
        name.trim().length > 100 -> "Name is too long"
        else -> null
    }

    /** The code in the password-reset email: 6 to 10 digits, depending on project settings. */
    fun resetCodeError(code: String): String? = when {
        code.isBlank() -> "Enter the code from the email"
        !code.trim().matches(Regex("^[0-9]{6,10}$")) -> "The code is the number in the email (6 or more digits)"
        else -> null
    }
}
