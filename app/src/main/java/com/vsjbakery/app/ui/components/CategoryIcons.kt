package com.vsjbakery.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.BreakfastDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon for a category; categories added later by the administrator fall back to a shop icon. */
fun categoryIcon(slug: String): ImageVector = when (slug) {
    "cakes" -> Icons.Filled.Cake
    "pastries" -> Icons.Filled.BakeryDining
    "breads" -> Icons.Filled.BreakfastDining
    "cookies" -> Icons.Filled.Cookie
    "snacks" -> Icons.Filled.LunchDining
    "beverages" -> Icons.Filled.LocalCafe
    else -> Icons.Filled.Storefront
}
