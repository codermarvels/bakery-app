package com.vsjbakery.app.data.payments

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What Razorpay Checkout reported on the phone. This is NOT proof of payment. */
sealed interface CheckoutResult {
    /** Razorpay says the payment went through; the server must still verify it. */
    data class Completed(val razorpayOrderId: String, val razorpayPaymentId: String, val signature: String) :
        CheckoutResult

    data class Failed(val cancelledByUser: Boolean, val description: String?) : CheckoutResult
}

/**
 * Carries Razorpay's callback from MainActivity (where the SDK delivers it) to the
 * payment ViewModel.
 */
class CheckoutResults {
    private val _results = MutableSharedFlow<CheckoutResult>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: SharedFlow<CheckoutResult> = _results.asSharedFlow()

    fun report(result: CheckoutResult) {
        _results.tryEmit(result)
    }
}
