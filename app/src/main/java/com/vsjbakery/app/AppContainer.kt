package com.vsjbakery.app

import com.vsjbakery.app.data.SupabaseConfig
import com.vsjbakery.app.data.admin.AdminRepository
import com.vsjbakery.app.data.admin.SupabaseAdminRepository
import com.vsjbakery.app.data.auth.AuthRepository
import com.vsjbakery.app.data.auth.SupabaseAuthRepository
import com.vsjbakery.app.data.auth.UnconfiguredAuthRepository
import com.vsjbakery.app.data.cart.CartRepository
import com.vsjbakery.app.data.catalog.CatalogRepository
import com.vsjbakery.app.data.catalog.SupabaseCatalogRepository
import com.vsjbakery.app.data.orders.OrderRepository
import com.vsjbakery.app.data.orders.SupabaseOrderRepository
import com.vsjbakery.app.data.payments.CheckoutResults
import com.vsjbakery.app.data.payments.PaymentRepository
import com.vsjbakery.app.data.payments.SupabasePaymentRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates the app's long-lived objects once and shares them (a simple form of
 * "dependency injection"). Screens get what they need from here via their ViewModels.
 */
class AppContainer {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val supabaseConfig = SupabaseConfig(
        url = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    )

    val supabase: SupabaseClient? =
        if (supabaseConfig.isConfigured) supabaseConfig.createClient() else null

    val authRepository: AuthRepository =
        supabase?.let { SupabaseAuthRepository(it, appScope) } ?: UnconfiguredAuthRepository()

    /** Null when the build has no Supabase settings; screens then show a message instead. */
    val catalogRepository: CatalogRepository? =
        supabase?.let { SupabaseCatalogRepository(it, supabaseConfig) }

    val orderRepository: OrderRepository? = supabase?.let { SupabaseOrderRepository(it) }

    val cartRepository = CartRepository()

    val adminRepository: AdminRepository? = supabase?.let { SupabaseAdminRepository(it, supabaseConfig) }

    val paymentRepository: PaymentRepository? = supabase?.let { SupabasePaymentRepository(it) }

    /** Razorpay Checkout's on-phone result, passed from MainActivity to the payment screen. */
    val checkoutResults = CheckoutResults()
}
