package com.vsjbakery.app.domain.model

/** How a walk-in customer paid at the counter. */
enum class CounterPaymentMethod(val dbValue: String, val label: String) {
    CASH("cash", "Cash"),
    UPI("upi", "UPI"),
    CARD("card", "Card"),
    ;

    companion object {
        fun fromDatabase(value: String?): CounterPaymentMethod? = entries.firstOrNull { it.dbValue == value }
    }
}

/** A product as the administrator sees it, including hidden ones. */
data class AdminProduct(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val pricePaise: Long,
    val imagePath: String?,
    val imageUrl: String?,
    val stockQuantity: Int,
    val isActive: Boolean,
)

data class AdminCategory(
    val id: String,
    val slug: String,
    val name: String,
    val tagline: String,
    val sortOrder: Int,
    val isActive: Boolean,
)

/** Values from the product form. [id] is null for a new product. */
data class ProductDraft(
    val id: String?,
    val categoryId: String,
    val name: String,
    val description: String,
    val pricePaise: Long,
    val isActive: Boolean,
)

data class CategoryDraft(
    val id: String?,
    val name: String,
    val tagline: String,
    val sortOrder: Int,
    val isActive: Boolean,
)

enum class StockMovementType(val dbValue: String, val label: String) {
    PRODUCTION("production", "Production"),
    SALE("sale", "Sale"),
    ADJUSTMENT("adjustment", "Adjustment"),
    WASTAGE("wastage", "Wastage"),
    REFUND_RESTOCK("refund_restock", "Returned to stock"),
    ;

    companion object {
        fun fromDatabase(value: String): StockMovementType =
            entries.firstOrNull { it.dbValue == value } ?: ADJUSTMENT
    }
}

data class StockMovement(
    val type: StockMovementType,
    val quantityDelta: Int,
    val reason: String?,
    val createdAt: String,
)

/** An app order as the administrator sees it. */
data class AdminOrder(
    val order: Order,
    val customerName: String,
    val customerPhone: String?,
)

/** One walk-in sale recorded at the counter. */
data class CounterSale(
    val id: String,
    val number: Long,
    val totalPaise: Long,
    val method: CounterPaymentMethod?,
    val isUndone: Boolean,
    val note: String?,
    val createdAt: String,
    val items: List<OrderItem>,
)

/** Today's counter takings, split by how customers paid. Undone sales are excluded. */
data class CounterTotals(
    val saleCount: Int,
    val totalPaise: Long,
    val byMethod: Map<CounterPaymentMethod, Long>,
) {
    companion object {
        fun of(sales: List<CounterSale>): CounterTotals {
            val counted = sales.filter { !it.isUndone }
            return CounterTotals(
                saleCount = counted.size,
                totalPaise = counted.sumOf { it.totalPaise },
                byMethod = CounterPaymentMethod.entries.associateWith { method ->
                    counted.filter { it.method == method }.sumOf { it.totalPaise }
                },
            )
        }
    }
}

data class StockDifference(
    val productName: String,
    val expected: Int,
    val counted: Int,
    val difference: Int,
    val valuePaise: Long,
)

/** Turns what the administrator types into safe values. */
object AdminInput {
    /** Highest price accepted (matches the database limit of ₹1,00,000). */
    const val MAX_PRICE_PAISE = 10_000_000L

    private val rupeePattern = Regex("^\\d{1,6}(\\.\\d{1,2})?$")

    /** "45", "45.5", "1,250.00" → paise. Returns null if the text is not a valid price. */
    fun parseRupees(text: String): Long? {
        val cleaned = text.trim().replace(",", "").removePrefix("₹").trim()
        if (!rupeePattern.matches(cleaned)) return null
        val parts = cleaned.split(".")
        val rupees = parts[0].toLong()
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
        val total = rupees * 100 + paise
        return total.takeIf { it in 0..MAX_PRICE_PAISE }
    }

    /** Paise → text for an input box, e.g. 4550 → "45.50", 4500 → "45". */
    fun rupeesForInput(paise: Long): String =
        if (paise % 100 == 0L) (paise / 100).toString() else "%d.%02d".format(paise / 100, paise % 100)

    /** Whole number of items, e.g. "12" → 12. Null if not a whole number in range. */
    fun parseQuantity(text: String, min: Int = 1, max: Int = 100_000): Int? =
        text.trim().toIntOrNull()?.takeIf { it in min..max }

    /** Web-safe category code from its name, e.g. "Tea & Coffee" → "tea-coffee". */
    fun slugFromName(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
            .ifBlank { "category" }
}
