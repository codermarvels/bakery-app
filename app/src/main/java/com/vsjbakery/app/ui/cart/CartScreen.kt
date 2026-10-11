package com.vsjbakery.app.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.CartLine
import com.vsjbakery.app.domain.model.CartRules
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.domain.model.PlacedOrder
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.MessageCard
import com.vsjbakery.app.ui.components.SubmitButton
import com.vsjbakery.app.ui.shop.CartUiState
import com.vsjbakery.app.ui.shop.CheckoutState
import com.vsjbakery.app.ui.shop.ProductImage
import com.vsjbakery.app.ui.shop.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    cart: CartUiState,
    checkout: CheckoutState,
    authState: AuthState,
    onIncrement: (CartLine) -> Unit,
    onDecrement: (CartLine) -> Unit,
    onRemove: (CartLine) -> Unit,
    onFixLine: (CartLine) -> Unit,
    onPlaceOrder: (pickupNote: String) -> Unit,
    onOrderPlaced: (PlacedOrder) -> Unit,
    onDismissError: () -> Unit,
    onSignIn: () -> Unit,
    onBrowseMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickupNote by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(checkout.placedOrder) {
        checkout.placedOrder?.let {
            pickupNote = ""
            onOrderPlaced(it)
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text("Your cart") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
        if (cart.lines.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.ShoppingBag,
                title = "Your cart is empty",
                message = "Add your favourite bakes and collect them fresh from the VSJ BAKERY counter.",
                action = { Button(onClick = onBrowseMenu) { Text("Browse the menu") } },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cart.lines, key = { it.productId }) { line ->
                CartLineCard(
                    line = line,
                    onIncrement = { onIncrement(line) },
                    onDecrement = { onDecrement(line) },
                    onRemove = { onRemove(line) },
                    onFix = { onFixLine(line) },
                )
            }
            item {
                OutlinedTextField(
                    value = pickupNote,
                    onValueChange = { if (it.length <= 300) pickupNote = it },
                    label = { Text("Pickup note (optional)") },
                    placeholder = { Text("e.g. I'll collect around 5 pm") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !checkout.isPlacing,
                )
            }
            item {
                MessageCard(
                    icon = Icons.Outlined.Storefront,
                    message = "Pickup only — collect your order at the VSJ BAKERY counter. " +
                        "Prices and stock are confirmed by the bakery when you place the order.",
                )
            }
        }

        CheckoutPanel(
            cart = cart,
            checkout = checkout,
            authState = authState,
            onPlaceOrder = { onPlaceOrder(pickupNote) },
            onDismissError = onDismissError,
            onSignIn = onSignIn,
        )
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onFix: () -> Unit,
) {
    val product = line.product
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = product?.imageUrl,
                fallbackIcon = Icons.Outlined.ShoppingBag,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product?.name ?: "Unavailable item",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (product != null) {
                    Text(
                        text = "${Money.formatRupees(product.pricePaise)} each",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                line.problem?.let { problem ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = problem,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onFix) {
                            Text(if (product?.isAvailable == true) "Update" else "Remove")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (product != null && product.isAvailable) {
                        QuantityStepper(
                            quantity = line.quantity,
                            onDecrement = onDecrement,
                            onIncrement = onIncrement,
                            canIncrement = line.quantity < CartRules.maxQuantity(product),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = Money.formatRupees(line.lineTotalPaise),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove from cart")
            }
        }
    }
}

@Composable
private fun CheckoutPanel(
    cart: CartUiState,
    checkout: CheckoutState,
    authState: AuthState,
    onPlaceOrder: () -> Unit,
    onDismissError: () -> Unit,
    onSignIn: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Total (${cart.itemCount} item${if (cart.itemCount == 1) "" else "s"})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Money.formatRupees(cart.totalPaise),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
            checkout.errorMessage?.let {
                MessageCard(message = it, isError = true)
                TextButton(onClick = onDismissError) { Text("OK") }
            }
            val profile = (authState as? AuthState.SignedIn)?.profile
            when {
                profile == null -> {
                    Text(
                        text = "Sign in to place your pickup order.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in to order") }
                }
                profile.role != AppRole.CUSTOMER -> MessageCard(
                    icon = Icons.Outlined.Info,
                    message = "You are signed in as ${profile.role.title}. Please use a customer account to place orders.",
                )
                else -> {
                    if (!cart.canCheckOut) {
                        Text(
                            text = "Please fix the highlighted items before ordering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    SubmitButton(
                        text = "Place pickup order",
                        isSubmitting = checkout.isPlacing,
                        enabled = cart.canCheckOut,
                        onClick = onPlaceOrder,
                    )
                }
            }
        }
    }
}

/** Shown after the server has created the order. */
@Composable
fun OrderPlacedScreen(
    orderNumber: Long,
    totalPaise: Long,
    onViewOrders: () -> Unit,
    onContinueShopping: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EmptyState(
            icon = Icons.Outlined.ShoppingBag,
            title = "Order #$orderNumber placed",
            message = "Total ${Money.formatRupees(totalPaise)}. Your order is awaiting payment. " +
                "Online payment is being added in the next update — the bakery prepares orders once they are paid.",
            action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = onViewOrders) { Text("View my orders") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onContinueShopping) { Text("Continue shopping") }
                }
            },
        )
    }
}
