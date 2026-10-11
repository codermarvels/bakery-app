package com.vsjbakery.app.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsjbakery.app.data.RemoteErrors
import com.vsjbakery.app.data.orders.OrderRepository
import com.vsjbakery.app.domain.model.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersState(
    val isLoading: Boolean = true,
    val orders: List<Order> = emptyList(),
    val errorMessage: String? = null,
    /** Id of the order currently being cancelled, to show a spinner on that card. */
    val cancellingId: String? = null,
)

class OrdersViewModel(private val repository: OrderRepository?) : ViewModel() {

    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    fun load() {
        val repo = repository ?: run {
            _state.value = OrdersState(isLoading = false, errorMessage = "Orders are not set up in this version of the app.")
            return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val orders = repo.myOrders()
                _state.update { it.copy(isLoading = false, orders = orders) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = RemoteErrors.message(e)) }
            }
        }
    }

    fun cancel(orderId: String) {
        val repo = repository ?: return
        if (_state.value.cancellingId != null) return
        _state.update { it.copy(cancellingId = orderId, errorMessage = null) }
        viewModelScope.launch {
            try {
                repo.cancelOrder(orderId)
                _state.update { it.copy(cancellingId = null) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(cancellingId = null, errorMessage = RemoteErrors.message(e)) }
            }
        }
    }
}
