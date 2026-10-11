package com.vsjbakery.app.domain.model

/** One row in the cart, joined with the latest product information from the server. */
data class CartLine(
    val productId: String,
    val product: Product?,
    val quantity: Int,
) {
    /** Problem to show for this line, or null if it can be ordered as it is. */
    val problem: String?
        get() = when {
            product == null -> "No longer available"
            !product.isAvailable -> "Sold out"
            quantity > product.stockQuantity -> "Only ${product.stockQuantity} left"
            else -> null
        }

    /** Line total shown in the cart. The server recalculates it when the order is placed. */
    val lineTotalPaise: Long get() = (product?.pricePaise ?: 0L) * quantity
}

/**
 * The cart's maths. The totals here are only for display; the server works out the real
 * total from its own prices when the order is placed.
 */
object CartRules {
    /** Most of one item a customer can add (the server enforces the same limit). */
    const val MAX_PER_ITEM = 100

    fun lines(quantities: Map<String, Int>, catalog: Catalog?): List<CartLine> =
        quantities.map { (id, quantity) -> CartLine(id, catalog?.product(id), quantity) }

    fun totalPaise(lines: List<CartLine>): Long = lines.sumOf { it.lineTotalPaise }

    fun itemCount(quantities: Map<String, Int>): Int = quantities.values.sum()

    fun canCheckOut(lines: List<CartLine>): Boolean =
        lines.isNotEmpty() && lines.all { it.problem == null }

    /** Largest quantity allowed for a product right now. */
    fun maxQuantity(product: Product): Int = minOf(product.stockQuantity, MAX_PER_ITEM)

    /** Adds one, without going above what is in stock. */
    fun increment(current: Int, product: Product): Int = minOf(current + 1, maxQuantity(product))
}
