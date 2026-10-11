package com.vsjbakery.app.ui.navigation

import com.vsjbakery.app.domain.model.AppRole
import kotlinx.serialization.Serializable

// Each screen is a typed route. Navigation Compose turns these into URLs behind the scenes,
// so screen arguments are checked by the compiler instead of being hand-written strings.

@Serializable
data object HomeRoute

@Serializable
data object CartRoute

@Serializable
data object AccountRoute

@Serializable
data class CategoryRoute(val slug: String, val name: String)

@Serializable
data class SignInRoute(val role: AppRole)

@Serializable
data object SignUpRoute

@Serializable
data object AdminHomeRoute

@Serializable
data object ReportsHomeRoute
