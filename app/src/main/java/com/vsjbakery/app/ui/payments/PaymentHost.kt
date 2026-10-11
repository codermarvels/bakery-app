package com.vsjbakery.app.ui.payments

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.razorpay.Checkout
import com.vsjbakery.app.data.payments.PaymentOutcome
import com.vsjbakery.app.data.payments.PaymentSession
import com.vsjbakery.app.domain.model.Money
import org.json.JSONObject

/**
 * Shows payment progress and results anywhere in the app, and opens Razorpay Checkout
 * when the server has prepared a payment. [onFinished] lets screens refresh their data.
 */
@Composable
fun PaymentHost(
    state: PaymentState,
    onCheckoutOpened: () -> Unit,
    onCheckoutCouldNotOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val activity = LocalActivity.current

    LaunchedEffect(state) {
        when (state) {
            is PaymentState.ReadyToOpen -> {
                if (activity == null) {
                    onCheckoutCouldNotOpen("Could not open the payment screen.")
                } else {
                    runCatching { openRazorpayCheckout(activity, state.session) }
                        .onSuccess { onCheckoutOpened() }
                        .onFailure { onCheckoutCouldNotOpen("Could not open the payment screen.") }
                }
            }
            is PaymentState.Finished, is PaymentState.Failed -> onFinished()
            else -> Unit
        }
    }

    when (state) {
        PaymentState.Starting -> ProgressDialog("Preparing secure payment…")
        is PaymentState.Verifying -> ProgressDialog("Confirming your payment with the bank…")
        is PaymentState.Finished -> ResultDialog(
            title = when (state.outcome) {
                is PaymentOutcome.Paid -> "Payment successful"
                is PaymentOutcome.Refunded -> "Payment refunded"
                PaymentOutcome.Pending -> "Payment being confirmed"
            },
            message = when (val outcome = state.outcome) {
                is PaymentOutcome.Paid ->
                    "Order #${outcome.orderNumber} is paid. We'll have it ready for pickup at the VSJ BAKERY counter."
                is PaymentOutcome.Refunded ->
                    "We're sorry — order #${outcome.orderNumber} could not be fulfilled (${outcome.reason}). " +
                        if (outcome.refundSent) {
                            "Your money has been refunded and will reach your account in a few days."
                        } else {
                            "Your refund is being processed by the bakery."
                        }
                PaymentOutcome.Pending ->
                    "We haven't received confirmation from the bank yet. If money was taken, the order " +
                        "will be marked paid automatically within a few minutes. Check My orders."
            },
            onDismiss = onDismiss,
        )
        is PaymentState.Failed -> ResultDialog(title = "Payment not completed", message = state.message, onDismiss = onDismiss)
        else -> Unit
    }
}

private fun openRazorpayCheckout(activity: Activity, session: PaymentSession) {
    val checkout = Checkout()
    checkout.setKeyID(session.keyId)
    val options = JSONObject().apply {
        put("name", "VSJ BAKERY")
        put("description", "Pickup order #${session.orderNumber} · ${Money.formatRupees(session.amountPaise)}")
        put("order_id", session.razorpayOrderId)
        put("amount", session.amountPaise)
        put("currency", "INR")
        put("theme", JSONObject().put("color", "#4E342E"))
        put("retry", JSONObject().put("enabled", true).put("max_count", 3))
        session.customerEmail?.let { put("prefill", JSONObject().put("email", it)) }
    }
    checkout.open(activity, options)
}

@Composable
private fun ProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(16.dp))
                Text(message)
            }
        },
    )
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
