package com.vsjbakery.app.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsjbakery.app.data.RemoteErrors
import com.vsjbakery.app.data.cart.CartRepository
import com.vsjbakery.app.data.catalog.CatalogRepository
import com.vsjbakery.app.data.orders.OrderRepository
import com.vsjbakery.app.domain.model.CartLine
import com.vsjbakery.app.domain.model.CartRules
import com.vsjbakery.app.domain.model.Catalog
import com.vsjbakery.app.domain.model.PlacedOrder
import com.vsjbakery.app.domain.model.Product
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Error(val message: String) : CatalogState
    data class Ready(val catalog: Catalog) : CatalogState
}

data class CheckoutState(
    val isPlacing: Boolean = false,
    val errorMessage: String? = null,
    /** Set when the server has created the order; the screen navigates, then clears it. */
    val placedOrder: PlacedOrder? = null,
)

data class CartUiState(
    val lines: List<CartLine> = emptyList(),
    val totalPaise: Long = 0,
    val itemCount: Int = 0,
    val canCheckOut: Boolean = false,
)

/** Shared by the home, category and cart screens: the catalogue, the cart and checkout. */
class ShopViewModel(
    private val catalogRepository: CatalogRepository?,
    private val cart: CartRepository,
    private val orderRepository: OrderRepository?,
) : ViewModel() {

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _checkout = MutableStateFlow(CheckoutState())
    val checkout: StateFlow<CheckoutState> = _checkout.asStateFlow()

    val cartQuantities: StateFlow<Map<String, Int>> = cart.quantities

    val cartState: StateFlow<CartUiState> = combine(cart.quantities, _catalog) { quantities, state ->
        val lines = CartRules.lines(quantities, (state as? CatalogState.Ready)?.catalog)
        CartUiState(
            lines = lines,
            totalPaise = CartRules.totalPaise(lines),
            itemCount = CartRules.itemCount(quantities),
            canCheckOut = state is CatalogState.Ready && CartRules.canCheckOut(lines),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState())

    init {
        refresh()
    }

    /** Reloads products, prices and stock from the server. */
    fun refresh() {
        val repository = catalogRepository
        if (repository == null) {
            _catalog.value = CatalogState.Error("The shop is not set up in this version of the app.")
            return
        }
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                _catalog.value = CatalogState.Ready(repository.loadCatalog())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing the last good catalogue if we have one.
                if (_catalog.value !is CatalogState.Ready) {
                    _catalog.value = CatalogState.Error(RemoteErrors.message(e))
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addToCart(product: Product) {
        val current = cart.quantities.value[product.id] ?: 0
        cart.setQuantity(product.id, CartRules.increment(current, product))
    }

    fun decrease(productId: String) {
        val current = cart.quantities.value[productId] ?: return
        cart.setQuantity(productId, current - 1)
    }

    fun remove(productId: String) = cart.remove(productId)

    /** Lowers a line to what is actually in stock (or removes it if sold out). */
    fun fixLine(line: CartLine) {
        val available = line.product?.let { CartRules.maxQuantity(it) } ?: 0
        cart.setQuantity(line.productId, minOf(line.quantity, available))
    }

    fun placeOrder(pickupNote: String) {
        val repository = orderRepository ?: return
        val quantities = cart.quantities.value
        if (quantities.isEmpty() || _checkout.value.isPlacing) return
        _checkout.value = CheckoutState(isPlacing = true)
        viewModelScope.launch {
            try {
                val placed = repository.placeOrder(quantities, pickupNote)
                cart.clear()
                _checkout.value = CheckoutState(placedOrder = placed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _checkout.value = CheckoutState(errorMessage = RemoteErrors.message(e))
            }
            // Stock may have changed (for example, something sold out): show the latest.
            refresh()
        }
    }

    fun onOrderPlacedHandled() {
        _checkout.value = CheckoutState()
    }

    fun dismissCheckoutError() {
        _checkout.value = _checkout.value.copy(errorMessage = null)
    }
}
