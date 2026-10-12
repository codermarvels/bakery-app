package com.vsjbakery.app

import android.app.Application
import com.razorpay.Checkout
import com.vsjbakery.app.push.OrderAlerts

class VsjBakeryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        OrderAlerts.createChannel(this)
        OrderAlerts.initFirebase(this)
        container = AppContainer(this)
        // Loads Razorpay Checkout in the background so the payment screen opens faster.
        Checkout.preload(applicationContext)
    }
}
