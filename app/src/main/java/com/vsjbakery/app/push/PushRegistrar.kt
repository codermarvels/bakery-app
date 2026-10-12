package com.vsjbakery.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * Tells our server which phone should receive new-order alerts. Only administrator accounts
 * can register (the database refuses everyone else).
 */
class PushRegistrar(private val client: SupabaseClient, private val context: Context) {

    private suspend fun currentToken(): String? {
        if (!OrderAlerts.initFirebase(context)) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    /** After an administrator signs in (and at every app start while signed in). */
    suspend fun registerThisPhone() {
        currentToken()?.let { register(it) }
    }

    /** Also called when Firebase gives the phone a new token. */
    suspend fun register(token: String) {
        try {
            client.postgrest.rpc("register_push_device", buildJsonObject { put("p_token", token) })
            OrderAlerts.setEnabled(context, true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not register for new-order alerts", e)
        }
    }

    /** Before signing out, so a signed-out phone stops receiving alerts. */
    suspend fun unregisterThisPhone() {
        OrderAlerts.setEnabled(context, false)
        try {
            val token = currentToken() ?: return
            client.postgrest.rpc("unregister_push_device", buildJsonObject { put("p_token", token) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister from new-order alerts", e)
        }
    }

    private companion object {
        const val TAG = "PushRegistrar"
    }
}
