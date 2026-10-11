package com.vsjbakery.app.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.domain.model.Order
import com.vsjbakery.app.domain.model.OrderDates
import com.vsjbakery.app.domain.model.OrderStatus
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.MessageCard
import com.vsjbakery.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyOrdersScreen(
    state: OrdersState,
    onLoad: () -> Unit,
    onCancel: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoad() }
    var confirmCancel by remember { mutableStateOf<Order?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        BackTopBar(title = "My orders", onBack = onBack)
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.orders.isNotEmpty(),
            onRefresh = onLoad,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.orders.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null && state.orders.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "Couldn't load your orders",
                    message = state.errorMessage,
                    action = { Button(onClick = onLoad) { Text("Try again") } },
                )
                state.orders.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "No orders yet",
                    message = "Orders you place for pickup will appear here.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    state.errorMessage?.let { item { MessageCard(message = it, isError = true) } }
                    items(state.orders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            isCancelling = state.cancellingId == order.id,
                            onCancel = { confirmCancel = order },
                        )
                    }
                }
            }
        }
    }

    confirmCancel?.let { order ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text("Cancel order #${order.number}?") },
            text = { Text("This unpaid order will be cancelled. You can place a new order any time.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = null
                    onCancel(order.id)
                }) { Text("Cancel order") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun OrderCard(order: Order, isCancelling: Boolean, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Order #${order.number}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = OrderDates.display(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(order.status)
            }
            Spacer(Modifier.height(12.dp))
            order.items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "${item.quantity} × ${item.productName}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(Money.formatRupees(item.lineTotalPaise), style = MaterialTheme.typography.bodyMedium)
                }
            }
            order.pickupNote?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Note: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = Money.formatRupees(order.totalPaise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (order.status == OrderStatus.PENDING_PAYMENT) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Online payment is coming in the next update. Unpaid orders are not prepared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (order.canCancel) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel order")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: OrderStatus) {
    val (container, content) = when (status) {
        OrderStatus.PENDING_PAYMENT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        OrderStatus.PAID, OrderStatus.READY_FOR_PICKUP -> SuccessGreen.copy(alpha = 0.15f) to SuccessGreen
        OrderStatus.COLLECTED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        OrderStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
