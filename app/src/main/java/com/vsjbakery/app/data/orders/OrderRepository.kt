package com.vsjbakery.app.data.orders

import com.vsjbakery.app.domain.model.Order
import com.vsjbakery.app.domain.model.OrderItem
import com.vsjbakery.app.domain.model.OrderRefund
import com.vsjbakery.app.domain.model.OrderStatus
import com.vsjbakery.app.domain.model.RefundState
import com.vsjbakery.app.domain.model.PlacedOrder
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as SortOrder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

interface OrderRepository {
    /**
     * Sends product ids and quantities only; the server decides prices and the total.
     * If the customer already has an unpaid order, the server adds the items to it.
     */
    suspend fun placeOrder(quantities: Map<String, Int>, pickupNote: String?): PlacedOrder

    suspend fun myOrders(): List<Order>

    suspend fun cancelOrder(orderId: String)
}

@Serializable
private data class PlacedOrderRow(
    @SerialName("order_id") val orderId: String,
    @SerialName("order_number") val orderNumber: Long,
    @SerialName("total_paise") val totalPaise: Long,
    @SerialName("added_to_existing") val addedToExisting: Boolean = false,
)

@Serializable
private data class OrderItemRow(
    @SerialName("product_name") val productName: String,
    @SerialName("unit_price_paise") val unitPricePaise: Long,
    val quantity: Int,
    @SerialName("line_total_paise") val lineTotalPaise: Long,
)

@Serializable
private data class RefundRow(
    @SerialName("amount_paise") val amountPaise: Long,
    val status: String,
    @SerialName("processed_at") val processedAt: String? = null,
    val reason: String? = null,
)

@Serializable
private data class OrderRow(
    val id: String,
    @SerialName("order_number") val orderNumber: Long,
    val status: String,
    @SerialName("total_paise") val totalPaise: Long,
    @SerialName("pickup_note") val pickupNote: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("order_items") val items: List<OrderItemRow> = emptyList(),
    val refunds: List<RefundRow> = emptyList(),
)

class SupabaseOrderRepository(private val client: SupabaseClient) : OrderRepository {

    override suspend fun placeOrder(quantities: Map<String, Int>, pickupNote: String?): PlacedOrder {
        val params = buildJsonObject {
            putJsonArray("p_items") {
                quantities.forEach { (productId, quantity) ->
                    addJsonObject {
                        put("product_id", productId)
                        put("quantity", quantity)
                    }
                }
            }
            put("p_pickup_note", pickupNote?.trim()?.ifBlank { null })
        }
        val row = client.postgrest.rpc("place_order", params).decodeAs<PlacedOrderRow>()
        return PlacedOrder(row.orderId, row.orderNumber, row.totalPaise, row.addedToExisting)
    }

    override suspend fun myOrders(): List<Order> =
        // Row Level Security returns only the signed-in customer's own orders.
        client.from("orders")
            .select(
                Columns.raw(
                    "id, order_number, status, total_paise, pickup_note, created_at, " +
                        "order_items(product_name, unit_price_paise, quantity, line_total_paise), " +
                        "refunds(amount_paise, status, processed_at, reason)",
                ),
            ) {
                order("created_at", SortOrder.DESCENDING)
                limit(50)
            }
            .decodeList<OrderRow>()
            .map { row ->
                Order(
                    id = row.id,
                    number = row.orderNumber,
                    status = OrderStatus.fromDatabase(row.status),
                    totalPaise = row.totalPaise,
                    pickupNote = row.pickupNote,
                    createdAt = row.createdAt,
                    items = row.items.map {
                        OrderItem(it.productName, it.unitPricePaise, it.quantity, it.lineTotalPaise)
                    },
                    refund = row.refunds.firstOrNull()?.let { r ->
                        RefundState.fromDatabase(r.status)?.let { OrderRefund(r.amountPaise, it, r.processedAt, r.reason) }
                    },
                )
            }

    override suspend fun cancelOrder(orderId: String) {
        client.postgrest.rpc("cancel_my_order", buildJsonObject { put("p_order_id", orderId) })
    }
}

