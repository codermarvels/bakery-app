package com.vsjbakery.app.data.catalog

import com.vsjbakery.app.data.SupabaseConfig
import com.vsjbakery.app.domain.model.Catalog
import com.vsjbakery.app.domain.model.Category
import com.vsjbakery.app.domain.model.Product
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface CatalogRepository {
    suspend fun loadCatalog(): Catalog
}

@Serializable
private data class CategoryRow(
    val id: String,
    val slug: String,
    val name: String,
    val tagline: String = "",
)

@Serializable
private data class ProductRow(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val description: String = "",
    @SerialName("price_paise") val pricePaise: Long,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("stock_quantity") val stockQuantity: Int,
    @SerialName("is_active") val isActive: Boolean,
)

/**
 * Reads categories and products. Row Level Security already hides anything not on sale
 * from customers; the extra is_active filters keep staff browsing the same shop view.
 */
class SupabaseCatalogRepository(
    private val client: SupabaseClient,
    private val config: SupabaseConfig,
) : CatalogRepository {

    override suspend fun loadCatalog(): Catalog {
        val categories = client.from("categories")
            .select(Columns.list("id", "slug", "name", "tagline")) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
                order("name", Order.ASCENDING)
            }
            .decodeList<CategoryRow>()
            .map { Category(id = it.id, slug = it.slug, name = it.name, tagline = it.tagline) }

        val activeCategoryIds = categories.map { it.id }.toSet()
        val products = client.from("products")
            .select(
                Columns.list(
                    "id", "category_id", "name", "description", "price_paise",
                    "image_path", "stock_quantity", "is_active",
                ),
            ) {
                filter { eq("is_active", true) }
                order("name", Order.ASCENDING)
            }
            .decodeList<ProductRow>()
            .filter { it.isActive && it.categoryId in activeCategoryIds }
            .map {
                Product(
                    id = it.id,
                    categoryId = it.categoryId,
                    name = it.name,
                    description = it.description,
                    pricePaise = it.pricePaise,
                    imageUrl = config.productImageUrl(it.imagePath),
                    stockQuantity = it.stockQuantity,
                )
            }
        return Catalog(categories, products)
    }
}
