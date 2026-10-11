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
            CheckoutResult.Failed(cancelledByUser = code == Checkout.PAYMENT_CANCELED, description = null),
        )
    }
}
