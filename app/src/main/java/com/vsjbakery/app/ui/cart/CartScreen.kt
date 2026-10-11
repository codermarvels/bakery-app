package com.vsjbakery.app.ui.cart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vsjbakery.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBrowseMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Your cart") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
        EmptyState(
            icon = Icons.Outlined.ShoppingBag,
            title = "Your cart is empty",
            message = "Add your favourite bakes and collect them fresh from the VSJ BAKERY counter.",
            action = {
                Button(onClick = onBrowseMenu) { Text("Browse the menu") }
            },
        )
    }
}
