package com.vsjbakery.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vsjbakery.app.VsjBakeryApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Receives alerts from our server (via Firebase) even when the app is closed. */
class VsjMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container get() = (application as VsjBakeryApplication).container

    override fun onNewToken(token: String) {
        if (!OrderAlerts.isEnabled(this)) return
        val registrar = container.pushRegistrar ?: return
        scope.launch { registrar.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "new_order") return
        OrderAlerts.show(
            context = this,
            orderId = data["order_id"].orEmpty(),
            title = data["title"] ?: "New paid order",
            body = data["body"].orEmpty(),
        )
        // If the admin screen is open, refresh it straight away.
        container.newOrderSignals.tryEmit(Unit)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
