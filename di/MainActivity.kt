package com.hemax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.hemax.navigation.HEmaxNavHost
import com.hemax.theme.HEmaxTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HEmaxTheme {
                val windowSizeClass = calculateWindowSizeClass(activity = this)
                HEmaxNavHost(windowSizeClass = windowSizeClass)
            }
        }
    }
}
