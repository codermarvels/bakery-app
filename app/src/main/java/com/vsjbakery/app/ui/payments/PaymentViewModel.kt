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
        _state.value = PaymentState.Starting
        viewModelScope.launch {
            _state.value = try {
                PaymentState.ReadyToOpen(repo.startPayment(orderId))
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
            is CheckoutResult.Failed -> _state.value = PaymentState.Failed(
                if (result.cancelledByUser) {
                    "Payment cancelled. Your order is still waiting for payment."
                } else {
                    "Payment did not go through. No money was taken for this attempt. " +
                        (result.description?.let { "($it)" } ?: "")
                },
            )
            is CheckoutResult.Completed -> verify(result, session?.orderNumber ?: 0)
        }
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
        _state.value = PaymentState.Idle
    }
}
