package com.vsjbakery.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.UserProfile
import com.vsjbakery.app.ui.auth.DeleteAccountState
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.MessageCard
import com.vsjbakery.app.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    authState: AuthState,
    onSignIn: (AppRole) -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenOrders: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    deleteState: DeleteAccountState,
    onDeleteAccount: () -> Unit,
    onClearDeleteError: () -> Unit,
    policiesUrl: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Account") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
        when (authState) {
            AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is AuthState.SignedIn -> SignedInContent(
                profile = authState.profile,
                onOpenAdmin = onOpenAdmin,
                onOpenReports = onOpenReports,
                onOpenOrders = onOpenOrders,
                onSignOut = onSignOut,
                deleteState = deleteState,
                onDeleteAccount = onDeleteAccount,
                onClearDeleteError = onClearDeleteError,
                policiesUrl = policiesUrl,
            )
            is AuthState.ProfileUnavailable -> EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "Signed in as ${authState.email}",
                message = authState.message,
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = onRetryProfile) { Text("Try again") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onSignOut) { Text("Sign out") }
                    }
                },
            )
            AuthState.SignedOut, AuthState.NotConfigured -> SignedOutContent(
                notConfigured = authState is AuthState.NotConfigured,
                accountDeleted = deleteState.deleted,
                onSignIn = onSignIn,
                policiesUrl = policiesUrl,
            )
        }
    }
}

@Composable
private fun SignedOutContent(
    notConfigured: Boolean,
    accountDeleted: Boolean,
    onSignIn: (AppRole) -> Unit,
    policiesUrl: String,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (accountDeleted) {
            item {
                MessageCard(
                    icon = Icons.Outlined.CheckCircle,
                    message = "Your account has been deleted. Thank you for ordering from VSJ BAKERY.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (notConfigured) {
            item {
                MessageCard(
                    icon = Icons.Outlined.Info,
                    message = "Sign-in is not set up in this version of the app.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item { SectionHeader(title = "Customers") }
        item { RoleCard(AppRole.CUSTOMER, Icons.Filled.Person) { onSignIn(AppRole.CUSTOMER) } }
        item {
            SectionHeader(
                title = "Bakery staff",
                subtitle = "For VSJ BAKERY team members only",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item { RoleCard(AppRole.ADMIN, Icons.Filled.AdminPanelSettings) { onSignIn(AppRole.ADMIN) } }
        item { RoleCard(AppRole.REPORTS, Icons.Filled.Assessment) { onSignIn(AppRole.REPORTS) } }
        item { PolicyLinks(policiesUrl, Modifier.padding(horizontal = 16.dp)) }
    }
}

/** Links to the bakery's policy pages (the same pages Razorpay and Google Play check). */
@Composable
private fun PolicyLinks(baseUrl: String, modifier: Modifier = Modifier) {
    if (baseUrl.isBlank()) return
    val uriHandler = LocalUriHandler.current
    val base = baseUrl.trimEnd('/')
    Column(modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        listOf(
            "Privacy policy" to "privacy.html",
            "Terms, cancellations & refunds" to "terms.html",
            "Contact the bakery" to "contact.html",
        ).forEach { (label, page) ->
            TextButton(onClick = { runCatching { uriHandler.openUri("$base/$page") } }) { Text(label) }
        }
    }
}

@Composable
private fun DeleteAccountSection(
    state: DeleteAccountState,
    onDelete: () -> Unit,
    onClearError: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { confirming = true }, enabled = !state.isDeleting) {
            Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Delete my account", color = MaterialTheme.colorScheme.error)
        }
        if (state.isDeleting) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(22.dp), strokeWidth = 2.dp)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This permanently removes your sign-in, name and phone number. It cannot be undone.\n\n" +
                        "• Unpaid orders are cancelled.\n" +
                        "• Past orders stay in the bakery's sales records without your name.\n" +
                        "• If an order is paid and waiting for pickup, collect it first.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep my account") } },
        )
    }
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Account not deleted") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onClearError) { Text("OK") } },
        )
    }
}

@Composable
private fun SignedInContent(
    profile: UserProfile,
    onOpenAdmin: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenOrders: () -> Unit,
    onSignOut: () -> Unit,
    deleteState: DeleteAccountState,
    onDeleteAccount: () -> Unit,
    onClearDeleteError: () -> Unit,
    policiesUrl: String,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ProfileCard(profile) }
        when (profile.role) {
            AppRole.ADMIN -> {
                item {
                    Button(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open admin dashboard")
                    }
                }
                item {
                    OutlinedButton(onClick = onOpenReports, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Assessment, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sales reports")
                    }
                }
            }
            AppRole.REPORTS -> item {
                Button(onClick = onOpenReports, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Assessment, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open sales reports")
                }
            }
            AppRole.CUSTOMER -> item {
                Button(onClick = onOpenOrders, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("My orders")
                }
            }
        }
        item {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }
        if (profile.role == AppRole.CUSTOMER) {
            item { DeleteAccountSection(deleteState, onDeleteAccount, onClearDeleteError) }
        }
        item { PolicyLinks(policiesUrl) }
    }
}

@Composable
private fun ProfileCard(profile: UserProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile.fullName.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.fullName.ifBlank { "VSJ BAKERY member" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = profile.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                AssistChip(onClick = {}, label = { Text(profile.role.title) })
            }
        }
    }
}

@Composable
private fun RoleCard(role: AppRole, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(role.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = role.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
