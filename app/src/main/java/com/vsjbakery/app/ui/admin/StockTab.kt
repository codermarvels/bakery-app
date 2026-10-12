package com.vsjbakery.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsjbakery.app.domain.model.AdminInput
import com.vsjbakery.app.domain.model.AdminProduct
import com.vsjbakery.app.domain.model.OrderDates

private const val STOCK_INFO =
    "Keep the stock true so app customers only order what is really on the shelf.\n\n" +
        "• + Made: freshly prepared items (e.g. this morning's batch of 30 puffs).\n" +
        "• Wastage: spoiled, burnt, dropped or expired items. A reason is required.\n" +
        "• Adjust: correct a wrong count up or down. A reason is required.\n" +
        "• History: every change, with who/why, for checking.\n\n" +
        "Sales from the app and the Counter tab reduce stock automatically — do not enter them here.\n\n" +
        "End of day: use 'End-of-day count' to enter what is actually left on the shelves."

private enum class StockAction(val title: String, val needsReason: Boolean) {
    PRODUCTION("Add freshly made stock", needsReason = false),
    WASTAGE("Record wastage", needsReason = true),
    ADJUST("Adjust stock", needsReason = true),
}

@Composable
fun StockTab(viewModel: AdminCatalogViewModel, onStockCount: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var action by remember { mutableStateOf<Pair<AdminProduct, StockAction>?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Stock", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InfoButton(title = "Stock — what to enter", text = STOCK_INFO)
        }
        Button(
            onClick = onStockCount,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) { Text("End-of-day count") }
        AdminMessageBanner(state.message, onShown = viewModel::clearMessage)
        state.loadError?.let { AdminMessageBanner(AdminMessage(it, isError = true), onShown = {}) }

        if (state.isLoading && state.products.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.products, key = { it.id }) { product ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    state.categoryName(product.categoryId) + if (!product.isActive) " · hidden" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${product.stockQuantity}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (product.stockQuantity == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilledTonalButton(onClick = { action = product to StockAction.PRODUCTION }) { Text("+ Made") }
                            FilledTonalButton(onClick = { action = product to StockAction.WASTAGE }) { Text("Wastage") }
                            TextButton(onClick = { action = product to StockAction.ADJUST }) { Text("Adjust") }
                            TextButton(onClick = { viewModel.openHistory(product) }) { Text("History") }
                        }
                    }
                }
            }
        }
    }

    action?.let { (product, kind) ->
        StockActionDialog(
            product = product,
            action = kind,
            isSaving = state.isSaving,
            onDismiss = { action = null },
            onConfirm = { quantity, text ->
                action = null
                when (kind) {
                    StockAction.PRODUCTION -> viewModel.recordProduction(product, quantity, text)
                    StockAction.WASTAGE -> viewModel.recordWastage(product, quantity, text)
                    StockAction.ADJUST -> viewModel.adjustStock(product, quantity, text)
                }
            },
        )
    }

    state.history?.let { history ->
        AlertDialog(
            onDismissRequest = viewModel::closeHistory,
            title = { Text("${history.product.name} — history") },
            text = {
                when {
                    history.isLoading -> CircularProgressIndicator()
                    history.errorMessage != null -> Text(history.errorMessage)
                    history.movements.isEmpty() -> Text("No stock changes yet.")
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history.movements) { m ->
                            Column {
                                Text(
                                    "${if (m.quantityDelta > 0) "+" else ""}${m.quantityDelta} · ${m.type.label}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (m.quantityDelta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    OrderDates.display(m.createdAt) + (m.reason?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeHistory) { Text("Close") } },
        )
    }
}

@Composable
private fun StockActionDialog(
    product: AdminProduct,
    action: StockAction,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, text: String) -> Unit,
) {
    var quantityText by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    // Adjustments may be negative ("-2" removes two); the others are positive amounts.
    val quantity = if (action == StockAction.ADJUST) {
        quantityText.trim().toIntOrNull()?.takeIf { it != 0 && it in -100_000..100_000 }
    } else {
        AdminInput.parseQuantity(quantityText)
    }
    val tooMuch = action == StockAction.WASTAGE && quantity != null && quantity > product.stockQuantity
    val reasonOk = !action.needsReason || text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${product.name} — ${product.stockQuantity} in stock now")
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.take(7) },
                    label = {
                        Text(if (action == StockAction.ADJUST) "Change (e.g. 3 or -2)" else "Quantity")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (action == StockAction.ADJUST) KeyboardType.Text else KeyboardType.Number,
                    ),
                    isError = quantityText.isNotBlank() && (quantity == null || tooMuch),
                    supportingText = { if (tooMuch) Text("More than is in stock") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(250) },
                    label = { Text(if (action.needsReason) "Reason (required)" else "Note (optional, e.g. morning batch)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { quantity?.let { onConfirm(it, text) } },
                enabled = quantity != null && !tooMuch && reasonOk && !isSaving,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
