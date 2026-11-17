package com.example.ui.components

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.media.KenBurnsMath
import com.example.model.AutoZoomIntensity
import com.example.model.BRollDisplayMode
import com.example.model.StudioProject
import com.example.model.SubtitleFontOption
import com.example.model.SubtitlePosition
import com.example.model.SubtitlePresetStyle
import com.example.ui.theme.AbhayaLibreFontFamily
import com.example.ui.theme.NotoSansSinhalaFontFamily
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VeedCyan
import com.example.ui.theme.VeedIndigo
import com.example.ui.theme.VeedViolet
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun StudioPreviewStage(
    project: StudioProject,
    playheadMs: Long,
    isPlaying: Boolean,
    seekRequestMs: Long?,
    onPlayheadChanged: (Long) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onSeekConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoUri = project.videoMetadata?.uriString

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            if (videoUri != null) {
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
                prepare()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingChanged(playing)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && !exoPlayer.isPlaying) {
            exoPlayer.play()
        } else if (!isPlaying && exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(seekRequestMs) {
        if (seekRequestMs != null) {
            exoPlayer.seekTo(seekRequestMs)
            onSeekConsumed()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            if (exoPlayer.isPlaying) {
                onPlayheadChanged(exoPlayer.currentPosition.coerceAtLeast(0L))
            }
            delay(40L)
        }
    }

    val durationMs = project.videoMetadata?.durationMs ?: 10_000L
    val kenBurns = remember(playheadMs, project.autoZoomIntensity, project.zoomKeyframes) {
        KenBurnsMath.computeTransformAt(
            timeMs = playheadMs,
            durationMs = durationMs,
            intensity = project.autoZoomIntensity,
            keyframes = project.zoomKeyframes
        )
    }

    val activeCue = remember(playheadMs, project.subtitles) {
        project.subtitles.firstOrNull { playheadMs in it.startMs..it.endMs }
    }

    val activeBRoll = remember(playheadMs, project.bRollItems) {
        project.bRollItems.firstOrNull {
            it.enabled && it.imagePath != null && playheadMs in it.startMs..it.endMs
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioBackground),
        contentAlignment = Alignment.Center
    ) {
        val stageMaxHeight = maxHeight.coerceAtMost(360.dp)
        Box(
            modifier = Modifier
                .height(stageMaxHeight)
                .aspectRatio(project.aspectRatio.ratioFloat, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
        ) {
            // 1. Live Video TextureView transformed by Ken Burns Auto-Zoom
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(
                        R.layout.kepler_player_view,
                        null,
                        false
                    ) as PlayerView).apply {
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kenBurns.scale
                        scaleY = kenBurns.scale
                        translationX = kenBurns.panX * size.width
                        translationY = kenBurns.panY * size.height
                    }
            )

            // 2. Live AI B-Roll Overlay
            AnimatedVisibility(
                visible = activeBRoll != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                activeBRoll?.let { bRoll ->
                    val bRollProgress = ((playheadMs - bRoll.startMs).toFloat() /
                        (bRoll.endMs - bRoll.startMs).coerceAtLeast(300L)).coerceIn(0f, 1f)
                    val bRollZoom = 1f + 0.08f * bRollProgress
                    val painter = rememberAsyncImagePainter(model = File(bRoll.imagePath!!))

                    when (bRoll.displayMode) {
                        BRollDisplayMode.CUTAWAY_FULL -> {
                            Image(
                                painter = painter,
                                contentDescription = bRoll.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = bRollZoom
                                        scaleY = bRollZoom
                                    }
                            )
                        }
                        BRollDisplayMode.SPLIT_TOP -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.48f)
                                    .align(Alignment.TopCenter)
                                    .border(1.5.dp, VeedIndigo)
                            ) {
                                Image(
                                    painter = painter,
                                    contentDescription = bRoll.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = bRollZoom
                                            scaleY = bRollZoom
                                        }
                                )
                            }
                        }
                        BRollDisplayMode.FLOATING_CARD -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 28.dp, start = 24.dp, end = 24.dp)
                                    .fillMaxWidth(0.76f)
                                    .aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(2.dp, VeedIndigo, RoundedCornerShape(14.dp))
                            ) {
                                Image(
                                    painter = painter,
                                    contentDescription = bRoll.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = bRollZoom
                                            scaleY = bRollZoom
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Telemetry Pills (Auto Zoom + AI B-Roll status)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (project.autoZoomIntensity != AutoZoomIntensity.OFF) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(StudioPanel.copy(alpha = 0.82f))
                            .border(1.dp, VeedViolet.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ZoomIn,
                            contentDescription = null,
                            tint = VeedViolet,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "%.2fx".format(kenBurns.scale),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
                if (activeBRoll != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(StudioPanel.copy(alpha = 0.86f))
                            .border(1.dp, VeedCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = VeedCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "B-ROLL",
                            style = MaterialTheme.typography.labelSmall,
                            color = VeedCyan
                        )
                    }
                }
            }

            // 4. Live Sinhala + English Unicode Subtitle Overlay
            if (activeCue != null) {
                val style = project.styleConfig
                val fontFamily = when (style.fontOption) {
                    SubtitleFontOption.NOTO_SANS_SINHALA -> NotoSansSinhalaFontFamily
                    SubtitleFontOption.ABHAYA_LIBRE -> AbhayaLibreFontFamily
                }
                val alignment = when (style.position) {
                    SubtitlePosition.TOP -> Alignment.TopCenter
                    SubtitlePosition.CENTER -> Alignment.Center
                    SubtitlePosition.BOTTOM -> Alignment.BottomCenter
                }
                val edgePadding = when (style.position) {
                    SubtitlePosition.TOP -> Modifier.padding(top = 36.dp)
                    SubtitlePosition.CENTER -> Modifier
                    SubtitlePosition.BOTTOM -> Modifier.padding(bottom = 26.dp)
                }

                val previewFontSp = (style.fontSizeSp * 0.72f).coerceIn(12f, 26f).sp

                Box(
                    modifier = Modifier
                        .align(alignment)
                        .then(edgePadding)
                        .padding(horizontal = 14.dp)
                        .widthIn(max = 280.dp)
                ) {
                    val containerModifier = when (style.presetStyle) {
                        SubtitlePresetStyle.VEED_NEON_PILL -> Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0E101A).copy(alpha = style.boxAlpha))
                            .border(1.5.dp, VeedIndigo, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                        SubtitlePresetStyle.CINEMA_BAR -> Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = style.boxAlpha))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                        SubtitlePresetStyle.REELS_BOLD_OUTLINE,
                        SubtitlePresetStyle.MINIMAL_SHADOW -> Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    }

                    Column(
                        modifier = containerModifier,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeCue.primaryText,
                            style = TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = previewFontSp,
                                color = Color(style.primaryTextHex),
                                textAlign = TextAlign.Center,
                                shadow = if (style.presetStyle == SubtitlePresetStyle.REELS_BOLD_OUTLINE ||
                                    style.presetStyle == SubtitlePresetStyle.MINIMAL_SHADOW
                                ) {
                                    Shadow(color = Color.Black, offset = Offset(0f, 3f), blurRadius = 8f)
                                } else null
                            )
                        )
                        if (activeCue.secondaryText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeCue.secondaryText,
                                style = TextStyle(
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = (previewFontSp.value * 0.78f).sp,
                                    color = Color(style.accentHex),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
