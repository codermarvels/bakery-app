package com.vsjbakery.app.data

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException

/** Turns server errors into short messages a customer can act on. */
object RemoteErrors {
    /** SQLSTATE our database functions use for messages written for customers. */
    private const val CUSTOMER_MESSAGE_CODE = "P0001"
    private const val PERMISSION_DENIED_CODE = "42501"

    fun message(e: Throwable): String = when {
        e is PostgrestRestException && e.code == CUSTOMER_MESSAGE_CODE -> e.error
        e is PostgrestRestException && e.code == PERMISSION_DENIED_CODE ->
            e.error.ifBlank { "You are not allowed to do that." }
        e is HttpRequestException -> "Could not reach the bakery. Check your internet connection."
        else -> "Something went wrong. Please try again."
    }
}
