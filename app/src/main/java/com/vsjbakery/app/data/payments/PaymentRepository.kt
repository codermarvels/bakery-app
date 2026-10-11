package com.vsjbakery.app.data.payments

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** What the app needs to open Razorpay Checkout. Created by the server. */
data class PaymentSession(
    val keyId: String,
    val razorpayOrderId: String,
    val amountPaise: Long,
    val orderNumber: Long,
    val customerEmail: String?,
)

/** The server's verdict after checking the payment with Razorpay. */
sealed interface PaymentOutcome {
    data class Paid(val orderNumber: Long) : PaymentOutcome

    /** Money was received but the order could not be fulfilled, so it was sent back. */
    data class Refunded(val orderNumber: Long, val reason: String, val refundSent: Boolean) : PaymentOutcome

    /** Razorpay has not reported the money as captured yet; the webhook will finish it. */
    data object Pending : PaymentOutcome
}

class PaymentException(message: String) : Exception(message)

interface PaymentRepository {
    /**
     * Asks the server to check with Razorpay whether this order was already paid (for
     * example the phone missed the success message). Returns null if nothing was paid.
     */
    suspend fun syncPayment(orderId: String): PaymentOutcome?

    suspend fun startPayment(orderId: String): PaymentSession

    suspend fun verifyPayment(razorpayOrderId: String, razorpayPaymentId: String, signature: String): PaymentOutcome
}

/**
 * Talks only to our own `payments` Edge Function. The phone never sees the Razorpay key
 * secret and never decides that an order is paid; it only passes Razorpay's response on
 * to the server, which checks it.
 */
class SupabasePaymentRepository(private val client: SupabaseClient) : PaymentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun call(body: JsonObject): JsonObject {
        val response = client.functions.invoke(function = FUNCTION, body = body)
        val result = json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (result["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            throw PaymentException(result.string("error") ?: "Payment service error. Please try again.")
        }
        return result
    }

    override suspend fun syncPayment(orderId: String): PaymentOutcome? {
        val result = call(
            buildJsonObject {
                put("action", "sync")
                put("order_id", orderId)
            },
        )
        val outcome = PaymentResults.outcome(
            result = result.string("result"),
            orderNumber = result.long("order_number") ?: 0,
            reason = result.string("reason"),
            refundStatus = result.string("refund_status"),
        )
        return outcome.takeIf { it !is PaymentOutcome.Pending }
    }

    override suspend fun startPayment(orderId: String): PaymentSession {
        val result = call(
            buildJsonObject {
                put("action", "create")
                put("order_id", orderId)
            },
        )
        return PaymentSession(
            keyId = result.string("key_id") ?: throw PaymentException("Payments are not set up yet."),
            razorpayOrderId = result.string("razorpay_order_id") ?: throw PaymentException("Payment could not start."),
            amountPaise = result.long("amount_paise") ?: throw PaymentException("Payment could not start."),
            orderNumber = result.long("order_number") ?: 0,
            customerEmail = result.string("customer_email"),
        )
    }

    override suspend fun verifyPayment(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        signature: String,
    ): PaymentOutcome {
        val result = call(
            buildJsonObject {
                put("action", "verify")
                put("razorpay_order_id", razorpayOrderId)
                put("razorpay_payment_id", razorpayPaymentId)
                put("razorpay_signature", signature)
            },
        )
        return PaymentResults.outcome(
            result = result.string("result"),
            orderNumber = result.long("order_number") ?: 0,
            reason = result.string("reason"),
            refundStatus = result.string("refund_status"),
        )
    }

    private companion object {
        const val FUNCTION = "payments"
    }
}

/** Maps the server's result codes to outcomes (kept separate so it can be unit-tested). */
object PaymentResults {
    fun outcome(result: String?, orderNumber: Long, reason: String?, refundStatus: String?): PaymentOutcome =
        when (result) {
            "paid", "already_confirmed" -> PaymentOutcome.Paid(orderNumber)
            "refund_required", "refund_recorded" -> PaymentOutcome.Refunded(
                orderNumber = orderNumber,
                reason = reason ?: "The order could not be fulfilled",
                refundSent = refundStatus != "failed",
            )
            else -> PaymentOutcome.Pending
        }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.takeIf { it.isString }?.content

private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
