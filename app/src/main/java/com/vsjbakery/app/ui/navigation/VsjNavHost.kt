package com.vsjbakery.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.BuildConfig
import com.vsjbakery.app.ui.account.AccountScreen
import com.vsjbakery.app.ui.account.ResetPasswordScreen
import com.vsjbakery.app.ui.account.SignInScreen
import com.vsjbakery.app.ui.account.SignUpScreen
import com.vsjbakery.app.ui.admin.AdminCatalogViewModel
import com.vsjbakery.app.ui.admin.AdminOrdersViewModel
import com.vsjbakery.app.ui.admin.AdminScreen
import com.vsjbakery.app.ui.admin.CategoriesScreen
import com.vsjbakery.app.ui.admin.CounterViewModel
import com.vsjbakery.app.ui.admin.ProductEditScreen
import com.vsjbakery.app.ui.admin.StockCountScreen
import com.vsjbakery.app.ui.auth.AuthViewModel
import com.vsjbakery.app.ui.cart.CartScreen
import com.vsjbakery.app.ui.cart.OrderPlacedScreen
import com.vsjbakery.app.ui.category.CategoryScreen
import com.vsjbakery.app.ui.home.HomeScreen
import com.vsjbakery.app.ui.orders.MyOrdersScreen
import com.vsjbakery.app.ui.orders.OrdersViewModel
import com.vsjbakery.app.ui.payments.PaymentState
import com.vsjbakery.app.ui.payments.PaymentViewModel
import com.vsjbakery.app.ui.shop.ShopViewModel
import com.vsjbakery.app.ui.reports.ReportsScreen
import com.vsjbakery.app.ui.reports.ReportsViewModel

@Composable
fun VsjNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    shopViewModel: ShopViewModel,
    ordersViewModel: OrdersViewModel,
    paymentViewModel: PaymentViewModel,
    adminCatalogViewModel: AdminCatalogViewModel,
    adminOrdersViewModel: AdminOrdersViewModel,
    counterViewModel: CounterViewModel,
    reportsViewModel: ReportsViewModel,
    modifier: Modifier = Modifier,
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val form by authViewModel.form.collectAsStateWithLifecycle()
    val catalog by shopViewModel.catalog.collectAsStateWithLifecycle()
    val isRefreshing by shopViewModel.isRefreshing.collectAsStateWithLifecycle()
    val cartQuantities by shopViewModel.cartQuantities.collectAsStateWithLifecycle()
    val cartState by shopViewModel.cartState.collectAsStateWithLifecycle()
    val checkout by shopViewModel.checkout.collectAsStateWithLifecycle()
    val ordersState by ordersViewModel.state.collectAsStateWithLifecycle()
    val paymentState by paymentViewModel.state.collectAsStateWithLifecycle()
    val resetState by authViewModel.reset.collectAsStateWithLifecycle()
    val deleteState by authViewModel.delete.collectAsStateWithLifecycle()

    // After signing in, staff go straight to their dashboard. Customers go back to where
    // they came from (for example the cart, to finish ordering).
    val onSignedIn: (AppRole) -> Unit = { role ->
        authViewModel.onCompletionHandled()
        when (role) {
            AppRole.ADMIN -> navController.navigate(AdminHomeRoute) {
                popUpTo<AccountRoute>()
                launchSingleTop = true
            }
            AppRole.REPORTS -> navController.navigate(ReportsHomeRoute) {
                popUpTo<AccountRoute>()
                launchSingleTop = true
            }
            AppRole.CUSTOMER -> {
                navController.popBackStack<SignInRoute>(inclusive = true)
                navController.popBackStack<SignUpRoute>(inclusive = true)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                catalogState = catalog,
                isRefreshing = isRefreshing,
                cartQuantities = cartQuantities,
                onRefresh = shopViewModel::refresh,
                onCategoryClick = { category ->
                    navController.navigate(CategoryRoute(id = category.id, slug = category.slug, name = category.name))
                },
                onAdd = shopViewModel::addToCart,
                onDecrement = { shopViewModel.decrease(it.id) },
            )
        }
        composable<CategoryRoute> { entry ->
            val route = entry.toRoute<CategoryRoute>()
            CategoryScreen(
                categoryId = route.id,
                slug = route.slug,
                name = route.name,
                catalogState = catalog,
                isRefreshing = isRefreshing,
                cartQuantities = cartQuantities,
                cartState = cartState,
                onRefresh = shopViewModel::refresh,
                onAdd = shopViewModel::addToCart,
                onDecrement = { shopViewModel.decrease(it.id) },
                onOpenCart = { navController.navigateToTopLevel(CartRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<CartRoute> {
            CartScreen(
                cart = cartState,
                checkout = checkout,
                authState = authState,
                onIncrement = { line -> line.product?.let(shopViewModel::addToCart) },
                onDecrement = { line -> shopViewModel.decrease(line.productId) },
                onRemove = { line -> shopViewModel.remove(line.productId) },
                onFixLine = shopViewModel::fixLine,
                onPlaceOrder = shopViewModel::placeOrder,
                onOrderPlaced = { placed ->
                    shopViewModel.onOrderPlacedHandled()
                    navController.navigate(
                        OrderPlacedRoute(placed.orderId, placed.orderNumber, placed.totalPaise, placed.addedToExisting),
                    )
                },
                onDismissError = shopViewModel::dismissCheckoutError,
                onSignIn = { navController.navigate(SignInRoute(AppRole.CUSTOMER)) },
                onBrowseMenu = { navController.navigateToTopLevel(HomeRoute) },
            )
        }
        composable<OrderPlacedRoute> { entry ->
            val route = entry.toRoute<OrderPlacedRoute>()
            OrderPlacedScreen(
                orderNumber = route.orderNumber,
                totalPaise = route.totalPaise,
                addedToExisting = route.addedToExisting,
                onPayNow = {
                    paymentViewModel.pay(route.orderId)
                    // Show the order list underneath so the new status is visible afterwards.
                    navController.navigate(MyOrdersRoute) {
                        popUpTo<OrderPlacedRoute> { inclusive = true }
                    }
                },
                onViewOrders = {
                    navController.navigate(MyOrdersRoute) {
                        popUpTo<OrderPlacedRoute> { inclusive = true }
                    }
                },
                onContinueShopping = { navController.navigateToTopLevel(HomeRoute) },
            )
        }
        composable<MyOrdersRoute> {
            MyOrdersScreen(
                state = ordersState,
                onLoad = ordersViewModel::load,
                onCancel = ordersViewModel::cancel,
                onPay = paymentViewModel::pay,
                isPaymentInProgress = paymentState !is PaymentState.Idle,
                onBack = { navController.popBackStack() },
            )
        }
        composable<AccountRoute> {
            AccountScreen(
                authState = authState,
                onSignIn = { role -> navController.navigate(SignInRoute(role)) },
                onOpenAdmin = { navController.navigate(AdminHomeRoute) },
                onOpenReports = { navController.navigate(ReportsHomeRoute) },
                onOpenOrders = { navController.navigate(MyOrdersRoute) },
                onRetryProfile = authViewModel::retryProfile,
                onSignOut = authViewModel::signOut,
                deleteState = deleteState,
                onDeleteAccount = authViewModel::deleteAccount,
                onClearDeleteError = authViewModel::clearDeleteError,
                policiesUrl = BuildConfig.POLICIES_URL,
            )
        }
        composable<SignInRoute> { entry ->
            val role = entry.toRoute<SignInRoute>().role
            SignInScreen(
                role = role,
                authState = authState,
                form = form,
                onSignIn = { email, password -> authViewModel.signIn(email, password, role) },
                onCreateAccount = {
                    navController.navigate(SignUpRoute) {
                        popUpTo<SignInRoute> { inclusive = true }
                    }
                },
                onForgotPassword = { email -> navController.navigate(ResetPasswordRoute(role, email)) },
                onSignedIn = onSignedIn,
                onResetForm = authViewModel::resetForm,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ResetPasswordRoute> { entry ->
            val route = entry.toRoute<ResetPasswordRoute>()
            ResetPasswordScreen(
                role = route.role,
                initialEmail = route.email,
                state = resetState,
                form = form,
                onStart = authViewModel::startPasswordReset,
                onSendCode = authViewModel::sendResetCode,
                onReset = { code, password, confirm -> authViewModel.resetPassword(code, password, confirm, route.role) },
                onSignedIn = onSignedIn,
                onBack = { navController.popBackStack() },
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(
                authState = authState,
                form = form,
                onSignUp = authViewModel::signUp,
                onSignedIn = onSignedIn,
                onGoToSignIn = {
                    navController.navigate(SignInRoute(AppRole.CUSTOMER)) {
                        popUpTo<SignUpRoute> { inclusive = true }
                    }
                },
                onResetForm = authViewModel::resetForm,
                onBack = { navController.popBackStack() },
            )
        }
        composable<AdminHomeRoute> {
            AdminScreen(
                authState = authState,
                catalogViewModel = adminCatalogViewModel,
                ordersViewModel = adminOrdersViewModel,
                counterViewModel = counterViewModel,
                onEditProduct = { id -> navController.navigate(ProductEditRoute(id.orEmpty())) },
                onManageCategories = { navController.navigate(CategoriesRoute) },
                onStockCount = { navController.navigate(StockCountRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ProductEditRoute> { entry ->
            val id = entry.toRoute<ProductEditRoute>().productId
            ProductEditScreen(
                productId = id.ifBlank { null },
                viewModel = adminCatalogViewModel,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<CategoriesRoute> {
            CategoriesScreen(viewModel = adminCatalogViewModel, onBack = { navController.popBackStack() })
        }
        composable<StockCountRoute> {
            StockCountScreen(viewModel = adminCatalogViewModel, onBack = { navController.popBackStack() })
        }
        composable<ReportsHomeRoute> {
            ReportsScreen(
                authState = authState,
                viewModel = reportsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Switches between bottom-bar tabs the standard way: one copy of each tab on the back stack,
 * and each tab remembers its scroll position when you come back to it.
 */
fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
