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
import com.vsjbakery.app.data.reports.ReportsRepository
import com.vsjbakery.app.data.reports.SupabaseReportsRepository
import android.content.Context
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.push.OrderAlerts
import com.vsjbakery.app.push.PushRegistrar
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates the app's long-lived objects once and shares them (a simple form of
 * "dependency injection"). Screens get what they need from here via their ViewModels.
 */
class AppContainer(private val context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val supabaseConfig = SupabaseConfig(
        url = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    )

    val supabase: SupabaseClient? =
        if (supabaseConfig.isConfigured) supabaseConfig.createClient() else null

    /** New-order alerts on the administrator's phone (null when this build has no Supabase). */
    val pushRegistrar: PushRegistrar? = supabase?.let { PushRegistrar(it, context) }

    val authRepository: AuthRepository =
        supabase?.let {
            SupabaseAuthRepository(it, appScope, beforeSignOut = { pushRegistrar?.unregisterThisPhone() })
        } ?: UnconfiguredAuthRepository()

    /** A new-order alert arrived: the admin screens refresh. */
    val newOrderSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The administrator tapped a new-order alert: open Admin → Orders. */
    val openOrdersRequest = MutableStateFlow(false)

    /** Null when the build has no Supabase settings; screens then show a message instead. */
    val catalogRepository: CatalogRepository? =
        supabase?.let { SupabaseCatalogRepository(it, supabaseConfig) }

    val orderRepository: OrderRepository? = supabase?.let { SupabaseOrderRepository(it) }

    val cartRepository = CartRepository()

    val adminRepository: AdminRepository? = supabase?.let { SupabaseAdminRepository(it, supabaseConfig) }

    val paymentRepository: PaymentRepository? = supabase?.let { SupabasePaymentRepository(it) }

    val reportsRepository: ReportsRepository? = supabase?.let { SupabaseReportsRepository(it) }

    /** Razorpay Checkout's on-phone result, passed from MainActivity to the payment screen. */
    val checkoutResults = CheckoutResults()

    init {
        // An administrator's phone signs up for alerts each time they are signed in (this also
        // refreshes the token). Any other account on this phone switches alerts off.
        appScope.launch {
            authRepository.authState.collect { state ->
                if (state is AuthState.SignedIn) {
                    if (state.profile.role == AppRole.ADMIN && OrderAlerts.isConfigured) {
                        pushRegistrar?.registerThisPhone()
                    } else {
                        OrderAlerts.setEnabled(context, false)
                    }
                }
            }
        }
    }
}
