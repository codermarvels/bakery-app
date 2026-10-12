package com.vsjbakery.app

import com.vsjbakery.app.domain.model.AdminInput
import com.vsjbakery.app.domain.model.CounterPaymentMethod
import com.vsjbakery.app.domain.model.CounterSale
import com.vsjbakery.app.domain.model.CounterTotals
import com.vsjbakery.app.domain.model.StockMovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdminInputTest {

    @Test
    fun parsesRupeePrices() {
        assertEquals(4500L, AdminInput.parseRupees("45"))
        assertEquals(4550L, AdminInput.parseRupees("45.5"))
        assertEquals(4505L, AdminInput.parseRupees("45.05"))
        assertEquals(125000L, AdminInput.parseRupees("1,250.00"))
        assertEquals(4500L, AdminInput.parseRupees(" ₹45 "))
        assertEquals(0L, AdminInput.parseRupees("0"))
        assertEquals(10_000_000L, AdminInput.parseRupees("100000"))
    }

    @Test
    fun rejectsInvalidPrices() {
        assertNull(AdminInput.parseRupees(""))
        assertNull(AdminInput.parseRupees("abc"))
        assertNull(AdminInput.parseRupees("-5"))
        assertNull(AdminInput.parseRupees("45.555"))
        assertNull(AdminInput.parseRupees("100001"))
        assertNull(AdminInput.parseRupees("12.3.4"))
    }

    @Test
    fun formatsPricesForEditing() {
        assertEquals("45", AdminInput.rupeesForInput(4500))
        assertEquals("45.50", AdminInput.rupeesForInput(4550))
        assertEquals("0.05", AdminInput.rupeesForInput(5))
    }

    @Test
    fun parsesQuantities() {
        assertEquals(12, AdminInput.parseQuantity("12"))
        assertNull(AdminInput.parseQuantity("0"))
        assertEquals(0, AdminInput.parseQuantity("0", min = 0))
        assertNull(AdminInput.parseQuantity("1.5"))
        assertNull(AdminInput.parseQuantity("-2", min = 0))
    }

    @Test
    fun makesCategorySlugs() {
        assertEquals("tea-coffee", AdminInput.slugFromName("Tea & Coffee"))
        assertEquals("cakes", AdminInput.slugFromName("  Cakes!! "))
        assertEquals("category", AdminInput.slugFromName("***"))
    }

    @Test
    fun counterTotalsExcludeUndoneSales() {
        fun sale(total: Long, method: CounterPaymentMethod, undone: Boolean = false) =
            CounterSale("id$total", 1, total, method, undone, null, "", emptyList())
        val totals = CounterTotals.of(
            listOf(
                sale(10000, CounterPaymentMethod.CASH),
                sale(2500, CounterPaymentMethod.CASH),
                sale(4500, CounterPaymentMethod.UPI),
                sale(9900, CounterPaymentMethod.CARD, undone = true),
            ),
        )
        assertEquals(3, totals.saleCount)
        assertEquals(17000L, totals.totalPaise)
        assertEquals(12500L, totals.byMethod[CounterPaymentMethod.CASH])
        assertEquals(4500L, totals.byMethod[CounterPaymentMethod.UPI])
        assertEquals(0L, totals.byMethod[CounterPaymentMethod.CARD])
    }

    @Test
    fun mapsDatabaseValues() {
        assertEquals(CounterPaymentMethod.UPI, CounterPaymentMethod.fromDatabase("upi"))
        assertNull(CounterPaymentMethod.fromDatabase("razorpay"))
        assertEquals(StockMovementType.WASTAGE, StockMovementType.fromDatabase("wastage"))
    }
}
