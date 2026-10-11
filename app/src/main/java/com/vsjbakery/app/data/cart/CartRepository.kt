package com.vsjbakery.app.data.cart

import com.vsjbakery.app.domain.model.CartRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The cart: product id → quantity. It lives in memory for as long as the app is running.
 * Prices are never stored here; they are always read fresh from the server.
 */
class CartRepository {
    private val _quantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val quantities: StateFlow<Map<String, Int>> = _quantities.asStateFlow()

    fun setQuantity(productId: String, quantity: Int) {
        _quantities.update { current ->
            val clamped = quantity.coerceAtMost(CartRules.MAX_PER_ITEM)
            if (clamped <= 0) current - productId else current + (productId to clamped)
        }
    }

    fun remove(productId: String) = setQuantity(productId, 0)

    fun clear() {
        _quantities.value = emptyMap()
    }
}
