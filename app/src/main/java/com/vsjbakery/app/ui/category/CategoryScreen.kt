package com.vsjbakery.app.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vsjbakery.app.domain.model.Product
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.categoryIcon
import com.vsjbakery.app.ui.shop.CartSummaryBar
import com.vsjbakery.app.ui.shop.CartUiState
import com.vsjbakery.app.ui.shop.CatalogState
import com.vsjbakery.app.ui.shop.ProductCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryId: String,
    slug: String,
    name: String,
    catalogState: CatalogState,
    isRefreshing: Boolean,
    cartQuantities: Map<String, Int>,
    cartState: CartUiState,
    onRefresh: () -> Unit,
    onAdd: (Product) -> Unit,
    onDecrement: (Product) -> Unit,
    onOpenCart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = (catalogState as? CatalogState.Ready)?.catalog?.category(categoryId)
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(text = category?.name ?: name, style = MaterialTheme.typography.titleLarge)
                    category?.tagline?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            when (catalogState) {
                CatalogState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is CatalogState.Error -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "Couldn't load the menu",
                    message = catalogState.message,
                    action = { Button(onClick = onRefresh) { Text("Try again") } },
                )
                is CatalogState.Ready -> {
                    val products = catalogState.catalog.productsIn(categoryId)
                    if (products.isEmpty()) {
                        EmptyState(
                            icon = categoryIcon(slug),
                            title = "No ${category?.name ?: name} today",
                            message = "Nothing from this category is on the menu right now. Please check back later.",
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Available items first, sold-out ones at the end.
                            items(products.sortedBy { !it.isAvailable }, key = { it.id }) { product ->
                                ProductCard(
                                    product = product,
                                    fallbackIcon = categoryIcon(slug),
                                    quantityInCart = cartQuantities[product.id] ?: 0,
                                    onAdd = { onAdd(product) },
                                    onDecrement = { onDecrement(product) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (cartState.itemCount > 0) {
            CartSummaryBar(
                itemCount = cartState.itemCount,
                totalPaise = cartState.totalPaise,
                onOpenCart = onOpenCart,
            )
        }
    }
}
