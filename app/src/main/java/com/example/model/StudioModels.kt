package com.example.model

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class SubtitleLanguageMode(
    val displayLabel: String,
    val badgeLabel: String
) {
    SINHALA_ENGLISH_MIXED("Sinhala + English", "සිං + EN"),
    SINHALA_UNICODE("Sinhala Unicode", "සිංහල"),
    DUAL_SINHALA_ENGLISH("Dual Line (සිං + EN)", "DUAL")
}

enum class SubtitleFontOption(val displayName: String) {
    NOTO_SANS_SINHALA("Noto Sans Sinhala"),
    ABHAYA_LIBRE("Abhaya Libre")
}

enum class SubtitlePresetStyle(val displayName: String) {
    VEED_NEON_PILL("VEED Neon Pill"),
    REELS_BOLD_OUTLINE("Reels Bold"),
    MINIMAL_SHADOW("Minimal Clean"),
    CINEMA_BAR("Cinema Box")
}

enum class SubtitlePosition(val displayName: String) {
    BOTTOM("Bottom"),
    CENTER("Center"),
    TOP("Top")
}

@JsonClass(generateAdapter = true)
data class SubtitleStyleConfig(
    val fontOption: SubtitleFontOption = SubtitleFontOption.NOTO_SANS_SINHALA,
    val presetStyle: SubtitlePresetStyle = SubtitlePresetStyle.VEED_NEON_PILL,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    val fontSizeSp: Float = 22f,
    val primaryTextHex: Long = 0xFFFFFFFF,
    val accentHex: Long = 0xFFFACC15,
    val boxAlpha: Float = 0.78f
)

@JsonClass(generateAdapter = true)
data class SubtitleCue(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val primaryText: String,
    val secondaryText: String = ""
)

enum class AutoZoomIntensity(
    val displayName: String,
    val maxScale: Float,
    val subtitle: String
) {
    OFF("Off", 1.0f, "Static original framing"),
    SUBTLE("Subtle", 1.12f, "Gentle documentary Ken Burns"),
    DYNAMIC_REELS("Dynamic Reels", 1.24f, "VEED-style speech punch-ins"),
    ENERGETIC("Energetic", 1.32f, "Fast creator jump-zooms")
}

@JsonClass(generateAdapter = true)
data class ZoomKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val targetScale: Float = 1.18f,
    val panX: Float = 0f,
    val panY: Float = -0.04f,
    val label: String = "Speech Emphasis"
)

enum class BRollDisplayMode(val displayName: String) {
    CUTAWAY_FULL("Full Cutaway"),
    SPLIT_TOP("Split Top"),
    FLOATING_CARD("Floating Card")
}

@JsonClass(generateAdapter = true)
data class BRollItem(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val prompt: String,
    val label: String,
    val imagePath: String? = null,
    val displayMode: BRollDisplayMode = BRollDisplayMode.CUTAWAY_FULL,
    val enabled: Boolean = true
)

enum class ExportAspectRatio(
    val displayName: String,
    val ratioFloat: Float,
    val outWidth1080: Int,
    val outHeight1080: Int,
    val outWidth720: Int,
    val outHeight720: Int
) {
    REELS_9_16("9:16 Reels", 9f / 16f, 1080, 1920, 720, 1280),
    YOUTUBE_16_9("16:9 YouTube", 16f / 9f, 1920, 1080, 1280, 720),
    PORTRAIT_4_5("4:5 Feed", 4f / 5f, 1080, 1350, 720, 900),
    SQUARE_1_1("1:1 Square", 1f, 1080, 1080, 720, 720)
}

enum class ExportResolution(val displayName: String) {
    HD_720P("720p HD (Fast)"),
    FHD_1080P("1080p Full HD")
}

@JsonClass(generateAdapter = true)
data class VideoMetadata(
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long
)

enum class StudioTab(val title: String) {
    SUBTITLES("Subtitles"),
    AUTO_ZOOM("Auto Zoom"),
    AI_BROLL("AI B-Roll"),
    STYLE("Style"),
    EXPORT("Export")
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object ExtractingAudio : AiTaskState
    data object GeneratingSubtitles : AiTaskState
    data class GeneratingBRoll(val current: Int, val total: Int, val label: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progressPercent: Int, val statusLabel: String) : ExportState
    data class Completed(
        val outputFilePath: String,
        val publicUriString: String?,
        val fileSizeBytes: Long
    ) : ExportState
    data class Error(val message: String) : ExportState
}

data class StudioProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Project",
    val videoMetadata: VideoMetadata? = null,
    val detectedTopic: String = "",
    val languageMode: SubtitleLanguageMode = SubtitleLanguageMode.SINHALA_ENGLISH_MIXED,
    val subtitles: List<SubtitleCue> = emptyList(),
    val styleConfig: SubtitleStyleConfig = SubtitleStyleConfig(),
    val autoZoomIntensity: AutoZoomIntensity = AutoZoomIntensity.DYNAMIC_REELS,
    val zoomKeyframes: List<ZoomKeyframe> = emptyList(),
    val bRollItems: List<BRollItem> = emptyList(),
    val aspectRatio: ExportAspectRatio = ExportAspectRatio.REELS_9_16,
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val burnSubtitlesOnExport: Boolean = true,
    val burnAutoZoomOnExport: Boolean = true,
    val burnBRollOnExport: Boolean = true,
    val waveformSamples: List<Float> = emptyList(),
    val lastExportedPath: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)
