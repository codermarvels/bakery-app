package com.vsjbakery.app.domain.model

/**
 * The three kinds of user. The app only uses this to choose which screens to show; the real
 * permission checks happen on the server (Supabase Row Level Security), so a tampered app
 * still cannot do anything its role is not allowed to do.
 */
enum class AppRole(val title: String, val shortTitle: String, val description: String) {
    CUSTOMER(
        title = "Customer",
        shortTitle = "customer",
        description = "Order bakery items for pickup and track your orders.",
    ),
    ADMIN(
        title = "Administrator",
        shortTitle = "administrator",
        description = "Manage products, prices, photos, stock, wastage and orders.",
    ),
    REPORTS(
        title = "Reports (read-only)",
        shortTitle = "reports",
        description = "View daily, monthly and date-range sales summaries and export PDFs.",
    ),
    ;

    companion object {
        /**
         * Converts the role text stored in the database. Anything unexpected becomes
         * CUSTOMER, the role with the fewest permissions.
         */
        fun fromDatabase(value: String?): AppRole = when (value) {
            "admin" -> ADMIN
            "reports" -> REPORTS
            else -> CUSTOMER
        }
    }
}
