package com.vsjbakery.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsjbakery.app.data.RemoteErrors
import com.vsjbakery.app.data.admin.AdminRepository
import com.vsjbakery.app.domain.model.AdminCategory
import com.vsjbakery.app.domain.model.AdminOrder
import com.vsjbakery.app.domain.model.AdminProduct
import com.vsjbakery.app.domain.model.CategoryDraft
import com.vsjbakery.app.domain.model.CounterPaymentMethod
import com.vsjbakery.app.domain.model.CounterSale
import com.vsjbakery.app.domain.model.CounterTotals
import com.vsjbakery.app.domain.model.Money
import com.vsjbakery.app.domain.model.OrderStatus
import com.vsjbakery.app.domain.model.ProductDraft
import com.vsjbakery.app.domain.model.StockDifference
import com.vsjbakery.app.domain.model.StockMovement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A short message for the admin: success (green) or problem (red). */
data class AdminMessage(val text: String, val isError: Boolean = false)

private const val NOT_SET_UP = "Admin tools are not set up in this version of the app."

/** Runs a server call, turning failures into a readable message. */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

// ======================================================================= products & stock

data class StockHistoryState(
    val product: AdminProduct,
    val isLoading: Boolean = true,
    val movements: List<StockMovement> = emptyList(),
    val errorMessage: String? = null,
)

data class AdminCatalogState(
    val isLoading: Boolean = true,
    val products: List<AdminProduct> = emptyList(),
    val categories: List<AdminCategory> = emptyList(),
    val loadError: String? = null,
    val isSaving: Boolean = false,
    val message: AdminMessage? = null,
    val history: StockHistoryState? = null,
    /** Set after an end-of-day count; empty list means everything matched. */
    val countResult: List<StockDifference>? = null,
    /** Set after a product was saved, so the edit screen can close. */
    val savedProductId: String? = null,
) {
    fun categoryName(id: String) = categories.firstOrNull { it.id == id }?.name ?: "—"
}

class AdminCatalogViewModel(private val repository: AdminRepository?) : ViewModel() {

    private val _state = MutableStateFlow(AdminCatalogState())
    val state: StateFlow<AdminCatalogState> = _state.asStateFlow()

    fun load() {
        val repo = repository ?: run {
            _state.update { it.copy(isLoading = false, loadError = NOT_SET_UP) }
            return
        }
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            attempt { repo.products() to repo.categories() }
                .onSuccess { (products, categories) ->
                    _state.update { it.copy(isLoading = false, products = products, categories = categories) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, loadError = RemoteErrors.message(e)) } }
        }
    }

    /** Runs an admin action, shows a message, and reloads products and stock. */
    private fun act(successText: String, onSuccess: (String?) -> Unit = {}, block: suspend AdminRepository.() -> String?) {
        val repo = repository ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            attempt { repo.block() }
                .onSuccess { result ->
                    _state.update { it.copy(isSaving = false, message = AdminMessage(successText)) }
                    onSuccess(result)
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, message = AdminMessage(RemoteErrors.message(e), isError = true)) }
                }
        }
    }

    fun saveProduct(draft: ProductDraft, photoJpeg: ByteArray?) = act(
        successText = "Saved ${draft.name.trim()}",
        onSuccess = { id -> _state.update { it.copy(savedProductId = id) } },
    ) {
        val id = saveProduct(draft)
        if (photoJpeg != null) uploadProductPhoto(id, photoJpeg)
        id
    }

    fun saveCategory(draft: CategoryDraft) = act("Saved category ${draft.name.trim()}") {
        saveCategory(draft)
        null
    }

    fun recordProduction(product: AdminProduct, quantity: Int, note: String) =
        act("Added $quantity × ${product.name} to stock") {
            recordProduction(product.id, quantity, note)
            null
        }

    fun adjustStock(product: AdminProduct, delta: Int, reason: String) =
        act("Adjusted ${product.name} by ${if (delta > 0) "+" else ""}$delta") {
            adjustStock(product.id, delta, reason)
            null
        }

    fun recordWastage(product: AdminProduct, quantity: Int, reason: String) =
        act("Recorded $quantity × ${product.name} as wastage") {
            recordWastage(product.id, quantity, reason)
            null
        }

    fun recordStockCount(counts: Map<String, Int>, note: String) {
        val repo = repository ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            attempt { repo.recordStockCount(counts, note) }
                .onSuccess { differences ->
                    _state.update { it.copy(isSaving = false, countResult = differences) }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, message = AdminMessage(RemoteErrors.message(e), isError = true)) }
                }
        }
    }

    fun openHistory(product: AdminProduct) {
        val repo = repository ?: return
        _state.update { it.copy(history = StockHistoryState(product)) }
        viewModelScope.launch {
            attempt { repo.stockHistory(product.id) }
                .onSuccess { movements ->
                    _state.update { s -> s.copy(history = s.history?.copy(isLoading = false, movements = movements)) }
                }
                .onFailure { e ->
                    _state.update { s -> s.copy(history = s.history?.copy(isLoading = false, errorMessage = RemoteErrors.message(e))) }
                }
        }
    }

    fun closeHistory() = _state.update { it.copy(history = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearCountResult() = _state.update { it.copy(countResult = null) }
    fun clearSavedProduct() = _state.update { it.copy(savedProductId = null) }
}

// ======================================================================= app orders

enum class OrderFilter(val label: String, val statuses: Set<OrderStatus>?) {
    TO_PREPARE("To prepare", setOf(OrderStatus.PAID)),
    READY("Ready", setOf(OrderStatus.READY_FOR_PICKUP)),
    COLLECTED("Collected", setOf(OrderStatus.COLLECTED)),
    UNPAID("Unpaid", setOf(OrderStatus.PENDING_PAYMENT)),
    CANCELLED("Cancelled", setOf(OrderStatus.CANCELLED)),
    ALL("All", null),
}

data class AdminOrdersState(
    val isLoading: Boolean = true,
    val orders: List<AdminOrder> = emptyList(),
    val filter: OrderFilter = OrderFilter.TO_PREPARE,
    val updatingId: String? = null,
    val message: AdminMessage? = null,
    val loadError: String? = null,
) {
    val visible: List<AdminOrder>
        get() = filter.statuses?.let { wanted -> orders.filter { it.order.status in wanted } } ?: orders

    fun count(filter: OrderFilter): Int =
        filter.statuses?.let { wanted -> orders.count { it.order.status in wanted } } ?: orders.size
}

class AdminOrdersViewModel(private val repository: AdminRepository?) : ViewModel() {

    private val _state = MutableStateFlow(AdminOrdersState())
    val state: StateFlow<AdminOrdersState> = _state.asStateFlow()

    fun load() {
        val repo = repository ?: run {
            _state.update { it.copy(isLoading = false, loadError = NOT_SET_UP) }
            return
        }
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            attempt { repo.appOrders() }
                .onSuccess { orders -> _state.update { it.copy(isLoading = false, orders = orders) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, loadError = RemoteErrors.message(e)) } }
        }
    }

    fun setFilter(filter: OrderFilter) = _state.update { it.copy(filter = filter) }

    fun markReady(order: AdminOrder) = update(order, OrderStatus.READY_FOR_PICKUP, "Order #${order.order.number} is ready for pickup")

    fun markCollected(order: AdminOrder) = update(order, OrderStatus.COLLECTED, "Order #${order.order.number} collected")

    private fun update(order: AdminOrder, status: OrderStatus, successText: String) {
        val repo = repository ?: return
        if (_state.value.updatingId != null) return
        _state.update { it.copy(updatingId = order.order.id, message = null) }
        viewModelScope.launch {
            attempt { repo.setOrderStatus(order.order.id, status) }
                .onSuccess {
                    _state.update { it.copy(updatingId = null, message = AdminMessage(successText)) }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(updatingId = null, message = AdminMessage(RemoteErrors.message(e), isError = true)) }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

// ======================================================================= counter sales

data class CounterState(
    val isLoading: Boolean = true,
    val products: List<AdminProduct> = emptyList(),
    val quantities: Map<String, Int> = emptyMap(),
    val method: CounterPaymentMethod? = null,
    val note: String = "",
    val isRecording: Boolean = false,
    val sales: List<CounterSale> = emptyList(),
    val message: AdminMessage? = null,
    val loadError: String? = null,
) {
    val totalPaise: Long
        get() = quantities.entries.sumOf { (id, qty) -> (products.firstOrNull { it.id == id }?.pricePaise ?: 0L) * qty }

    val itemCount: Int get() = quantities.values.sum()
    val totals: CounterTotals get() = CounterTotals.of(sales)
    val canRecord: Boolean get() = quantities.isNotEmpty() && method != null && !isRecording
}

class CounterViewModel(private val repository: AdminRepository?) : ViewModel() {

    private val _state = MutableStateFlow(CounterState())
    val state: StateFlow<CounterState> = _state.asStateFlow()

    fun load() {
        val repo = repository ?: run {
            _state.update { it.copy(isLoading = false, loadError = NOT_SET_UP) }
            return
        }
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            attempt { repo.products() to repo.counterSalesToday() }
                .onSuccess { (products, sales) ->
                    _state.update { it.copy(isLoading = false, products = products, sales = sales) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, loadError = RemoteErrors.message(e)) } }
        }
    }

    fun add(product: AdminProduct) = _state.update { s ->
        val current = s.quantities[product.id] ?: 0
        if (current >= product.stockQuantity) s else s.copy(quantities = s.quantities + (product.id to current + 1))
    }

    fun decrease(product: AdminProduct) = _state.update { s ->
        val current = s.quantities[product.id] ?: return@update s
        s.copy(quantities = if (current <= 1) s.quantities - product.id else s.quantities + (product.id to current - 1))
    }

    fun setMethod(method: CounterPaymentMethod) = _state.update { it.copy(method = method) }
    fun setNote(note: String) = _state.update { it.copy(note = note.take(300)) }
    fun clearSale() = _state.update { it.copy(quantities = emptyMap(), method = null, note = "") }

    fun record() {
        val repo = repository ?: return
        val s = _state.value
        val method = s.method ?: return
        if (!s.canRecord) return
        val total = s.totalPaise
        _state.update { it.copy(isRecording = true, message = null) }
        viewModelScope.launch {
            attempt { repo.recordCounterSale(s.quantities, method, s.note) }
                .onSuccess { number ->
                    _state.update {
                        it.copy(
                            isRecording = false,
                            quantities = emptyMap(),
                            method = null,
                            note = "",
                            message = AdminMessage("Sale #$number recorded: ${Money.formatRupees(total)} by ${method.label}"),
                        )
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isRecording = false, message = AdminMessage(RemoteErrors.message(e), isError = true)) }
                }
        }
    }

    fun undo(sale: CounterSale, reason: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            attempt { repo.undoCounterSale(sale.id, reason) }
                .onSuccess {
                    _state.update { it.copy(message = AdminMessage("Sale #${sale.number} undone; stock put back")) }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(message = AdminMessage(RemoteErrors.message(e), isError = true)) } }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
