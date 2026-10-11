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
import com.vsjbakery.app.ui.account.AccountScreen
import com.vsjbakery.app.ui.account.SignInScreen
import com.vsjbakery.app.ui.account.SignUpScreen
import com.vsjbakery.app.ui.auth.AuthViewModel
import com.vsjbakery.app.ui.cart.CartScreen
import com.vsjbakery.app.ui.category.CategoryScreen
import com.vsjbakery.app.ui.home.HomeScreen
import com.vsjbakery.app.ui.staff.AdminHomeScreen
import com.vsjbakery.app.ui.staff.ReportsHomeScreen

@Composable
fun VsjNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val form by authViewModel.form.collectAsStateWithLifecycle()

    // After signing in, staff go straight to their dashboard; customers return to Account.
    val onSignedIn: (AppRole) -> Unit = { role ->
        authViewModel.onCompletionHandled()
        val destination: Any = when (role) {
            AppRole.ADMIN -> AdminHomeRoute
            AppRole.REPORTS -> ReportsHomeRoute
            AppRole.CUSTOMER -> AccountRoute
        }
        navController.navigate(destination) {
            popUpTo<AccountRoute> { inclusive = destination == AccountRoute }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onCategoryClick = { category ->
                    navController.navigate(CategoryRoute(slug = category.slug, name = category.name))
                },
            )
        }
        composable<CategoryRoute> { entry ->
            val route = entry.toRoute<CategoryRoute>()
            CategoryScreen(
                slug = route.slug,
                name = route.name,
                onBack = { navController.popBackStack() },
            )
        }
        composable<CartRoute> {
            CartScreen(onBrowseMenu = { navController.navigateToTopLevel(HomeRoute) })
        }
        composable<AccountRoute> {
            AccountScreen(
                authState = authState,
                onSignIn = { role -> navController.navigate(SignInRoute(role)) },
                onOpenAdmin = { navController.navigate(AdminHomeRoute) },
                onOpenReports = { navController.navigate(ReportsHomeRoute) },
                onRetryProfile = authViewModel::retryProfile,
                onSignOut = authViewModel::signOut,
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
                onSignedIn = onSignedIn,
                onResetForm = authViewModel::resetForm,
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
            AdminHomeScreen(authState = authState, onBack = { navController.popBackStack() })
        }
        composable<ReportsHomeRoute> {
            ReportsHomeScreen(authState = authState, onBack = { navController.popBackStack() })
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
