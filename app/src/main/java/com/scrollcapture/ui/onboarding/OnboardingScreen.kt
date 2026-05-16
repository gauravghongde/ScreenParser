package com.scrollcapture.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrollcapture.ui.theme.*
import com.scrollcapture.util.PermissionHelper
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val buttonText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val finishOnboarding = {
        context.getSharedPreferences("scrollcapture_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
        onFinished()
    }

    val pages = listOf(
        OnboardingPage(
            Icons.Outlined.Videocam,
            "Screen Recording",
            "ScrollCapture needs to record your screen to capture text from any app. Your screen content is processed entirely on-device and never leaves your phone.",
            "Continue"
        ),
        OnboardingPage(
            Icons.Outlined.Accessibility,
            "Auto-Scroll (Optional)",
            "Enable the accessibility service to let ScrollCapture automatically scroll through content for hands-free capture. You can always scroll manually instead.",
            "Open Settings"
        ),
        OnboardingPage(
            Icons.Outlined.Layers,
            "Display Over Apps",
            "ScrollCapture needs to show a floating bubble over other apps so you can control capture sessions while using any app.",
            "Grant Permission"
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* don't block flow on this */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SurfaceDark, SurfaceContainerDark)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = finishOnboarding) {
                    Text("Skip", color = OnSurfaceVariantDark)
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            // Page indicators
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Purple60
                                else OutlineDark
                            )
                    )
                }
            }

            // Action button
            Button(
                onClick = {
                    when (pagerState.currentPage) {
                        0 -> {
                            // Notification permission for Android 13+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch { pagerState.animateScrollToPage(1) }
                        }
                        1 -> {
                            PermissionHelper.openAccessibilitySettings(context)
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                        2 -> {
                            if (!PermissionHelper.canDrawOverlays(context)) {
                                PermissionHelper.requestOverlayPermission(context)
                            }
                            finishOnboarding()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 32.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Purple40
                )
            ) {
                Text(
                    text = if (pagerState.currentPage == pages.lastIndex) "Get Started"
                    else pages[pagerState.currentPage].buttonText,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with glow effect
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Purple40.copy(alpha = 0.3f),
                            SurfaceDark.copy(alpha = 0f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = page.title,
                modifier = Modifier.size(56.dp),
                tint = Purple60
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurfaceDark,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariantDark,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    }
}
