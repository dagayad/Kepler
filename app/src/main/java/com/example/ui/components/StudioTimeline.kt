package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.media.MediaHelper
import com.example.model.StudioProject
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VeedCyan
import com.example.ui.theme.VeedIndigo
import com.example.ui.theme.VeedViolet

@Composable
fun StudioTimeline(
    project: StudioProject,
    playheadMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onQuickAutoGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationMs = (project.videoMetadata?.durationMs ?: 10_000L).coerceAtLeast(1_000L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioPanel)
            .border(1.dp, StudioBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Transport Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VeedIndigo)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                        tint = TextPrimary
                    )
                }

                Column {
                    Text(
                        text = "${MediaHelper.formatTimecode(playheadMs)} / ${MediaHelper.formatTimecode(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "${project.subtitles.size} captions • ${project.bRollItems.count { it.imagePath != null }} B-Roll",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            if (project.subtitles.isEmpty()) {
                Surface(
                    onClick = onQuickAutoGenerate,
                    shape = RoundedCornerShape(10.dp),
                    color = VeedIndigo.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VeedIndigo)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = VeedIndigo,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AI Auto-Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrubbable Multi-Track Canvas (Audio Waveform + Subtitles + AI B-Roll + Zoom Keyframes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioSurface)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                .padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekMs((ratio * durationMs).toLong())
                        }
                    }
                    .pointerInput(durationMs) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekMs((ratio * durationMs).toLong())
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Track 1: Audio Waveform (top 26.dp)
                val peaks = project.waveformSamples.ifEmpty { List(60) { 0.25f } }
                val barStep = w / peaks.size.toFloat()
                val barWidth = (barStep * 0.56f).coerceAtLeast(2f)
                val waveCenterY = 14.dp.toPx()
                val maxWaveAmp = 11.dp.toPx()
                peaks.forEachIndexed { idx, amp ->
                    val x = idx * barStep + (barStep - barWidth) * 0.5f
                    val barH = (amp * maxWaveAmp * 2f).coerceAtLeast(3f)
                    val progressRatio = x / w
                    val playedRatio = playheadMs.toFloat() / durationMs.toFloat()
                    val color = if (progressRatio <= playedRatio) VeedIndigo else StudioElevated
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, waveCenterY - barH * 0.5f),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }

                // Track 2: Subtitles Track (y = 30.dp..44.dp)
                val subTop = 30.dp.toPx()
                val subHeight = 14.dp.toPx()
                project.subtitles.forEach { cue ->
                    val left = (cue.startMs.toFloat() / durationMs) * w
                    val right = (cue.endMs.toFloat() / durationMs) * w
                    val blockW = (right - left).coerceAtLeast(4f)
                    drawRoundRect(
                        color = VeedIndigo.copy(alpha = 0.85f),
                        topLeft = Offset(left, subTop),
                        size = Size(blockW, subHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }

                // Track 3: AI B-Roll Track (y = 48.dp..60.dp)
                val bRollTop = 48.dp.toPx()
                val bRollHeight = 12.dp.toPx()
                project.bRollItems.filter { it.enabled }.forEach { item ->
                    val left = (item.startMs.toFloat() / durationMs) * w
                    val right = (item.endMs.toFloat() / durationMs) * w
                    val blockW = (right - left).coerceAtLeast(6f)
                    val color = if (item.imagePath != null) VeedCyan else VeedCyan.copy(alpha = 0.35f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, bRollTop),
                        size = Size(blockW, bRollHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }

                // Track 4: Auto-Zoom Keyframes (y = 65.dp)
                val zoomY = 67.dp.toPx()
                val r = 4.dp.toPx()
                project.zoomKeyframes.forEach { kf ->
                    val cx = (kf.startMs.toFloat() / durationMs) * w
                    val diamond = Path().apply {
                        moveTo(cx, zoomY - r)
                        lineTo(cx + r, zoomY)
                        lineTo(cx, zoomY + r)
                        lineTo(cx - r, zoomY)
                        close()
                    }
                    drawPath(diamond, color = VeedViolet)
                }

                // Playhead Needle
                val playX = ((playheadMs.toFloat() / durationMs) * w).coerceIn(0f, w)
                drawLine(
                    color = TextPrimary,
                    start = Offset(playX, 0f),
                    end = Offset(playX, h),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = VeedIndigo,
                    radius = 4.5.dp.toPx(),
                    center = Offset(playX, 4.5.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimelineLegendChip("Audio", VeedIndigo)
            TimelineLegendChip("සිංහල Captions", VeedIndigo.copy(alpha = 0.85f))
            TimelineLegendChip("AI B-Roll", VeedCyan)
            TimelineLegendChip("Zoom", VeedViolet)
        }
    }
}

@Composable
private fun TimelineLegendChip(label: String, dotColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
