package com.vsjbakery.app.domain.model

data class Product(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String,
    /** Price of one item in paise. */
    val pricePaise: Long,
    /** Full web address of the photo, or null when no photo has been uploaded. */
    val imageUrl: String?,
    val stockQuantity: Int,
) {
    val isAvailable: Boolean get() = stockQuantity > 0
}

/** Everything the shop shows: categories in display order, and the products on sale. */
data class Catalog(
    val categories: List<Category>,
    val products: List<Product>,
) {
    private val productsById = products.associateBy { it.id }

    fun product(id: String): Product? = productsById[id]

    fun productsIn(categoryId: String): List<Product> =
        products.filter { it.categoryId == categoryId }

    fun category(id: String): Category? = categories.firstOrNull { it.id == id }
}

/** Short availability text shown on product cards. */
object Availability {
    const val LOW_STOCK_THRESHOLD = 5

    fun label(stockQuantity: Int): String = when {
        stockQuantity <= 0 -> "Sold out"
        stockQuantity <= LOW_STOCK_THRESHOLD -> "Only $stockQuantity left"
        else -> "In stock"
    }
}
