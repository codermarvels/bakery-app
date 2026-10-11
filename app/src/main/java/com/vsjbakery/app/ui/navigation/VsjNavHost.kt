package com.vsjbakery.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vsjbakery.app.ui.account.AccountScreen
import com.vsjbakery.app.ui.account.SignInScreen
import com.vsjbakery.app.ui.cart.CartScreen
import com.vsjbakery.app.ui.category.CategoryScreen
import com.vsjbakery.app.ui.home.HomeScreen

@Composable
fun VsjNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
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
            AccountScreen(onSignIn = { role -> navController.navigate(SignInRoute(role)) })
        }
        composable<SignInRoute> { entry ->
            SignInScreen(
                role = entry.toRoute<SignInRoute>().role,
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
