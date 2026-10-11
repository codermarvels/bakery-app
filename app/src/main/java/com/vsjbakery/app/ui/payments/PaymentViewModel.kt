package com.vsjbakery.app.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsjbakery.app.data.RemoteErrors
import com.vsjbakery.app.data.payments.CheckoutResult
import com.vsjbakery.app.data.payments.CheckoutResults
import com.vsjbakery.app.data.payments.PaymentOutcome
import com.vsjbakery.app.data.payments.PaymentRepository
import com.vsjbakery.app.data.payments.PaymentSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The steps of one payment, as shown to the customer. */
sealed interface PaymentState {
    data object Idle : PaymentState

    /** Asking the server to create the Razorpay order. */
    data object Starting : PaymentState

    /** The server is ready; the screen should open Razorpay Checkout with this session. */
    data class ReadyToOpen(val session: PaymentSession) : PaymentState

    /** Razorpay Checkout is open on screen. */
    data class InCheckout(val session: PaymentSession) : PaymentState

    /** Razorpay reported success; the server is checking it. */
    data class Verifying(val orderNumber: Long) : PaymentState

    data class Finished(val outcome: PaymentOutcome, val orderNumber: Long) : PaymentState

    data class Failed(val message: String) : PaymentState
}

class PaymentViewModel(
    private val repository: PaymentRepository?,
    checkoutResults: CheckoutResults,
) : ViewModel() {

    /** The order being paid for, so its payment can be re-checked if the phone reports an error. */
    private var currentOrderId: String? = null

    private val _state = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val state: StateFlow<PaymentState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            checkoutResults.results.collect(::onCheckoutResult)
        }
    }

    /** Starts paying for an unpaid order. The amount always comes from the server. */
    fun pay(orderId: String) {
        val repo = repository ?: run {
            _state.value = PaymentState.Failed("Payments are not set up in this version of the app.")
            return
        }
        if (_state.value !is PaymentState.Idle) return
        currentOrderId = orderId
        _state.value = PaymentState.Starting
        viewModelScope.launch {
            _state.value = try {
                // An earlier attempt may already have been paid; never charge twice.
                val alreadySettled = repo.syncPayment(orderId)
                if (alreadySettled != null) {
                    PaymentState.Finished(alreadySettled, orderNumberOf(alreadySettled))
                } else {
                    PaymentState.ReadyToOpen(repo.startPayment(orderId))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PaymentState.Failed(RemoteErrors.message(e))
            }
        }
    }

    fun onCheckoutOpened() {
        val current = _state.value
        if (current is PaymentState.ReadyToOpen) _state.value = PaymentState.InCheckout(current.session)
    }

    fun onCheckoutCouldNotOpen(message: String) {
        _state.value = PaymentState.Failed(message)
    }

    private fun onCheckoutResult(result: CheckoutResult) {
        val session = (_state.value as? PaymentState.InCheckout)?.session
        when (result) {
            is CheckoutResult.Failed -> checkAfterFailure(result, session?.orderNumber ?: 0)
            is CheckoutResult.Completed -> verify(result, session?.orderNumber ?: 0)
        }
    }

    /**
     * The phone said the payment failed, but the bank may still have taken the money
     * (this happens with some payment methods). Ask the server to check with Razorpay
     * before telling the customer it failed.
     */
    private fun checkAfterFailure(result: CheckoutResult.Failed, orderNumber: Long) {
        val failedMessage = if (result.cancelledByUser) {
            "Payment cancelled. Your order is still waiting for payment."
        } else {
            "Payment did not go through, so the order is still waiting for payment. You can try again." +
                (result.description?.let { "\n\nRazorpay said: $it" } ?: "")
        }
        val repo = repository
        val orderId = currentOrderId
        if (repo == null || orderId == null) {
            _state.value = PaymentState.Failed(failedMessage)
            return
        }
        _state.value = PaymentState.Verifying(orderNumber)
        viewModelScope.launch {
            _state.value = try {
                repo.syncPayment(orderId)
                    ?.let { PaymentState.Finished(it, orderNumberOf(it).takeIf { n -> n > 0 } ?: orderNumber) }
                    ?: PaymentState.Failed(failedMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PaymentState.Failed(failedMessage)
            }
        }
    }

    private fun orderNumberOf(outcome: PaymentOutcome): Long = when (outcome) {
        is PaymentOutcome.Paid -> outcome.orderNumber
        is PaymentOutcome.Refunded -> outcome.orderNumber
        PaymentOutcome.Pending -> 0
    }

    private fun verify(result: CheckoutResult.Completed, orderNumber: Long) {
        val repo = repository ?: return
        _state.value = PaymentState.Verifying(orderNumber)
        viewModelScope.launch {
            _state.value = try {
                val outcome = repo.verifyPayment(result.razorpayOrderId, result.razorpayPaymentId, result.signature)
                PaymentState.Finished(outcome, orderNumber)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // We could not reach the server to confirm. Razorpay also tells the server
                // directly (webhook), so the order will update by itself.
                PaymentState.Finished(PaymentOutcome.Pending, orderNumber)
            }
        }
    }

    fun dismiss() {
        currentOrderId = null
        _state.value = PaymentState.Idle
    }
}
