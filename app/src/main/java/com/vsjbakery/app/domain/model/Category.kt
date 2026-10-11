package com.vsjbakery.app.domain.model

/**
 * A product category shown to customers. Categories will be stored in the database and be
 * editable by the administrator; [StarterCategories] lists the ones the bakery opens with.
 */
data class Category(
    val slug: String,
    val name: String,
    val tagline: String,
)

object StarterCategories {
    val all: List<Category> = listOf(
        Category(slug = "cakes", name = "Cakes", tagline = "Celebration & tea-time cakes"),
        Category(slug = "pastries", name = "Pastries", tagline = "Flaky, creamy, delicate"),
        Category(slug = "breads", name = "Breads", tagline = "Baked fresh every morning"),
        Category(slug = "cookies", name = "Cookies", tagline = "Crisp, buttery bites"),
        Category(slug = "snacks", name = "Snacks", tagline = "Puffs, rolls & savouries"),
        Category(slug = "beverages", name = "Beverages", tagline = "Tea, coffee & coolers"),
    )

    fun bySlug(slug: String): Category? = all.firstOrNull { it.slug == slug }
}
