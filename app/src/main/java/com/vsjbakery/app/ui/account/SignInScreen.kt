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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.ui.auth.AuthFormState
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.MessageCard
import com.vsjbakery.app.ui.components.SubmitButton

@Composable
fun SignInScreen(
    role: AppRole,
    authState: AuthState,
    form: AuthFormState,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    onSignedIn: (AppRole) -> Unit,
    onResetForm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Leave the screen once the server has confirmed the sign-in.
    LaunchedEffect(form.signedInAs) {
        form.signedInAs?.let(onSignedIn)
    }
    DisposableEffect(Unit) {
        onResetForm()
        onDispose { onResetForm() }
    }

    val notConfigured = authState is AuthState.NotConfigured
    val submit = { if (!form.isSubmitting && !notConfigured) onSignIn(email, password) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        BackTopBar(title = "${role.title} sign-in", onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = role.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (notConfigured) {
                MessageCard(
                    icon = Icons.Outlined.Info,
                    message = "Sign-in is not set up in this version of the app.",
                )
            }
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
                singleLine = true,
                enabled = !form.isSubmitting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            form.errorMessage?.let { MessageCard(message = it, isError = true) }
            SubmitButton(
                text = "Sign in",
                isSubmitting = form.isSubmitting,
                enabled = !notConfigured,
                onClick = submit,
            )
            if (role == AppRole.CUSTOMER) {
                TextButton(
                    onClick = onCreateAccount,
                    enabled = !form.isSubmitting,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("New here? Create a customer account")
                }
            } else {
                Text(
                    text = "Staff accounts are created by the bakery owner. Ask them if you need access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
