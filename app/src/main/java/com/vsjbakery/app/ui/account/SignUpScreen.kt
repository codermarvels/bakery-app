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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.CredentialsValidator
import com.vsjbakery.app.ui.auth.AuthFormState
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.MessageCard
import com.vsjbakery.app.ui.components.SubmitButton

/** Customer self sign-up. Staff accounts are never created here. */
@Composable
fun SignUpScreen(
    authState: AuthState,
    form: AuthFormState,
    onSignUp: (name: String, email: String, password: String, confirm: String) -> Unit,
    onSignedIn: (AppRole) -> Unit,
    onGoToSignIn: () -> Unit,
    onResetForm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(form.signedInAs) { form.signedInAs?.let(onSignedIn) }
    DisposableEffect(Unit) {
        onResetForm()
        onDispose { onResetForm() }
    }

    val notConfigured = authState is AuthState.NotConfigured

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        BackTopBar(title = "Create account", onBack = onBack)

        val sentTo = form.confirmationSentTo
        if (sentTo != null) {
            EmptyState(
                icon = Icons.Outlined.MarkEmailRead,
                title = "Check your email",
                message = "We sent a confirmation link to $sentTo. Open it on this phone, then come back and sign in.",
                action = { Button(onClick = onGoToSignIn) { Text("Go to sign in") } },
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Create a customer account to order bakery items for pickup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                enabled = !form.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !form.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                supportingText = { Text("At least ${CredentialsValidator.MIN_PASSWORD_LENGTH} characters") },
                singleLine = true,
                enabled = !form.isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirm password") },
                singleLine = true,
                enabled = !form.isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            form.errorMessage?.let { MessageCard(message = it, isError = true) }
            SubmitButton(
                text = "Create account",
                isSubmitting = form.isSubmitting,
                enabled = !notConfigured,
                onClick = { onSignUp(name, email, password, confirm) },
            )
        }
    }
}
