package com.vsjbakery.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vsjbakery.app.AppContainer
import com.vsjbakery.app.VsjBakeryApplication
import com.vsjbakery.app.ui.admin.AdminCatalogViewModel
import com.vsjbakery.app.ui.admin.AdminOrdersViewModel
import com.vsjbakery.app.ui.admin.CounterViewModel
import com.vsjbakery.app.ui.auth.AuthViewModel
import com.vsjbakery.app.ui.orders.OrdersViewModel
import com.vsjbakery.app.ui.payments.PaymentViewModel
import com.vsjbakery.app.ui.shop.ShopViewModel

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as VsjBakeryApplication).container

/** Builds every ViewModel in the app from the shared [AppContainer]. */
val AppViewModelFactory = viewModelFactory {
    initializer { AuthViewModel(container().authRepository) }
    initializer {
        val c = container()
        ShopViewModel(c.catalogRepository, c.cartRepository, c.orderRepository)
    }
    initializer { OrdersViewModel(container().orderRepository) }
    initializer {
        val c = container()
        PaymentViewModel(c.paymentRepository, c.checkoutResults)
    }
    initializer { AdminCatalogViewModel(container().adminRepository) }
    initializer { AdminOrdersViewModel(container().adminRepository) }
    initializer { CounterViewModel(container().adminRepository) }
}
