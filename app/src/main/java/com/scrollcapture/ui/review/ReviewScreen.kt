package com.scrollcapture.ui.review

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrollcapture.prompt.PromptTemplate
import com.scrollcapture.ui.theme.*
import com.scrollcapture.util.ClipboardHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.factory(sessionId))
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val editedText by viewModel.editedText.collectAsStateWithLifecycle()
    val selectedTemplate by viewModel.selectedTemplate.collectAsStateWithLifecycle()
    val customInstruction by viewModel.customInstruction.collectAsStateWithLifecycle()
    val generatedPrompt by viewModel.generatedPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Review Capture", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveEdits()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveEdits() }) {
                        Icon(Icons.Filled.Save, "Save", tint = Teal60)
                    }
                    IconButton(onClick = {
                        viewModel.deleteSession { onBack() }
                    }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = ErrorDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        containerColor = SurfaceDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats row
            session?.let { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip("${s.frameCount} frames", Modifier.weight(1f))
                    StatChip("${s.characterCount} chars", Modifier.weight(1f))
                    StatChip(formatDuration(s.durationMs), Modifier.weight(1f))
                }
            }

            // Captured text editor
            Text(
                "Captured Text",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceDark
            )

            OutlinedTextField(
                value = editedText,
                onValueChange = { viewModel.updateText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = OnSurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple60,
                    unfocusedBorderColor = OutlineDark,
                    focusedContainerColor = SurfaceContainerDark,
                    unfocusedContainerColor = SurfaceContainerDark
                )
            )

            // Copy raw text
            OutlinedButton(
                onClick = {
                    ClipboardHelper.copyToClipboard(context, "Captured Text", editedText)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Raw Text")
            }

            // Share button
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, editedText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share captured text"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share Text")
            }

            HorizontalDivider(color = OutlineDark)

            // Prompt Builder section
            Text(
                "Generate AI Prompt",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceDark
            )

            Text(
                "Select a template to create a ready-to-paste prompt for any AI tool",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )

            // Template chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PromptTemplate.entries.forEach { template ->
                    FilterChip(
                        selected = selectedTemplate == template,
                        onClick = { viewModel.selectTemplate(template) },
                        label = {
                            Text("${template.emoji} ${template.label}", fontSize = 13.sp)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceContainerDark,
                            selectedContainerColor = Purple40.copy(alpha = 0.3f),
                            labelColor = OnSurfaceVariantDark,
                            selectedLabelColor = OnSurfaceDark
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = OutlineDark,
                            selectedBorderColor = Purple60,
                            enabled = true,
                            selected = selectedTemplate == template
                        )
                    )
                }
            }

            // Custom instruction input
            AnimatedVisibility(visible = selectedTemplate == PromptTemplate.CUSTOM) {
                OutlinedTextField(
                    value = customInstruction,
                    onValueChange = { viewModel.updateCustomInstruction(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type your instruction…", color = OnSurfaceVariantDark) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal60,
                        unfocusedBorderColor = OutlineDark,
                        focusedContainerColor = SurfaceContainerDark,
                        unfocusedContainerColor = SurfaceContainerDark
                    ),
                    minLines = 2
                )
            }

            // Prompt preview
            AnimatedVisibility(visible = selectedTemplate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Prompt Preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurfaceVariantDark
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerHighDark)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (generatedPrompt.length > 600)
                                generatedPrompt.take(600) + "…"
                            else generatedPrompt,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = OnSurfaceDark.copy(alpha = 0.9f)
                        )
                    }

                    // Copy prompt button
                    Button(
                        onClick = {
                            ClipboardHelper.copyToClipboard(
                                context, "AI Prompt", generatedPrompt
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple40)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Copy Prompt to Clipboard",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = SurfaceContainerDark
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Teal60,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
}
