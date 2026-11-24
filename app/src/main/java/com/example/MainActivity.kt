package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.UserPreferences
import com.example.di.AppContainer
import com.example.ui.navigation.ExcevoNavHost
import com.example.ui.theme.ExcevoTheme

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val prefs by appContainer.userPreferences.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            ExcevoTheme(themeIntensity = prefs.themeIntensity) {
                ExcevoNavHost(appContainer = appContainer)
            }
        }
    }
}
