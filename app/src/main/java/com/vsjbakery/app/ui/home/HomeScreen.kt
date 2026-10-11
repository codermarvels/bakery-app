package com.vsjbakery.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vsjbakery.app.domain.model.Catalog
import com.vsjbakery.app.domain.model.Category
import com.vsjbakery.app.domain.model.Product
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.SectionHeader
import com.vsjbakery.app.ui.components.categoryIcon
import com.vsjbakery.app.ui.shop.CatalogState
import com.vsjbakery.app.ui.shop.ProductCard
import com.vsjbakery.app.ui.theme.Brown700
import com.vsjbakery.app.ui.theme.Brown900
import com.vsjbakery.app.ui.theme.Cream100
import com.vsjbakery.app.ui.theme.Gold300
import com.vsjbakery.app.ui.theme.VsjBakeryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    catalogState: CatalogState,
    isRefreshing: Boolean,
    cartQuantities: Map<String, Int>,
    onRefresh: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onAdd: (Product) -> Unit,
    onDecrement: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "VSJ BAKERY",
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp),
                    fontWeight = FontWeight.Bold,
                )
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { HeroBanner(modifier = Modifier.padding(horizontal = 16.dp)) }
                item { PickupInfoRow(modifier = Modifier.padding(horizontal = 16.dp)) }
                when (catalogState) {
                    CatalogState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is CatalogState.Error -> item {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "Couldn't load the menu",
                            message = catalogState.message,
                            action = { Button(onClick = onRefresh) { Text("Try again") } },
                        )
                    }
                    is CatalogState.Ready -> catalogContent(
                        catalog = catalogState.catalog,
                        cartQuantities = cartQuantities,
                        onCategoryClick = onCategoryClick,
                        onAdd = onAdd,
                        onDecrement = onDecrement,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.catalogContent(
    catalog: Catalog,
    cartQuantities: Map<String, Int>,
    onCategoryClick: (Category) -> Unit,
    onAdd: (Product) -> Unit,
    onDecrement: (Product) -> Unit,
) {
    item {
        SectionHeader(
            title = "Shop by category",
            subtitle = "Freshly prepared every day at our counter",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    // Two cards per row. A plain list of rows keeps everything in one scrollable column.
    items(catalog.categories.chunked(2)) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { category ->
                CategoryCard(
                    category = category,
                    productCount = catalog.productsIn(category.id).size,
                    onClick = { onCategoryClick(category) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    item {
        SectionHeader(
            title = "Fresh today",
            subtitle = "Available now for pickup",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    val available = catalog.products.filter { it.isAvailable }
    if (available.isEmpty()) {
        item { MenuEmptyCard(modifier = Modifier.padding(horizontal = 16.dp)) }
    } else {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(available, key = { it.id }) { product ->
                    val slug = catalog.category(product.categoryId)?.slug.orEmpty()
                    ProductCard(
                        product = product,
                        fallbackIcon = categoryIcon(slug),
                        quantityInCart = cartQuantities[product.id] ?: 0,
                        onAdd = { onAdd(product) },
                        onDecrement = { onDecrement(product) },
                        modifier = Modifier.width(176.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Brown900, Brown700)))
            .padding(24.dp),
    ) {
        Column {
            Text(
                text = "EST. FRESH DAILY",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = Gold300,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Baked with love,\nready for pickup",
                style = MaterialTheme.typography.headlineMedium,
                color = Cream100,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Cakes, pastries, breads and more — order ahead and collect at the counter.",
                style = MaterialTheme.typography.bodyMedium,
                color = Cream100.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun PickupInfoRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoChip(Icons.Filled.Storefront, "Bakery pickup only", Modifier.weight(1f))
        InfoChip(Icons.Filled.AccessTime, "Order ahead, skip the queue", Modifier.weight(1f))
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    productCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(132.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon(category.slug),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (productCount > 0) {
                    Text(
                        text = "$productCount item${if (productCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = category.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MenuEmptyCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Nothing ready right now", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Fresh batches are added through the day. Pull down to refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun HomeScreenPreview() {
    val categories = listOf(
        Category("1", "cakes", "Cakes", "Celebration & tea-time cakes"),
        Category("2", "breads", "Breads", "Baked fresh every morning"),
        Category("3", "beverages", "Beverages", "Tea, coffee & coolers"),
    )
    val products = listOf(
        Product("p1", "1", "Black Forest Cake", "", 45000, null, 4),
        Product("p2", "2", "Milk Bread", "", 4500, null, 12),
    )
    VsjBakeryTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreen(
                catalogState = CatalogState.Ready(Catalog(categories, products)),
                isRefreshing = false,
                cartQuantities = mapOf("p2" to 2),
                onRefresh = {},
                onCategoryClick = {},
                onAdd = {},
                onDecrement = {},
            )
        }
    }
}
