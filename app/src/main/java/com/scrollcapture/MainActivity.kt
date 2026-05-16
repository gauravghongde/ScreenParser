package com.scrollcapture

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.scrollcapture.ui.navigation.AppNavGraph
import com.scrollcapture.ui.navigation.Routes
import com.scrollcapture.ui.theme.ScrollCaptureTheme
import com.scrollcapture.ui.theme.SurfaceDark

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("scrollcapture_prefs", Context.MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean("onboarding_done", false)

        setContent {
            ScrollCaptureTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceDark
                ) {
                    val navController = rememberNavController()
                    val startDest = if (onboardingDone) Routes.HOME else Routes.ONBOARDING

                    AppNavGraph(
                        navController = navController,
                        startDestination = startDest
                    )
                }
            }
        }
    }

    // Save onboarding completion when navigating away from it
    fun markOnboardingDone() {
        getSharedPreferences("scrollcapture_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
    }
}
