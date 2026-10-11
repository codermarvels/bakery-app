package com.vsjbakery.app

import com.vsjbakery.app.data.SupabaseConfig
import com.vsjbakery.app.data.cart.CartRepository
import com.vsjbakery.app.domain.model.Availability
import com.vsjbakery.app.domain.model.CartRules
import com.vsjbakery.app.domain.model.Catalog
import com.vsjbakery.app.domain.model.Category
import com.vsjbakery.app.domain.model.OrderDates
import com.vsjbakery.app.domain.model.OrderStatus
import com.vsjbakery.app.domain.model.Product
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartRulesTest {

    private val cakes = Category("c1", "cakes", "Cakes", "")
    private val cake = Product("p1", "c1", "Plum Cake", "", 45000, null, 5)
    private val puff = Product("p2", "c1", "Veg Puff", "", 2500, null, 30)
    private val soldOut = Product("p3", "c1", "Roll", "", 1500, null, 0)
    private val catalog = Catalog(listOf(cakes), listOf(cake, puff, soldOut))

    @Test
    fun totalUsesCurrentPrices() {
        val lines = CartRules.lines(mapOf("p1" to 2, "p2" to 4), catalog)
        assertEquals(100000L, CartRules.totalPaise(lines))
        assertTrue(CartRules.canCheckOut(lines))
    }

    @Test
    fun soldOutAndMissingProductsBlockCheckout() {
        val lines = CartRules.lines(mapOf("p3" to 1, "gone" to 1), catalog)
        assertEquals("Sold out", lines.first { it.productId == "p3" }.problem)
        assertEquals("No longer available", lines.first { it.productId == "gone" }.problem)
        assertFalse(CartRules.canCheckOut(lines))
        assertEquals(0L, CartRules.totalPaise(lines.filter { it.productId == "gone" }))
    }

    @Test
    fun moreThanStockIsFlagged() {
        val line = CartRules.lines(mapOf("p1" to 6), catalog).single()
        assertEquals("Only 5 left", line.problem)
    }

    @Test
    fun emptyCartCannotCheckOut() {
        assertFalse(CartRules.canCheckOut(emptyList()))
    }

    @Test
    fun incrementStopsAtStock() {
        assertEquals(5, CartRules.increment(5, cake))
        assertEquals(3, CartRules.increment(2, cake))
        assertEquals(30, CartRules.maxQuantity(puff))
    }

    @Test
    fun cartRepositoryAddsClampsAndRemoves() {
        val cart = CartRepository()
        cart.setQuantity("p1", 2)
        cart.setQuantity("p2", 500)
        assertEquals(mapOf("p1" to 2, "p2" to CartRules.MAX_PER_ITEM), cart.quantities.value)
        cart.setQuantity("p1", 0)
        assertEquals(setOf("p2"), cart.quantities.value.keys)
        assertEquals(CartRules.MAX_PER_ITEM, CartRules.itemCount(cart.quantities.value))
        cart.clear()
        assertTrue(cart.quantities.value.isEmpty())
    }

    @Test
    fun availabilityLabels() {
        assertEquals("Sold out", Availability.label(0))
        assertEquals("Only 3 left", Availability.label(3))
        assertEquals("In stock", Availability.label(20))
    }

    @Test
    fun orderStatusFromDatabase() {
        assertEquals(OrderStatus.PENDING_PAYMENT, OrderStatus.fromDatabase("pending_payment"))
        assertEquals(OrderStatus.READY_FOR_PICKUP, OrderStatus.fromDatabase("ready_for_pickup"))
        assertEquals(OrderStatus.CANCELLED, OrderStatus.fromDatabase("cancelled"))
    }

    @Test
    fun orderDatesShowLocalTime() {
        assertEquals(
            "11 Oct 2026, 1:00 PM",
            OrderDates.display("2026-10-11T07:30:00.123456+00:00", ZoneId.of("Asia/Kolkata")),
        )
        assertEquals("not a date", OrderDates.display("not a date"))
    }

    @Test
    fun productImageUrls() {
        val config = SupabaseConfig("https://abc.supabase.co", "sb_publishable_x")
        assertEquals(
            "https://abc.supabase.co/storage/v1/object/public/product-images/cakes/plum.jpg",
            config.productImageUrl("cakes/plum.jpg"),
        )
        assertNull(config.productImageUrl(null))
        assertNull(config.productImageUrl(" "))
        assertEquals("https://cdn.example.com/a.jpg", config.productImageUrl("https://cdn.example.com/a.jpg"))
    }
}
