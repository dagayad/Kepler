package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.media.MediaHelper
import com.example.model.AutoZoomIntensity
import com.example.model.BRollDisplayMode
import com.example.model.ExportAspectRatio
import com.example.model.ExportResolution
import com.example.model.ExportState
import com.example.model.StudioProject
import com.example.model.SubtitleFontOption
import com.example.model.SubtitleLanguageMode
import com.example.model.SubtitlePosition
import com.example.model.SubtitlePresetStyle
import com.example.model.SubtitleStyleConfig
import com.example.ui.theme.AbhayaLibreFontFamily
import com.example.ui.theme.NotoSansSinhalaFontFamily
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VeedCyan
import com.example.ui.theme.VeedEmerald
import com.example.ui.theme.VeedGold
import com.example.ui.theme.VeedIndigo
import com.example.ui.theme.VeedViolet
import java.io.File

@Composable
fun SubtitlesInspectorPanel(
    project: StudioProject,
    playheadMs: Long,
    isAiBusy: Boolean,
    onSelectLanguageMode: (SubtitleLanguageMode) -> Unit,
    onGenerateAiSubtitles: () -> Unit,
    onUpdateCue: (String, String, String) -> Unit,
    onAddCueAtPlayhead: () -> Unit,
    onDeleteCue: (String) -> Unit,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFont = if (project.styleConfig.fontOption == SubtitleFontOption.NOTO_SANS_SINHALA) {
        NotoSansSinhalaFontFamily
    } else {
        AbhayaLibreFontFamily
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Speech Language Mode",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubtitleLanguageMode.entries.forEach { mode ->
                    val selected = project.languageMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLanguageMode(mode) },
                        label = {
                            Text(
                                text = mode.displayLabel,
                                fontFamily = NotoSansSinhalaFontFamily
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VeedIndigo,
                            selectedLabelColor = TextPrimary,
                            containerColor = StudioSurface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateAiSubtitles,
                    enabled = !isAiBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("generate_subtitles_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VeedIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (project.subtitles.isEmpty()) "Generate Auto Subtitles" else "Re-Transcribe Video",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onAddCueAtPlayhead,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_cue_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add subtitle cue", tint = TextPrimary)
                }
            }
        }

        if (project.subtitles.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Subtitles,
                            contentDescription = null,
                            tint = VeedIndigo,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Sinhala / English Subtitles Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Generate Auto Subtitles to transcribe speech in proper සිංහල Unicode + English, or tap + to add a cue manually.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontFamily = NotoSansSinhalaFontFamily
                        )
                    }
                }
            }
        } else {
            items(project.subtitles, key = { it.id }) { cue ->
                val isActive = playheadMs in cue.startMs..cue.endMs
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) StudioElevated else StudioSurface,
                    border = BorderStroke(
                        width = if (isActive) 1.5.dp else 1.dp,
                        color = if (isActive) VeedIndigo else StudioBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                onClick = { onSeekToMs(cue.startMs) },
                                shape = RoundedCornerShape(8.dp),
                                color = VeedIndigo.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${MediaHelper.formatTimecode(cue.startMs)} → ${MediaHelper.formatTimecode(cue.endMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VeedIndigo,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteCue(cue.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete subtitle",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = cue.primaryText,
                            onValueChange = { onUpdateCue(cue.id, it, cue.secondaryText) },
                            textStyle = TextStyle(
                                fontFamily = activeFont,
                                fontSize = 15.sp,
                                color = TextPrimary
                            ),
                            label = { Text("Subtitle (සිංහල / English)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VeedIndigo,
                                unfocusedBorderColor = StudioBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        if (project.languageMode == SubtitleLanguageMode.DUAL_SINHALA_ENGLISH ||
                            cue.secondaryText.isNotBlank()
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = cue.secondaryText,
                                onValueChange = { onUpdateCue(cue.id, cue.primaryText, it) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = VeedGold),
                                label = { Text("English Line") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VeedGold,
                                    unfocusedBorderColor = StudioBorder
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
fun AutoZoomInspectorPanel(
    project: StudioProject,
    onSelectIntensity: (AutoZoomIntensity) -> Unit,
    onAddKeyframeAtPlayhead: () -> Unit,
    onDeleteKeyframe: (String) -> Unit,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Ken Burns Auto-Zoom Intensity",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoZoomIntensity.entries.forEach { mode ->
                    val selected = project.autoZoomIntensity == mode
                    Surface(
                        onClick = { onSelectIntensity(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) VeedViolet.copy(alpha = 0.18f) else StudioSurface,
                        border = BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) VeedViolet else StudioBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "${mode.maxScale}x",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) VeedViolet else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speech-Synced Keyframes (${project.zoomKeyframes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                OutlinedButton(
                    onClick = onAddKeyframeAtPlayhead,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, VeedViolet)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = VeedViolet, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Keyframe", color = TextPrimary)
                }
            }
        }

        items(project.zoomKeyframes, key = { it.id }) { kf ->
            Surface(
                onClick = { onSeekToMs(kf.startMs) },
                shape = RoundedCornerShape(12.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = null, tint = VeedViolet)
                        Column {
                            Text(kf.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "${MediaHelper.formatTimecode(kf.startMs)} → ${MediaHelper.formatTimecode(kf.endMs)} • ${"%.2fx".format(kf.targetScale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = { onDeleteKeyframe(kf.id) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete keyframe", tint = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun BRollInspectorPanel(
    project: StudioProject,
    isAiBusy: Boolean,
    onGenerateAllBRoll: () -> Unit,
    onGenerateSingleBRoll: (String) -> Unit,
    onPickCustomPhotoForItem: (String) -> Unit,
    onAddBRollSlot: () -> Unit,
    onUpdatePrompt: (String, String) -> Unit,
    onUpdateDisplayMode: (String, BRollDisplayMode) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onDeleteBRoll: (String) -> Unit,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (project.detectedTopic.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VeedCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, VeedCyan.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "Detected Topic: ${project.detectedTopic}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VeedCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateAllBRoll,
                    enabled = !isAiBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("generate_broll_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VeedIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate AI B-Roll Images", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onAddBRollSlot,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add B-Roll slot", tint = TextPrimary)
                }
            }
        }

        if (project.bRollItems.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = VeedCyan, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No B-Roll Slots Planned", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Run Generate Auto Subtitles first so Gemini plans matching B-Roll scenes from your speech, or tap + to add a custom B-Roll slot.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(project.bRollItems, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, if (item.enabled) VeedCyan.copy(alpha = 0.5f) else StudioBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSeekToMs(item.startMs) }
                            ) {
                                Text(item.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    "${MediaHelper.formatTimecode(item.startMs)} → ${MediaHelper.formatTimecode(item.endMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VeedCyan
                                )
                            }
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { onToggleEnabled(item.id) },
                                colors = SwitchDefaults.colors(checkedTrackColor = VeedCyan)
                            )
                            IconButton(onClick = { onDeleteBRoll(item.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete B-Roll", tint = TextSecondary)
                            }
                        }

                        if (item.imagePath != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                painter = rememberAsyncImagePainter(model = File(item.imagePath)),
                                contentDescription = item.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = item.prompt,
                            onValueChange = { onUpdatePrompt(item.id, it) },
                            label = { Text("Gemini Image Prompt") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VeedCyan,
                                unfocusedBorderColor = StudioBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BRollDisplayMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = item.displayMode == mode,
                                    onClick = { onUpdateDisplayMode(item.id, mode) },
                                    label = { Text(mode.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VeedCyan.copy(alpha = 0.25f),
                                        selectedLabelColor = VeedCyan
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onGenerateSingleBRoll(item.id) },
                                enabled = !isAiBusy,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (item.imagePath == null) "Generate" else "Regenerate")
                            }
                            OutlinedButton(
                                onClick = { onPickCustomPhotoForItem(item.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StyleInspectorPanel(
    style: SubtitleStyleConfig,
    onUpdateStyle: ((SubtitleStyleConfig) -> SubtitleStyleConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Sinhala Unicode Font", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleFontOption.entries.forEach { font ->
                val selected = style.fontOption == font
                val previewFamily = if (font == SubtitleFontOption.NOTO_SANS_SINHALA) {
                    NotoSansSinhalaFontFamily
                } else {
                    AbhayaLibreFontFamily
                }
                Surface(
                    onClick = { onUpdateStyle { it.copy(fontOption = font) } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) VeedIndigo.copy(alpha = 0.2f) else StudioSurface,
                    border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) VeedIndigo else StudioBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("ආයුබෝවන්", fontFamily = previewFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(font.displayName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }

        Text("VEED Caption Style Preset", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubtitlePresetStyle.entries.forEach { preset ->
                FilterChip(
                    selected = style.presetStyle == preset,
                    onClick = { onUpdateStyle { it.copy(presetStyle = preset) } },
                    label = { Text(preset.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VeedIndigo,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        Text("Vertical Position", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitlePosition.entries.forEach { pos ->
                FilterChip(
                    selected = style.position == pos,
                    onClick = { onUpdateStyle { it.copy(position = pos) } },
                    label = { Text(pos.displayName) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VeedIndigo,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        Text("Font Size (${style.fontSizeSp.toInt()} sp)", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Slider(
            value = style.fontSizeSp,
            onValueChange = { v -> onUpdateStyle { it.copy(fontSizeSp = v) } },
            valueRange = 16f..34f,
            colors = SliderDefaults.colors(thumbColor = VeedIndigo, activeTrackColor = VeedIndigo)
        )

        Text("Primary Text Color", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val palette = listOf(0xFFFFFFFFL, 0xFFFACC15L, 0xFF22D3EEL, 0xFF576BFFL, 0xFFF43F5EL)
            palette.forEach { hex ->
                val selected = style.primaryTextHex == hex
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(hex))
                        .border(if (selected) 3.dp else 1.dp, if (selected) TextPrimary else StudioBorder, CircleShape)
                        .clickable { onUpdateStyle { it.copy(primaryTextHex = hex) } }
                )
            }
        }
    }
}

@Composable
fun ExportInspectorPanel(
    project: StudioProject,
    exportState: ExportState,
    onSelectAspectRatio: (ExportAspectRatio) -> Unit,
    onSelectResolution: (ExportResolution) -> Unit,
    onToggleFlags: (Boolean?, Boolean?, Boolean?) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShareExportedFile: (String) -> Unit,
    onResetExportState: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Target Platform Aspect Ratio", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportAspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = project.aspectRatio == ratio,
                    onClick = { onSelectAspectRatio(ratio) },
                    label = { Text(ratio.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VeedIndigo,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        Text("Resolution", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportResolution.entries.forEach { res ->
                FilterChip(
                    selected = project.resolution == res,
                    onClick = { onSelectResolution(res) },
                    label = { Text(res.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VeedIndigo,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = StudioSurface,
            border = BorderStroke(1.dp, StudioBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportToggleRow("Burn Sinhala + English Subtitles", project.burnSubtitlesOnExport) {
                    onToggleFlags(it, null, null)
                }
                ExportToggleRow("Bake Ken Burns Auto-Zoom", project.burnAutoZoomOnExport) {
                    onToggleFlags(null, it, null)
                }
                ExportToggleRow("Insert AI B-Roll Overlays", project.burnBRollOnExport) {
                    onToggleFlags(null, null, it)
                }
            }
        }

        when (exportState) {
            is ExportState.Idle -> {
                Button(
                    onClick = onStartExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_export_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VeedIndigo),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.MovieCreation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Ready MP4", style = MaterialTheme.typography.titleMedium)
                }
            }
            is ExportState.Exporting -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioElevated,
                    border = BorderStroke(1.dp, VeedIndigo)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(exportState.statusLabel, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("${exportState.progressPercent}%", style = MaterialTheme.typography.labelLarge, color = VeedIndigo)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = VeedIndigo,
                            trackColor = StudioPanel
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onCancelExport, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel Export")
                        }
                    }
                }
            }
            is ExportState.Completed -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VeedEmerald.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, VeedEmerald)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VeedEmerald)
                            Text("MP4 Export Complete!", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        val sizeMb = exportState.fileSizeBytes / (1024f * 1024f)
                        Text(
                            text = "Saved to Movies/Kepler (${"%.1f".format(sizeMb)} MB) with burned-in Sinhala Unicode captions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onShareExportedFile(exportState.outputFilePath) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = VeedEmerald)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share MP4")
                            }
                            OutlinedButton(onClick = onResetExportState, modifier = Modifier.weight(1f)) {
                                Text("Export Again")
                            }
                        }
                    }
                }
            }
            is ExportState.Error -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Export Error: ${exportState.message}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Button(onClick = onStartExport, colors = ButtonDefaults.buttonColors(containerColor = VeedIndigo)) {
                            Text("Retry Export")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = VeedIndigo)
        )
    }
}
