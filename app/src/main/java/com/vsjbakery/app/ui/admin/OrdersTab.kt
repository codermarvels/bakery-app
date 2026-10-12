package com.vsjbakery.app.ui.admin

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsjbakery.app.domain.model.AdminOrder
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.domain.model.OrderDates
import com.vsjbakery.app.domain.model.OrderStatus
import com.vsjbakery.app.domain.model.RefundState

private const val ORDERS_INFO =
    "Orders placed in the app for pickup.\n\n" +
        "• To prepare: paid orders. Pack them, then tap Mark ready.\n" +
        "• Ready: waiting at the counter. When the customer picks up, tap Collected.\n" +
        "• Unpaid: not paid yet. Do not prepare these.\n\n" +
        "Refund: gives the full amount back to the customer's UPI / card / bank through Razorpay and " +
        "cancels the order. Use it when the customer cancels before you prepare the order, when you " +
        "cannot make it, or for a quality complaint. Always refund here, not only in the Razorpay " +
        "dashboard, so sales reports and stock stay correct.\n\n" +
        "Walk-in customers are recorded in the Counter tab, not here."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersTab(viewModel: AdminOrdersViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var refunding by remember { mutableStateOf<AdminOrder?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("App pickup orders", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InfoButton(title = "App orders — what to do", text = ORDERS_INFO)
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrderFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text("${filter.label} (${state.count(filter)})") },
                )
            }
        }
        AdminMessageBanner(state.message, onShown = viewModel::clearMessage)
        state.loadError?.let { AdminMessageBanner(AdminMessage(it, isError = true), onShown = {}) }

        PullToRefreshBox(
            isRefreshing = state.isLoading && state.orders.isNotEmpty(),
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.orders.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.visible.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        "No orders here. Pull down to refresh.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.visible, key = { it.order.id }) { order ->
                        AdminOrderCard(
                            order = order,
                            isUpdating = state.updatingId == order.order.id,
                            onReady = { viewModel.markReady(order) },
                            onCollected = { viewModel.markCollected(order) },
                            onRefund = { refunding = order },
                        )
                    }
                }
            }
        }
    }
    refunding?.let { order ->
        RefundDialog(
            order = order,
            onConfirm = { reason, restock ->
                refunding = null
                viewModel.refund(order, reason, restock)
            },
            onDismiss = { refunding = null },
        )
    }
}

@Composable
private fun RefundDialog(order: AdminOrder, onConfirm: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val o = order.order
    val retry = order.refund != null
    // Collected items are with the customer; the server never restocks those anyway.
    val canRestock = !retry && o.status != OrderStatus.COLLECTED
    var reason by remember { mutableStateOf("") }
    var restock by remember { mutableStateOf(canRestock) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (retry) "Retry refund for order #${o.number}?" else "Refund order #${o.number}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${Money.formatRupees(o.totalPaise)} goes back to ${order.customerName}'s original payment method " +
                        "through Razorpay. The order is cancelled. This cannot be undone.",
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(250) },
                    label = { Text("Reason (the customer sees this)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canRestock) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = restock, onCheckedChange = { restock = it })
                        Text("Put the items back in stock (not yet prepared or handed over)")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason, restock && canRestock) }, enabled = reason.isNotBlank()) {
                Text(if (retry) "Retry refund" else "Refund")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AdminOrderCard(
    order: AdminOrder,
    isUpdating: Boolean,
    onReady: () -> Unit,
    onCollected: () -> Unit,
    onRefund: () -> Unit,
) {
    val o = order.order
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Order #${o.number} · ${o.status.label}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        order.customerName + (order.customerPhone?.let { " · $it" } ?: "") +
                            " · " + OrderDates.display(o.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Money.formatRupees(o.totalPaise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            o.items.forEach { Text("${it.quantity} × ${it.productName}", style = MaterialTheme.typography.bodyMedium) }
            o.pickupNote?.let {
                Spacer(Modifier.height(4.dp))
                Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (o.status) {
                OrderStatus.PAID -> Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onReady, enabled = !isUpdating) { Text("Mark ready") }
                    OutlinedButton(onClick = onCollected, enabled = !isUpdating) { Text("Collected") }
                }
                OrderStatus.READY_FOR_PICKUP -> Button(
                    onClick = onCollected,
                    enabled = !isUpdating,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Collected by customer") }
                OrderStatus.PENDING_PAYMENT -> Text(
                    "Not paid — do not prepare yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> Unit
            }
            order.refund?.let { refund ->
                Text(
                    refund.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (refund == RefundState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (order.canRefund) {
                TextButton(onClick = onRefund, enabled = !isUpdating, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        if (order.refund == null) "Refund" else "Retry refund",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
