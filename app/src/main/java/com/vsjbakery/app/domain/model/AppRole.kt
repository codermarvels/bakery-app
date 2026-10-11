package com.vsjbakery.app.domain.model

/**
 * The three kinds of user. The app only uses this to choose which screens to show; the real
 * permission checks happen on the server (Supabase Row Level Security), so a tampered app
 * still cannot do anything its role is not allowed to do.
 */
enum class AppRole(val title: String, val description: String) {
    CUSTOMER(
        title = "Customer",
        description = "Order bakery items for pickup and track your orders.",
    ),
    ADMIN(
        title = "Administrator",
        description = "Manage products, prices, photos, stock, wastage and orders.",
    ),
    REPORTS(
        title = "Reports (read-only)",
        description = "View daily, monthly and date-range sales summaries and export PDFs.",
    ),
}
