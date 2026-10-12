package com.vsjbakery.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.vsjbakery.app.VsjBakeryApplication
import com.vsjbakery.app.ui.navigation.AdminHomeRoute
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vsjbakery.app.ui.admin.AdminCatalogViewModel
import com.vsjbakery.app.ui.admin.AdminOrdersViewModel
import com.vsjbakery.app.ui.admin.CounterViewModel
import com.vsjbakery.app.ui.auth.AuthViewModel
import com.vsjbakery.app.ui.orders.OrdersViewModel
import com.vsjbakery.app.ui.payments.PaymentHost
import com.vsjbakery.app.ui.payments.PaymentViewModel
import com.vsjbakery.app.ui.reports.ReportsViewModel
import com.vsjbakery.app.ui.shop.ShopViewModel
import com.vsjbakery.app.ui.navigation.AccountRoute
import com.vsjbakery.app.ui.navigation.CartRoute
import com.vsjbakery.app.ui.navigation.HomeRoute
import com.vsjbakery.app.ui.navigation.VsjNavHost
import com.vsjbakery.app.ui.navigation.navigateToTopLevel
import kotlin.reflect.KClass

private data class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    TopLevelTab(HomeRoute, HomeRoute::class, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelTab(CartRoute, CartRoute::class, "Cart", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag),
    TopLevelTab(AccountRoute, AccountRoute::class, "Account", Icons.Filled.Person, Icons.Outlined.Person),
)

private fun NavDestination?.isOn(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true

/** Root of the UI: the bottom navigation bar plus whichever screen is current. */
@Composable
fun VsjBakeryApp() {
    val navController = rememberNavController()
    // One AuthViewModel for the whole app, so every screen sees the same sign-in state.
    val authViewModel: AuthViewModel = viewModel(factory = AppViewModelFactory)
    val shopViewModel: ShopViewModel = viewModel(factory = AppViewModelFactory)
    val ordersViewModel: OrdersViewModel = viewModel(factory = AppViewModelFactory)
    val paymentViewModel: PaymentViewModel = viewModel(factory = AppViewModelFactory)
    val paymentState by paymentViewModel.state.collectAsStateWithLifecycle()
    // Admin screens share these, so a product saved on the edit screen shows in the lists.
    val adminCatalogViewModel: AdminCatalogViewModel = viewModel(factory = AppViewModelFactory)
    val adminOrdersViewModel: AdminOrdersViewModel = viewModel(factory = AppViewModelFactory)
    val counterViewModel: CounterViewModel = viewModel(factory = AppViewModelFactory)
    val reportsViewModel: ReportsViewModel = viewModel(factory = AppViewModelFactory)

    // New-order alerts: refresh the admin lists when one arrives, and open Admin → Orders
    // when the administrator taps the notification.
    val container = (LocalContext.current.applicationContext as VsjBakeryApplication).container
    val openOrdersRequested by container.openOrdersRequest.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        container.newOrderSignals.collect { adminOrdersViewModel.load() }
    }
    LaunchedEffect(openOrdersRequested) {
        if (openOrdersRequested) navController.navigate(AdminHomeRoute) { launchSingleTop = true }
    }
    val cartState by shopViewModel.cartState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // The bottom bar only shows on the three main tabs, not on detail screens.
    val showBottomBar = destination == null || tabs.any { destination.isOn(it.routeClass) }

    Scaffold(
        // Each screen draws its own top bar, which already handles the status-bar area.
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        val selected = destination.isOn(tab.routeClass)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(tab.route) },
                            icon = {
                                val count = if (tab.route == CartRoute) cartState.itemCount else 0
                                BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        VsjNavHost(
            navController = navController,
            authViewModel = authViewModel,
            shopViewModel = shopViewModel,
            ordersViewModel = ordersViewModel,
            paymentViewModel = paymentViewModel,
            adminCatalogViewModel = adminCatalogViewModel,
            adminOrdersViewModel = adminOrdersViewModel,
            counterViewModel = counterViewModel,
            reportsViewModel = reportsViewModel,
            openOrdersRequested = openOrdersRequested,
            onOpenOrdersHandled = { container.openOrdersRequest.value = false },
            modifier = Modifier.padding(innerPadding),
        )
    }

    // Payment progress and results are shown on top of whatever screen is open.
    PaymentHost(
        state = paymentState,
        onCheckoutOpened = paymentViewModel::onCheckoutOpened,
        onCheckoutCouldNotOpen = paymentViewModel::onCheckoutCouldNotOpen,
        onDismiss = paymentViewModel::dismiss,
        onFinished = {
            // Show the server's latest order status and stock.
            ordersViewModel.load()
            shopViewModel.refresh()
        },
    )
}
