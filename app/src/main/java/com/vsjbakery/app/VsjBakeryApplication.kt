package com.vsjbakery.app

import android.app.Application
import com.razorpay.Checkout

class VsjBakeryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
        // Loads Razorpay Checkout in the background so the payment screen opens faster.
        Checkout.preload(applicationContext)
    }
}
