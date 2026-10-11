package com.vsjbakery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.vsjbakery.app.data.payments.CheckoutResult
import com.vsjbakery.app.ui.VsjBakeryApp
import com.vsjbakery.app.ui.theme.VsjBakeryTheme
import org.json.JSONObject

/**
 * The app's only screen container. Razorpay Checkout reports its result to the Activity
 * that opened it, so this class passes that result on. The result is only a claim from
 * the phone: the server verifies every payment before an order is marked paid.
 */
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val checkoutResults get() = (application as VsjBakeryApplication).container.checkoutResults

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VsjBakeryTheme {
                VsjBakeryApp()
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        val orderId = data?.orderId
        val paymentId = razorpayPaymentId ?: data?.paymentId
        val signature = data?.signature
        if (orderId.isNullOrBlank() || paymentId.isNullOrBlank() || signature.isNullOrBlank()) {
            checkoutResults.report(CheckoutResult.Failed(cancelledByUser = false, description = "incomplete response"))
        } else {
            checkoutResults.report(CheckoutResult.Completed(orderId, paymentId, signature))
        }
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) {
        checkoutResults.report(
            CheckoutResult.Failed(
                cancelledByUser = code == Checkout.PAYMENT_CANCELED,
                description = describeCheckoutError(code, response),
            ),
        )
    }
}

/**
 * Razorpay sends error details as JSON, e.g.
 * {"error":{"code":"BAD_REQUEST_ERROR","description":"Payment failed","reason":"payment_failed"}}.
 * Shown to the customer so problems can be understood and reported.
 */
internal fun describeCheckoutError(code: Int, response: String?): String {
    val error = runCatching { JSONObject(response.orEmpty()).optJSONObject("error") }.getOrNull()
    val description = error?.optString("description")?.takeIf { it.isNotBlank() && it != "null" }
    val reason = error?.optString("reason")?.takeIf { it.isNotBlank() && it != "null" }
    return listOfNotNull(description, reason?.let { "reason: $it" }, "code $code").joinToString(", ")
}
