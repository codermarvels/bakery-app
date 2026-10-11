package com.vsjbakery.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Where the app finds the bakery's Supabase project. Both values are public by design
 * (the publishable key only allows what Row Level Security permits), but they are still
 * kept out of Git and supplied at build time from local.properties or CI variables.
 */
data class SupabaseConfig(val url: String, val publishableKey: String) {

    val isConfigured: Boolean
        get() = url.startsWith("https://") && publishableKey.isNotBlank()

    /** Public web address of a photo stored in the product-images bucket. */
    fun productImageUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("https://") -> path
        else -> "${url.trimEnd('/')}/storage/v1/object/public/$PRODUCT_IMAGES_BUCKET/${path.trimStart('/')}"
    }

    fun createClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = url,
        supabaseKey = publishableKey,
    ) {
        // Auth keeps the login session on the device and refreshes it automatically.
        install(Auth)
        install(Postgrest)
    }

    companion object {
        const val PRODUCT_IMAGES_BUCKET = "product-images"
    }
}
