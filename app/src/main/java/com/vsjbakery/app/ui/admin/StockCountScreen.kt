package com.vsjbakery.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsjbakery.app.domain.model.AdminInput
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.ui.components.BackTopBar
import com.vsjbakery.app.ui.components.SubmitButton

private const val COUNT_INFO =
    "Do this at closing time, after the last sale.\n\n" +
        "1. Count what is actually left on the shelves for each product.\n" +
        "2. Type the number in the box. Leave a box empty to skip that product.\n" +
        "3. Tap Save count.\n\n" +
        "Your count becomes the stock. Any difference from what the app expected (sales nobody " +
        "recorded, breakage, mistakes) is saved as an 'unrecorded difference' with its value, so it " +
        "shows up in reports. If the difference is wastage you know about, record it as wastage in " +
        "the Stock tab before counting."

@Composable
fun StockCountScreen(viewModel: AdminCatalogViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val counts = remember { mutableStateMapOf<String, String>() }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.clearCountResult()
        viewModel.load()
    }

    // Only boxes with a valid whole number are sent.
    val entered: Map<String, Int> = counts.mapNotNull { (id, text) ->
        AdminInput.parseQuantity(text, min = 0)?.let { id to it }
    }.toMap()
    val invalid = counts.any { (_, text) -> text.isNotBlank() && AdminInput.parseQuantity(text, min = 0) == null }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        BackTopBar(title = "End-of-day count", onBack = onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Enter what is actually left on the shelves.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            InfoButton(title = "End-of-day count — what to enter", text = COUNT_INFO)
        }
        AdminMessageBanner(state.message, onShown = viewModel::clearMessage)

        if (state.isLoading && state.products.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.products, key = { it.id }) { product ->
                val text = counts[product.id].orEmpty()
                val counted = AdminInput.parseQuantity(text, min = 0)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "App expects ${product.stockQuantity}" + when {
                                    counted == null -> ""
                                    counted == product.stockQuantity -> " · matches"
                                    else -> " · difference ${counted - product.stockQuantity}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (counted != null && counted != product.stockQuantity) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { counts[product.id] = it.filter(Char::isDigit).take(6) },
                            label = { Text("Counted") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SubmitButton(
            text = "Save count (${entered.size} product${if (entered.size == 1) "" else "s"})",
            isSubmitting = state.isSaving,
            enabled = entered.isNotEmpty() && !invalid,
            onClick = { viewModel.recordStockCount(entered, note) },
            modifier = Modifier.padding(16.dp),
        )
    }

    state.countResult?.let { differences ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Count saved") },
            text = {
                if (differences.isEmpty()) {
                    Text("Everything matched what the app expected. Stock is up to date.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Stock now matches your count. Unrecorded differences:")
                        differences.forEach { d ->
                            Text(
                                "${d.productName}: expected ${d.expected}, counted ${d.counted} " +
                                    "(${if (d.difference > 0) "+" else ""}${d.difference}, ${Money.formatRupees(d.valuePaise)})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "Total value: ${Money.formatRupees(differences.sumOf { it.valuePaise })}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCountResult()
                    onBack()
                }) { Text("Done") }
            },
        )
    }
}
