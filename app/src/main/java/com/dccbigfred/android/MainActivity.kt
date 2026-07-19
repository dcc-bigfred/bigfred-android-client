package com.dccbigfred.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.dccbigfred.android.ui.navigation.BigFredApp
import com.dccbigfred.android.ui.theme.BigFredTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BigFredTheme {
                BigFredApp()
            }
        }
    }
}
