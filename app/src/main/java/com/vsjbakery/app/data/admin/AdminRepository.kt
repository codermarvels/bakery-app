package com.vsjbakery.app.data.admin

import com.vsjbakery.app.data.SupabaseConfig
import com.vsjbakery.app.domain.model.AdminCategory
import com.vsjbakery.app.domain.model.AdminInput
import com.vsjbakery.app.domain.model.AdminOrder
import com.vsjbakery.app.domain.model.AdminProduct
import com.vsjbakery.app.domain.model.CategoryDraft
import com.vsjbakery.app.domain.model.CounterPaymentMethod
import com.vsjbakery.app.domain.model.CounterSale
import com.vsjbakery.app.domain.model.Order
import com.vsjbakery.app.domain.model.OrderItem
import com.vsjbakery.app.domain.model.OrderStatus
import com.vsjbakery.app.domain.model.ProductDraft
import com.vsjbakery.app.domain.model.StockDifference
import com.vsjbakery.app.domain.model.StockMovement
import com.vsjbakery.app.domain.model.StockMovementType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Everything the administrator screens read and change. Every change goes through Row
 * Level Security or an administrator-only database function, so these calls fail for
 * any other role even if someone tampered with the app.
 */
interface AdminRepository {
    suspend fun products(): List<AdminProduct>
    suspend fun categories(): List<AdminCategory>
    suspend fun saveProduct(draft: ProductDraft): String
    suspend fun saveCategory(draft: CategoryDraft)
    suspend fun uploadProductPhoto(productId: String, jpegBytes: ByteArray)

    suspend fun recordProduction(productId: String, quantity: Int, note: String?)
    suspend fun adjustStock(productId: String, quantityDelta: Int, reason: String)
    suspend fun recordWastage(productId: String, quantity: Int, reason: String)
    suspend fun stockHistory(productId: String): List<StockMovement>
    suspend fun recordStockCount(counts: Map<String, Int>, note: String?): List<StockDifference>

    suspend fun appOrders(): List<AdminOrder>
    suspend fun setOrderStatus(orderId: String, status: OrderStatus)

    suspend fun recordCounterSale(quantities: Map<String, Int>, method: CounterPaymentMethod, note: String?): Long
    suspend fun counterSalesToday(): List<CounterSale>
    suspend fun undoCounterSale(orderId: String, reason: String)
}

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

@Serializable
private data class CategoryRow(
    val id: String,
    val slug: String,
    val name: String,
    val tagline: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class MovementRow(
    @SerialName("movement_type") val movementType: String,
    @SerialName("quantity_delta") val quantityDelta: Int,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class ItemRow(
    @SerialName("product_name") val productName: String,
    @SerialName("unit_price_paise") val unitPricePaise: Long,
    val quantity: Int,
    @SerialName("line_total_paise") val lineTotalPaise: Long,
)

@Serializable
private data class OrderRow(
    val id: String,
    @SerialName("order_number") val orderNumber: Long,
    val status: String,
    @SerialName("total_paise") val totalPaise: Long,
    @SerialName("pickup_note") val pickupNote: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("order_items") val items: List<ItemRow> = emptyList(),
)

@Serializable
private data class ProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val phone: String? = null,
)

@Serializable
private data class PaymentRow(
    @SerialName("order_id") val orderId: String,
    val provider: String,
)

class SupabaseAdminRepository(
    private val client: SupabaseClient,
    private val config: SupabaseConfig,
) : AdminRepository {

    override suspend fun products(): List<AdminProduct> =
        client.from("products")
            .select(
                Columns.list(
                    "id", "category_id", "name", "description", "price_paise",
                    "image_path", "stock_quantity", "is_active",
                ),
            ) { order("name", SortOrder.ASCENDING) }
            .decodeList<ProductRow>()
            .map {
                AdminProduct(
                    id = it.id,
                    categoryId = it.categoryId,
                    name = it.name,
                    description = it.description,
                    pricePaise = it.pricePaise,
                    imagePath = it.imagePath,
                    imageUrl = config.productImageUrl(it.imagePath),
                    stockQuantity = it.stockQuantity,
                    isActive = it.isActive,
                )
            }

    override suspend fun categories(): List<AdminCategory> =
        client.from("categories")
            .select(Columns.list("id", "slug", "name", "tagline", "sort_order", "is_active")) {
                order("sort_order", SortOrder.ASCENDING)
                order("name", SortOrder.ASCENDING)
            }
            .decodeList<CategoryRow>()
            .map { AdminCategory(it.id, it.slug, it.name, it.tagline, it.sortOrder, it.isActive) }

    override suspend fun saveProduct(draft: ProductDraft): String {
        val values = buildJsonObject {
            put("category_id", draft.categoryId)
            put("name", draft.name.trim())
            put("description", draft.description.trim())
            put("price_paise", draft.pricePaise)
            put("is_active", draft.isActive)
        }
        val id = draft.id
        return if (id == null) {
            client.from("products").insert(values) { select(Columns.list("id")) }.decodeSingle<IdRow>().id
        } else {
            client.from("products").update(values) { filter { eq("id", id) } }
            id
        }
    }

    override suspend fun saveCategory(draft: CategoryDraft) {
        val values = buildJsonObject {
            put("name", draft.name.trim())
            put("tagline", draft.tagline.trim())
            put("sort_order", draft.sortOrder)
            put("is_active", draft.isActive)
        }
        val id = draft.id
        if (id == null) {
            val withSlug = JsonObject(values + ("slug" to JsonPrimitive(AdminInput.slugFromName(draft.name))))
            client.from("categories").insert(withSlug)
        } else {
            client.from("categories").update(values) { filter { eq("id", id) } }
        }
    }

    override suspend fun uploadProductPhoto(productId: String, jpegBytes: ByteArray) {
        // A new file name each time, so phones never show an old cached photo.
        val path = "products/$productId/${System.currentTimeMillis()}.jpg"
        client.storage.from(SupabaseConfig.PRODUCT_IMAGES_BUCKET).upload(path, jpegBytes) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        client.from("products").update(buildJsonObject { put("image_path", path) }) {
            filter { eq("id", productId) }
        }
    }

    override suspend fun recordProduction(productId: String, quantity: Int, note: String?) {
        client.postgrest.rpc(
            "record_production",
            buildJsonObject {
                put("p_product_id", productId)
                put("p_quantity", quantity)
                put("p_note", note?.trim()?.ifBlank { null })
            },
        )
    }

    override suspend fun adjustStock(productId: String, quantityDelta: Int, reason: String) {
        client.postgrest.rpc(
            "adjust_stock",
            buildJsonObject {
                put("p_product_id", productId)
                put("p_quantity_delta", quantityDelta)
                put("p_reason", reason.trim())
            },
        )
    }

    override suspend fun recordWastage(productId: String, quantity: Int, reason: String) {
        client.postgrest.rpc(
            "record_wastage",
            buildJsonObject {
                put("p_product_id", productId)
                put("p_quantity", quantity)
                put("p_reason", reason.trim())
            },
        )
    }

    override suspend fun stockHistory(productId: String): List<StockMovement> =
        client.from("stock_movements")
            .select(Columns.list("movement_type", "quantity_delta", "reason", "created_at")) {
                filter { eq("product_id", productId) }
                order("created_at", SortOrder.DESCENDING)
                limit(50)
            }
            .decodeList<MovementRow>()
            .map { StockMovement(StockMovementType.fromDatabase(it.movementType), it.quantityDelta, it.reason, it.createdAt) }

    override suspend fun recordStockCount(counts: Map<String, Int>, note: String?): List<StockDifference> {
        val result = client.postgrest.rpc(
            "record_stock_count",
            buildJsonObject {
                putJsonArray("p_counts") {
                    counts.forEach { (productId, counted) ->
                        addJsonObject {
                            put("product_id", productId)
                            put("counted", counted)
                        }
                    }
                }
                put("p_note", note?.trim()?.ifBlank { null })
            },
        ).decodeAs<JsonObject>()
        return result["differences"]?.jsonArray.orEmpty().map { element ->
            val d = element.jsonObject
            StockDifference(
                productName = d["name"]?.jsonPrimitive?.content.orEmpty(),
                expected = d["expected"]?.jsonPrimitive?.int ?: 0,
                counted = d["counted"]?.jsonPrimitive?.int ?: 0,
                difference = d["difference"]?.jsonPrimitive?.int ?: 0,
                valuePaise = d["value_paise"]?.jsonPrimitive?.long ?: 0,
            )
        }
    }

    private val orderColumns = Columns.raw(
        "id, order_number, status, total_paise, pickup_note, created_at, customer_id, " +
            "order_items(product_name, unit_price_paise, quantity, line_total_paise)",
    )

    private fun OrderRow.toOrder() = Order(
        id = id,
        number = orderNumber,
        status = OrderStatus.fromDatabase(status),
        totalPaise = totalPaise,
        pickupNote = pickupNote,
        createdAt = createdAt,
        items = items.map { OrderItem(it.productName, it.unitPricePaise, it.quantity, it.lineTotalPaise) },
    )

    override suspend fun appOrders(): List<AdminOrder> {
        val rows = client.from("orders")
            .select(orderColumns) {
                filter { eq("channel", "app") }
                order("created_at", SortOrder.DESCENDING)
                limit(150)
            }
            .decodeList<OrderRow>()
        val customerIds = rows.mapNotNull { it.customerId }.distinct()
        val profiles = if (customerIds.isEmpty()) {
            emptyMap()
        } else {
            client.from("profiles")
                .select(Columns.list("id", "full_name", "phone")) { filter { isIn("id", customerIds) } }
                .decodeList<ProfileRow>()
                .associateBy { it.id }
        }
        return rows.map { row ->
            val profile = row.customerId?.let(profiles::get)
            AdminOrder(
                order = row.toOrder(),
                customerName = profile?.fullName?.ifBlank { null } ?: "Customer",
                customerPhone = profile?.phone,
            )
        }
    }

    override suspend fun setOrderStatus(orderId: String, status: OrderStatus) {
        val value = when (status) {
            OrderStatus.READY_FOR_PICKUP -> "ready_for_pickup"
            OrderStatus.COLLECTED -> "collected"
            else -> error("Only ready-for-pickup and collected can be set here")
        }
        client.postgrest.rpc(
            "set_order_status",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_status", value)
            },
        )
    }

    override suspend fun recordCounterSale(
        quantities: Map<String, Int>,
        method: CounterPaymentMethod,
        note: String?,
    ): Long {
        val result = client.postgrest.rpc(
            "record_counter_sale",
            buildJsonObject {
                putJsonArray("p_items") {
                    quantities.forEach { (productId, quantity) ->
                        addJsonObject {
                            put("product_id", productId)
                            put("quantity", quantity)
                        }
                    }
                }
                put("p_payment_method", method.dbValue)
                put("p_note", note?.trim()?.ifBlank { null })
            },
        ).decodeAs<JsonObject>()
        return result["order_number"]?.jsonPrimitive?.long ?: 0
    }

    override suspend fun counterSalesToday(): List<CounterSale> {
        val startOfToday = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toOffsetDateTime().toString()
        val rows = client.from("orders")
            .select(orderColumns) {
                filter {
                    eq("channel", "counter")
                    gte("created_at", startOfToday)
                }
                order("created_at", SortOrder.DESCENDING)
            }
            .decodeList<OrderRow>()
        val methods = if (rows.isEmpty()) {
            emptyMap()
        } else {
            client.from("payments")
                .select(Columns.list("order_id", "provider")) { filter { isIn("order_id", rows.map { it.id }) } }
                .decodeList<PaymentRow>()
                .associate { it.orderId to CounterPaymentMethod.fromDatabase(it.provider) }
        }
        return rows.map { row ->
            CounterSale(
                id = row.id,
                number = row.orderNumber,
                totalPaise = row.totalPaise,
                method = methods[row.id],
                isUndone = row.status == "cancelled",
                note = row.pickupNote,
                createdAt = row.createdAt,
                items = row.items.map { OrderItem(it.productName, it.unitPricePaise, it.quantity, it.lineTotalPaise) },
            )
        }
    }

    override suspend fun undoCounterSale(orderId: String, reason: String) {
        client.postgrest.rpc(
            "void_counter_sale",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_reason", reason.trim())
            },
        )
    }
}
