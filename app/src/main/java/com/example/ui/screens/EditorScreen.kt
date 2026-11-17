package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.AiTaskState
import com.example.model.ExportAspectRatio
import com.example.model.ExportState
import com.example.model.StudioProject
import com.example.model.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.components.AutoZoomInspectorPanel
import com.example.ui.components.BRollInspectorPanel
import com.example.ui.components.ExportInspectorPanel
import com.example.ui.components.StudioPreviewStage
import com.example.ui.components.StudioTimeline
import com.example.ui.components.StyleInspectorPanel
import com.example.ui.components.SubtitlesInspectorPanel
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VeedCyan
import com.example.ui.theme.VeedIndigo
import java.io.File

@Composable
fun EditorScreen(
    project: StudioProject,
    selectedTab: StudioTab,
    aiTaskState: AiTaskState,
    exportState: ExportState,
    playheadMs: Long,
    isPlaying: Boolean,
    seekRequestMs: Long?,
    viewModel: StudioViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var pendingBRollItemId by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetId = pendingBRollItemId
        if (uri != null && targetId != null) {
            viewModel.attachCustomBRollPhoto(targetId, uri)
        }
        pendingBRollItemId = null
    }

    BackHandler {
        viewModel.updatePlaying(false)
        onNavigateBack()
    }

    val isAiBusy = aiTaskState is AiTaskState.ExtractingAudio ||
        aiTaskState is AiTaskState.GeneratingSubtitles ||
        aiTaskState is AiTaskState.GeneratingBRoll

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .statusBarsPadding()
    ) {
        // Top Studio Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioPanel)
                .border(1.dp, StudioBorder)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        viewModel.updatePlaying(false)
                        onNavigateBack()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to projects",
                        tint = TextPrimary
                    )
                }
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = project.languageMode.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = VeedCyan
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        val entries = ExportAspectRatio.entries
                        val next = entries[(entries.indexOf(project.aspectRatio) + 1) % entries.size]
                        viewModel.updateAspectRatio(next)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = StudioElevated,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Text(
                        text = project.aspectRatio.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                Button(
                    onClick = { viewModel.selectTab(StudioTab.EXPORT) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VeedIndigo),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("top_export_button")
                ) {
                    Text("Export", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // AI Progress / Error Banner
        AnimatedVisibility(visible = aiTaskState !is AiTaskState.Idle) {
            when (aiTaskState) {
                is AiTaskState.ExtractingAudio -> AiStatusBanner("Extracting audio track for Gemini AI...", false, null)
                is AiTaskState.GeneratingSubtitles -> AiStatusBanner("Transcribing Sinhala + English speech & planning Zoom/B-Roll...", false, null)
                is AiTaskState.GeneratingBRoll -> AiStatusBanner("Generating AI B-Roll (${aiTaskState.current}/${aiTaskState.total}): ${aiTaskState.label}...", false, null)
                is AiTaskState.Error -> AiStatusBanner(aiTaskState.message, true) { viewModel.dismissAiError() }
                is AiTaskState.Idle -> Unit
            }
        }

        // Adaptive Workspace (Compact vertical studio vs Wide two-pane tablet/PC studio)
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val isWideLayout = maxWidth >= 700.dp

            val inspectorContent: @Composable (Modifier) -> Unit = { mod ->
                when (selectedTab) {
                    StudioTab.SUBTITLES -> SubtitlesInspectorPanel(
                        project = project,
                        playheadMs = playheadMs,
                        isAiBusy = isAiBusy,
                        onSelectLanguageMode = viewModel::updateLanguageMode,
                        onGenerateAiSubtitles = viewModel::generateAutoSubtitlesAndPlan,
                        onUpdateCue = viewModel::updateSubtitleCue,
                        onAddCueAtPlayhead = viewModel::addSubtitleCueAtPlayhead,
                        onDeleteCue = viewModel::deleteSubtitleCue,
                        onSeekToMs = viewModel::requestSeek,
                        modifier = mod
                    )
                    StudioTab.AUTO_ZOOM -> AutoZoomInspectorPanel(
                        project = project,
                        onSelectIntensity = viewModel::updateAutoZoomIntensity,
                        onAddKeyframeAtPlayhead = viewModel::addZoomKeyframeAtPlayhead,
                        onDeleteKeyframe = viewModel::deleteZoomKeyframe,
                        onSeekToMs = viewModel::requestSeek,
                        modifier = mod
                    )
                    StudioTab.AI_BROLL -> BRollInspectorPanel(
                        project = project,
                        isAiBusy = isAiBusy,
                        onGenerateAllBRoll = viewModel::generateAllPendingBRoll,
                        onGenerateSingleBRoll = viewModel::generateSingleBRoll,
                        onPickCustomPhotoForItem = { itemId ->
                            pendingBRollItemId = itemId
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onAddBRollSlot = viewModel::addBRollSlotAtPlayhead,
                        onUpdatePrompt = viewModel::updateBRollPrompt,
                        onUpdateDisplayMode = viewModel::updateBRollDisplayMode,
                        onToggleEnabled = viewModel::toggleBRollEnabled,
                        onDeleteBRoll = viewModel::deleteBRoll,
                        onSeekToMs = viewModel::requestSeek,
                        modifier = mod
                    )
                    StudioTab.STYLE -> StyleInspectorPanel(
                        style = project.styleConfig,
                        onUpdateStyle = viewModel::updateStyleConfig,
                        modifier = mod
                    )
                    StudioTab.EXPORT -> ExportInspectorPanel(
                        project = project,
                        exportState = exportState,
                        onSelectAspectRatio = viewModel::updateAspectRatio,
                        onSelectResolution = viewModel::updateResolution,
                        onToggleFlags = viewModel::updateExportFlags,
                        onStartExport = viewModel::startExport,
                        onCancelExport = viewModel::cancelExport,
                        onShareExportedFile = { path ->
                            val file = File(path)
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Kepler Video"))
                        },
                        onResetExportState = viewModel::resetExportState,
                        modifier = mod
                    )
                }
            }

            if (isWideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                    ) {
                        StudioPreviewStage(
                            project = project,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            seekRequestMs = seekRequestMs,
                            onPlayheadChanged = viewModel::updatePlayhead,
                            onPlayingChanged = viewModel::updatePlaying,
                            onSeekConsumed = viewModel::clearSeekRequest,
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp)
                        )
                        StudioTimeline(
                            project = project,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            onTogglePlay = { viewModel.updatePlaying(!isPlaying) },
                            onSeekMs = viewModel::requestSeek,
                            onQuickAutoGenerate = viewModel::generateAutoSubtitlesAndPlan
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .background(StudioPanel)
                    ) {
                        inspectorContent(Modifier.weight(1f).fillMaxHeight())
                        NavigationRail(containerColor = StudioPanel) {
                            StudioTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = tab.title) },
                                    label = { Text(tab.title, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = TextPrimary,
                                        selectedTextColor = VeedIndigo,
                                        indicatorColor = VeedIndigo,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    StudioPreviewStage(
                        project = project,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        seekRequestMs = seekRequestMs,
                        onPlayheadChanged = viewModel::updatePlayhead,
                        onPlayingChanged = viewModel::updatePlaying,
                        onSeekConsumed = viewModel::clearSeekRequest,
                        modifier = Modifier
                            .height(220.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    StudioTimeline(
                        project = project,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        onTogglePlay = { viewModel.updatePlaying(!isPlaying) },
                        onSeekMs = viewModel::requestSeek,
                        onQuickAutoGenerate = viewModel::generateAutoSubtitlesAndPlan
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(StudioPanel)
                    ) {
                        inspectorContent(Modifier.fillMaxSize())
                    }
                    NavigationBar(
                        containerColor = StudioPanel,
                        tonalElevation = 0.dp
                    ) {
                        StudioTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                icon = { Icon(tabIcon(tab), contentDescription = tab.title) },
                                label = {
                                    Text(
                                        text = tab.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TextPrimary,
                                    selectedTextColor = VeedIndigo,
                                    indicatorColor = VeedIndigo,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiStatusBanner(
    message: String,
    isError: Boolean,
    onDismiss: (() -> Unit)?
) {
    val bg = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.16f) else VeedIndigo.copy(alpha = 0.16f)
    val borderColor = if (isError) MaterialTheme.colorScheme.error else VeedIndigo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .border(1.dp, borderColor)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isError) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = VeedIndigo
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextPrimary)
            }
        }
    }
}

private fun tabIcon(tab: StudioTab): ImageVector = when (tab) {
    StudioTab.SUBTITLES -> Icons.Filled.Subtitles
    StudioTab.AUTO_ZOOM -> Icons.Filled.ZoomIn
    StudioTab.AI_BROLL -> Icons.Filled.AutoAwesome
    StudioTab.STYLE -> Icons.Filled.Palette
    StudioTab.EXPORT -> Icons.Filled.MovieCreation
}
