package com.vsjbakery.app

import com.vsjbakery.app.data.SupabaseConfig
import com.vsjbakery.app.data.auth.AuthRepository
import com.vsjbakery.app.data.auth.SupabaseAuthRepository
import com.vsjbakery.app.data.auth.UnconfiguredAuthRepository
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
}
