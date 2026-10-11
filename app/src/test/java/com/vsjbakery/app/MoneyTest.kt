package com.vsjbakery.app

import com.vsjbakery.app.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun formatsSmallAmounts() {
        assertEquals("₹0.00", Money.formatRupees(0))
        assertEquals("₹0.05", Money.formatRupees(5))
        assertEquals("₹45.00", Money.formatRupees(4500))
        assertEquals("₹999.99", Money.formatRupees(99999))
    }

    @Test
    fun usesIndianDigitGrouping() {
        assertEquals("₹1,000.00", Money.formatRupees(100000))
        assertEquals("₹12,500.50", Money.formatRupees(1250050))
        assertEquals("₹1,23,456.00", Money.formatRupees(12345600))
        assertEquals("₹12,34,56,789.00", Money.formatRupees(123456789_00))
    }

    @Test
    fun formatsNegativeAmountsForRefunds() {
        assertEquals("-₹250.00", Money.formatRupees(-25000))
    }
}
