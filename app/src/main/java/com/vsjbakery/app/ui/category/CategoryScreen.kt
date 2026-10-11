package com.vsjbakery.app.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vsjbakery.app.domain.model.StarterCategories
import com.vsjbakery.app.ui.components.EmptyState
import com.vsjbakery.app.ui.components.categoryIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    slug: String,
    name: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tagline = StarterCategories.bySlug(slug)?.tagline
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(text = name, style = MaterialTheme.typography.titleLarge)
                    if (tagline != null) {
                        Text(
                            text = tagline,
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
        EmptyState(
            icon = categoryIcon(slug),
            title = "No $name on the menu yet",
            message = "Items in this category will appear here with photos, prices and availability once the bakery publishes them.",
        )
    }
}
