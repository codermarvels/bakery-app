package com.vsjbakery.app.ui.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vsjbakery.app.domain.model.AdminProduct
import com.vsjbakery.app.domain.model.CounterPaymentMethod
import com.vsjbakery.app.domain.model.CounterSale
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.domain.model.OrderDates
import com.vsjbakery.app.ui.components.SubmitButton
import com.vsjbakery.app.ui.shop.QuantityStepper

private const val COUNTER_INFO =
    "Use this tab for customers who buy at the bakery counter without the app.\n\n" +
        "1. Tap + for each item the customer takes, with the right quantity.\n" +
        "2. Choose how they paid: Cash, UPI (paid to the shop's UPI/QR) or Card (card machine).\n" +
        "3. Check the total matches what you collected, then tap Record sale.\n\n" +
        "This reduces stock straight away, so app customers see the correct availability, and the " +
        "sale appears in the sales reports.\n\n" +
        "Made a mistake? Use Undo on the sale below and give a reason. Stock is put back.\n\n" +
        "At closing, compare 'Today' totals with the cash drawer and the UPI/card statements."

@Composable
fun CounterTab(viewModel: CounterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var undoTarget by remember { mutableStateOf<CounterSale?>(null) }
    var confirmRecord by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Walk-in counter sale", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InfoButton(title = "Counter sales — what to enter", text = COUNTER_INFO)
        }
        AdminMessageBanner(state.message, onShown = viewModel::clearMessage)

        if (state.isLoading && state.products.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        state.loadError?.let {
            AdminMessageBanner(AdminMessage(it, isError = true), onShown = {})
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val sellable = state.products.filter { it.stockQuantity > 0 || (state.quantities[it.id] ?: 0) > 0 }
            if (sellable.isEmpty()) {
                item {
                    Text(
                        "Nothing in stock. Record production in the Stock tab first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(sellable, key = { it.id }) { product ->
                CounterProductRow(
                    product = product,
                    quantity = state.quantities[product.id] ?: 0,
                    onAdd = { viewModel.add(product) },
                    onDecrease = { viewModel.decrease(product) },
                )
            }
            item { TodaySection(state.sales, state.totals, onUndo = { undoTarget = it }) }
        }

        // Sale summary pinned at the bottom.
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sale total (${state.itemCount} item${if (state.itemCount == 1) "" else "s"})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Money.formatRupees(state.totalPaise),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CounterPaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = state.method == method,
                            onClick = { viewModel.setMethod(method) },
                            label = { Text(method.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::clearSale,
                        enabled = state.quantities.isNotEmpty() && !state.isRecording,
                    ) { Text("Clear") }
                    SubmitButton(
                        text = if (state.method == null && state.quantities.isNotEmpty()) "Choose payment" else "Record sale",
                        isSubmitting = state.isRecording,
                        enabled = state.canRecord,
                        onClick = { confirmRecord = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (confirmRecord) {
        AlertDialog(
            onDismissRequest = { confirmRecord = false },
            title = { Text("Record this sale?") },
            text = {
                Text(
                    "${state.itemCount} item(s), ${Money.formatRupees(state.totalPaise)}, paid by " +
                        "${state.method?.label}. Stock will be reduced now.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRecord = false
                    viewModel.record()
                }) { Text("Record") }
            },
            dismissButton = { TextButton(onClick = { confirmRecord = false }) { Text("Back") } },
        )
    }

    undoTarget?.let { sale ->
        ReasonDialog(
            title = "Undo sale #${sale.number}?",
            body = "${Money.formatRupees(sale.totalPaise)} by ${sale.method?.label ?: "?"}. " +
                "Stock will be put back and the sale removed from today's totals.",
            confirmText = "Undo sale",
            onConfirm = { reason ->
                undoTarget = null
                viewModel.undo(sale, reason)
            },
            onDismiss = { undoTarget = null },
        )
    }
}

@Composable
private fun CounterProductRow(product: AdminProduct, quantity: Int, onAdd: () -> Unit, onDecrease: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${Money.formatRupees(product.pricePaise)} · ${product.stockQuantity} in stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (quantity == 0) {
                OutlinedButton(onClick = onAdd, enabled = product.stockQuantity > 0) { Text("+ Add") }
            } else {
                QuantityStepper(
                    quantity = quantity,
                    onDecrement = onDecrease,
                    onIncrement = onAdd,
                    canIncrement = quantity < product.stockQuantity,
                )
            }
        }
    }
}

@Composable
private fun TodaySection(
    sales: List<CounterSale>,
    totals: com.vsjbakery.app.domain.model.CounterTotals,
    onUndo: (CounterSale) -> Unit,
) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Today at the counter", style = MaterialTheme.typography.titleLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${totals.saleCount} sale${if (totals.saleCount == 1) "" else "s"} · ${Money.formatRupees(totals.totalPaise)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                CounterPaymentMethod.entries.forEach { method ->
                    Row {
                        Text(method.label, modifier = Modifier.weight(1f))
                        Text(Money.formatRupees(totals.byMethod[method] ?: 0))
                    }
                }
            }
        }
        sales.forEach { sale -> CounterSaleRow(sale, onUndo = { onUndo(sale) }) }
    }
}

@Composable
private fun CounterSaleRow(sale: CounterSale, onUndo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "#${sale.number} · ${Money.formatRupees(sale.totalPaise)} · ${sale.method?.label ?: "?"}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        OrderDates.display(sale.createdAt) + if (sale.isUndone) " · UNDONE" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sale.isUndone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!sale.isUndone) TextButton(onClick = onUndo) { Text("Undo") }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                sale.items.joinToString { "${it.quantity} × ${it.productName}" },
                style = MaterialTheme.typography.bodySmall,
            )
            sale.note?.let {
                Spacer(Modifier.width(4.dp))
                Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Dialog that requires a reason before confirming. */
@Composable
fun ReasonDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(250) },
                    label = { Text("Reason (required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
