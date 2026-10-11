package com.vsjbakery.app

import android.app.Application

class VsjBakeryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
