package com.scrollcapture.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrollcapture.data.CaptureSession
import com.scrollcapture.service.CaptureService
import com.scrollcapture.service.OverlayService
import com.scrollcapture.ui.theme.*
import com.scrollcapture.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSessionClick: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptureService.start(context, result.resultCode, result.data!!)
            // Start overlay
            context.startForegroundService(
                Intent(context, OverlayService::class.java)
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("ScrollCapture", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "${sessions.size} capture${if (sessions.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariantDark
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!PermissionHelper.canDrawOverlays(context)) {
                        PermissionHelper.requestOverlayPermission(context)
                        return@ExtendedFloatingActionButton
                    }
                    val pm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                            as MediaProjectionManager
                    projectionLauncher.launch(pm.createScreenCaptureIntent())
                },
                icon = { Icon(Icons.Filled.Add, "New Capture") },
                text = { Text("New Capture") },
                containerColor = Purple40,
                contentColor = OnSurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        },
        containerColor = SurfaceDark
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onSessionClick(session.id) },
                        onDelete = { viewModel.deleteSession(session) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Purple40.copy(alpha = 0.2f), SurfaceDark.copy(alpha = 0f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Purple60.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "No captures yet",
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurfaceVariantDark
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap \"New Capture\" to start capturing\nscrollable content from any app",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantDark.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SessionCard(
    session: CaptureSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()) }

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = SurfaceContainerDark
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Purple40.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.TextSnippet,
                    contentDescription = null,
                    tint = Purple60,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title.ifEmpty { "Untitled Capture" },
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    dateFormat.format(Date(session.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoChip("${session.frameCount} frames")
                    InfoChip("${formatCharCount(session.characterCount)} chars")
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = OnSurfaceVariantDark.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun InfoChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceVariantDark.copy(alpha = 0.6f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariantDark
        )
    }
}

private fun formatCharCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}
