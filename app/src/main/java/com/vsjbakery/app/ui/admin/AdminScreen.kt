package com.vsjbakery.app.ui.admin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vsjbakery.app.push.OrderAlerts
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.MessageCard
import kotlinx.coroutines.delay

private enum class AdminTab(val label: String, val icon: ImageVector) {
    COUNTER("Counter", Icons.Outlined.PointOfSale),
    ORDERS("Orders", Icons.AutoMirrored.Outlined.ReceiptLong),
    STOCK("Stock", Icons.Outlined.Inventory2),
    PRODUCTS("Products", Icons.Outlined.Storefront),
}

/** Administrator dashboard: Counter sales, app Orders, Stock and Products. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    authState: AuthState,
    catalogViewModel: AdminCatalogViewModel,
    ordersViewModel: AdminOrdersViewModel,
    counterViewModel: CounterViewModel,
    onEditProduct: (productId: String?) -> Unit,
    onManageCategories: () -> Unit,
    onStockCount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    openOrdersRequested: Boolean = false,
    onOpenOrdersHandled: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        BackTopBar(title = "Admin", onBack = onBack)

        val profile = (authState as? AuthState.SignedIn)?.profile
        if (profile?.role != AppRole.ADMIN) {
            // Display-only guard; every admin action is also refused by the database.
            EmptyState(
                icon = Icons.Outlined.Lock,
                title = "Access restricted",
                message = "Sign in with an administrator account to open this page.",
            )
            return@Column
        }

        // Opened from a "New paid order" notification.
        LaunchedEffect(openOrdersRequested) {
            if (openOrdersRequested) {
                selectedTab = AdminTab.ORDERS.ordinal
                ordersViewModel.load()
                onOpenOrdersHandled()
            }
        }
        OrderAlertsBanner()

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            AdminTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.label) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                )
            }
        }

        when (AdminTab.entries[selectedTab]) {
            AdminTab.COUNTER -> CounterTab(counterViewModel)
            AdminTab.ORDERS -> OrdersTab(ordersViewModel)
            AdminTab.STOCK -> StockTab(catalogViewModel, onStockCount = onStockCount)
            AdminTab.PRODUCTS -> ProductsTab(
                viewModel = catalogViewModel,
                onEditProduct = onEditProduct,
                onManageCategories = onManageCategories,
            )
        }
    }
}

/**
 * Asks once for permission to show new-order alerts (Android 13+), and shows a short note
 * while alerts can't sound on this phone.
 */
@Composable
private fun OrderAlertsBanner() {
    val context = LocalContext.current
    var canAlert by remember { mutableStateOf(OrderAlerts.canAlert(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canAlert = OrderAlerts.canAlert(context)
    }
    LaunchedEffect(Unit) {
        if (OrderAlerts.isConfigured && !OrderAlerts.hasPermission(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Re-check when coming back from the phone's settings.
    LifecycleResumeEffect(Unit) {
        canAlert = OrderAlerts.canAlert(context)
        onPauseOrDispose { }
    }
    val text = when {
        !OrderAlerts.isConfigured -> "New-order alerts are not set up in this version of the app."
        !canAlert -> "Notifications are off, so you won't hear new orders. Tap to turn them on."
        else -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        MessageCard(
            message = text,
            icon = Icons.Outlined.NotificationsOff,
            modifier = Modifier.clickable(enabled = OrderAlerts.isConfigured) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

/** ⓘ button that explains what to enter on a screen. */
@Composable
fun InfoButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Info, contentDescription = "What to enter here", tint = MaterialTheme.colorScheme.tertiary)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

/** Shows an admin message for a few seconds, then clears it. */
@Composable
fun AdminMessageBanner(message: AdminMessage?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(if (message.isError) 8000 else 4000)
        onShown()
    }
    MessageCard(
        message = message.text,
        isError = message.isError,
        icon = Icons.Outlined.Info,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
