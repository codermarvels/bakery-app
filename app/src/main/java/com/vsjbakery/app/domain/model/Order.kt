package com.vsjbakery.app.domain.model

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class OrderStatus(val label: String) {
    PENDING_PAYMENT("Awaiting payment"),
    PAID("Paid"),
    READY_FOR_PICKUP("Ready for pickup"),
    COLLECTED("Collected"),
    CANCELLED("Cancelled"),
    ;

    companion object {
        fun fromDatabase(value: String): OrderStatus = when (value) {
            "paid" -> PAID
            "ready_for_pickup" -> READY_FOR_PICKUP
            "collected" -> COLLECTED
            "cancelled" -> CANCELLED
            else -> PENDING_PAYMENT
        }
    }
}

data class OrderItem(
    val productName: String,
    val unitPricePaise: Long,
    val quantity: Int,
    val lineTotalPaise: Long,
)

data class Order(
    val id: String,
    val number: Long,
    val status: OrderStatus,
    val totalPaise: Long,
    val pickupNote: String?,
    val createdAt: String,
    val items: List<OrderItem>,
) {
    val canCancel: Boolean get() = status == OrderStatus.PENDING_PAYMENT
}

/**
 * What the server returns after checkout. [addedToExisting] is true when the items were
 * added to the customer's existing unpaid order instead of starting a new one.
 */
data class PlacedOrder(
    val orderId: String,
    val orderNumber: Long,
    val totalPaise: Long,
    val addedToExisting: Boolean = false,
)

object OrderDates {
    private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)

    /** Turns the server's timestamp into local time, e.g. "11 Oct 2026, 5:30 PM". */
    fun display(timestamp: String, zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching { OffsetDateTime.parse(timestamp).atZoneSameInstant(zone).format(formatter) }
            .getOrDefault(timestamp)
}
