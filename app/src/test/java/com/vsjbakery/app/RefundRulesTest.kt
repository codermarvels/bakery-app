package com.vsjbakery.app

import com.vsjbakery.app.domain.model.AdminOrder
import com.vsjbakery.app.domain.model.Order
import com.vsjbakery.app.domain.model.OrderStatus
import com.vsjbakery.app.domain.model.RefundState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundRulesTest {
    private fun order(status: OrderStatus, paidOnline: Boolean = true, refund: RefundState? = null) = AdminOrder(
        order = Order("id", 1, status, 10000, null, "2026-10-12T10:00:00Z", emptyList()),
        customerName = "Asha",
        customerPhone = null,
        paidOnline = paidOnline,
        refund = refund,
    )

    @Test
    fun paidOnlineOrdersCanBeRefunded() {
        assertTrue(order(OrderStatus.PAID).canRefund)
        assertTrue(order(OrderStatus.READY_FOR_PICKUP).canRefund)
        assertTrue(order(OrderStatus.COLLECTED).canRefund)
    }

    @Test
    fun unpaidOrNotOnlineOrdersCannotBeRefunded() {
        assertFalse(order(OrderStatus.PENDING_PAYMENT, paidOnline = false).canRefund)
        assertFalse(order(OrderStatus.PAID, paidOnline = false).canRefund)
        assertFalse(order(OrderStatus.CANCELLED).canRefund)
    }

    @Test
    fun onlyRefundsThatDidNotGoThroughCanBeRetried() {
        assertTrue(order(OrderStatus.CANCELLED, refund = RefundState.FAILED).canRefund)
        assertTrue(order(OrderStatus.CANCELLED, refund = RefundState.PENDING).canRefund)
        assertFalse(order(OrderStatus.CANCELLED, refund = RefundState.PROCESSED).canRefund)
    }
}
