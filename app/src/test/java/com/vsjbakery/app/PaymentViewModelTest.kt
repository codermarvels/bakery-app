package com.vsjbakery.app

import com.vsjbakery.app.data.payments.CheckoutResult
import com.vsjbakery.app.data.payments.CheckoutResults
import com.vsjbakery.app.data.payments.PaymentException
import com.vsjbakery.app.data.payments.PaymentOutcome
import com.vsjbakery.app.data.payments.PaymentRepository
import com.vsjbakery.app.data.payments.PaymentResults
import com.vsjbakery.app.data.payments.PaymentSession
import com.vsjbakery.app.ui.payments.PaymentState
import com.vsjbakery.app.ui.payments.PaymentViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Stand-in for the payments server, used only by these tests. */
private class FakePaymentRepository : PaymentRepository {
    var startError: Exception? = null
    var verifyResult: PaymentOutcome = PaymentOutcome.Paid(12)
    var verifyError: Exception? = null
    val verified = mutableListOf<Triple<String, String, String>>()

    override suspend fun startPayment(orderId: String): PaymentSession {
        startError?.let { throw it }
        return PaymentSession("rzp_test_key", "order_R1", 57500, 12, "asha@test.local")
    }

    override suspend fun verifyPayment(razorpayOrderId: String, razorpayPaymentId: String, signature: String): PaymentOutcome {
        verified += Triple(razorpayOrderId, razorpayPaymentId, signature)
        verifyError?.let { throw it }
        return verifyResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun openCheckout(): Triple<FakePaymentRepository, CheckoutResults, PaymentViewModel> {
        val repo = FakePaymentRepository()
        val results = CheckoutResults()
        val vm = PaymentViewModel(repo, results)
        vm.pay("order-uuid")
        vm.onCheckoutOpened()
        return Triple(repo, results, vm)
    }

    @Test
    fun payAsksServerForSessionThenOpensCheckout() = runTest {
        val vm = PaymentViewModel(FakePaymentRepository(), CheckoutResults())
        vm.pay("order-uuid")
        val state = vm.state.value
        assertTrue(state is PaymentState.ReadyToOpen)
        assertEquals(57500L, (state as PaymentState.ReadyToOpen).session.amountPaise)
        vm.onCheckoutOpened()
        assertTrue(vm.state.value is PaymentState.InCheckout)
    }

    @Test
    fun successOnThePhoneIsSentToTheServerForVerification() = runTest {
        val (repo, results, vm) = openCheckout()
        results.report(CheckoutResult.Completed("order_R1", "pay_1", "sig"))
        assertEquals(listOf(Triple("order_R1", "pay_1", "sig")), repo.verified)
        assertEquals(PaymentState.Finished(PaymentOutcome.Paid(12), 12), vm.state.value)
    }

    @Test
    fun orderIsOnlyPaidIfTheServerSaysSo() = runTest {
        val (repo, results, vm) = openCheckout()
        repo.verifyResult = PaymentOutcome.Pending
        results.report(CheckoutResult.Completed("order_R1", "pay_1", "sig"))
        assertEquals(PaymentState.Finished(PaymentOutcome.Pending, 12), vm.state.value)
    }

    @Test
    fun unreachableServerShowsPendingNotPaid() = runTest {
        val (repo, results, vm) = openCheckout()
        repo.verifyError = IOException("offline")
        results.report(CheckoutResult.Completed("order_R1", "pay_1", "sig"))
        assertEquals(PaymentState.Finished(PaymentOutcome.Pending, 12), vm.state.value)
    }

    @Test
    fun cancelledCheckoutIsReported() = runTest {
        val (repo, results, vm) = openCheckout()
        results.report(CheckoutResult.Failed(cancelledByUser = true, description = null))
        assertTrue(vm.state.value is PaymentState.Failed)
        assertTrue(repo.verified.isEmpty())
    }

    @Test
    fun serverRefusalIsShown() = runTest {
        val repo = FakePaymentRepository().apply { startError = PaymentException("Plum Cake is sold out") }
        val vm = PaymentViewModel(repo, CheckoutResults())
        vm.pay("order-uuid")
        assertEquals(PaymentState.Failed("Plum Cake is sold out"), vm.state.value)
        vm.dismiss()
        assertEquals(PaymentState.Idle, vm.state.value)
    }

    @Test
    fun serverResultCodesMapToOutcomes() {
        assertEquals(PaymentOutcome.Paid(5), PaymentResults.outcome("paid", 5, null, null))
        assertEquals(PaymentOutcome.Paid(5), PaymentResults.outcome("already_confirmed", 5, null, null))
        assertEquals(
            PaymentOutcome.Refunded(5, "sold out", refundSent = true),
            PaymentResults.outcome("refund_required", 5, "sold out", "processed"),
        )
        assertEquals(
            PaymentOutcome.Refunded(5, "sold out", refundSent = false),
            PaymentResults.outcome("refund_required", 5, "sold out", "failed"),
        )
        assertEquals(PaymentOutcome.Pending, PaymentResults.outcome("not_captured", 5, null, null))
    }
}
