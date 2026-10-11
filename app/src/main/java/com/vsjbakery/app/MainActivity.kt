package com.vsjbakery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vsjbakery.app.ui.VsjBakeryApp
import com.vsjbakery.app.ui.theme.VsjBakeryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VsjBakeryTheme {
                VsjBakeryApp()
            }
        }
    }
}
