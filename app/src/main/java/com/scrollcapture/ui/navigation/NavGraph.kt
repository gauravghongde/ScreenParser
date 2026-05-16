package com.scrollcapture.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.scrollcapture.ui.home.HomeScreen
import com.scrollcapture.ui.onboarding.OnboardingScreen
import com.scrollcapture.ui.review.ReviewScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val REVIEW = "review/{sessionId}"
    fun review(sessionId: Long) = "review/$sessionId"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(Routes.review(sessionId))
                }
            )
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            ReviewScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
