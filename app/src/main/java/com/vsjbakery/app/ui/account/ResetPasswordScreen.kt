package com.vsjbakery.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.ui.auth.AuthFormState
import com.vsjbakery.app.ui.auth.PasswordResetState
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.MessageCard
import com.vsjbakery.app.ui.components.SubmitButton

/** Forgot password: the server emails a one-time code, then the user picks a new password. */
@Composable
fun ResetPasswordScreen(
    role: AppRole,
    initialEmail: String,
    state: PasswordResetState,
    form: AuthFormState,
    onStart: (email: String) -> Unit,
    onSendCode: (email: String) -> Unit,
    onReset: (code: String, password: String, confirm: String) -> Unit,
    onSignedIn: (AppRole) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    // Only once per visit, so turning the phone doesn't throw away a code request.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            onStart(initialEmail)
            started = true
        }
    }
    LaunchedEffect(form.signedInAs) { form.signedInAs?.let(onSignedIn) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        BackTopBar(title = "Reset password", onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.codeSent) {
                Text(
                    "Enter the email you signed up with. We'll email you a code to choose a new password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.errorMessage?.let { MessageCard(message = it, isError = true) }
                SubmitButton(text = "Email me a code", isSubmitting = state.isSubmitting, onClick = { onSendCode(email) })
            } else {
                MessageCard(
                    icon = Icons.Outlined.MarkEmailRead,
                    message = "If ${state.email} has an account, a code is on its way. " +
                        "Check your inbox (and spam folder). The code works for a limited time.",
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(10) },
                    label = { Text("Code from the email") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password (at least 8 characters)") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Repeat new password") },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.errorMessage?.let { MessageCard(message = it, isError = true) }
                SubmitButton(
                    text = "Save new password",
                    isSubmitting = state.isSubmitting,
                    onClick = { onReset(code, password, confirm) },
                )
                TextButton(
                    onClick = { onSendCode(state.email) },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Send a new code") }
            }
            if (role != AppRole.CUSTOMER) {
                Text(
                    "Staff can also ask the owner to set a new password in the Supabase dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
