package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.BRollItem
import com.example.model.ExportAspectRatio
import com.example.model.SubtitleCue
import com.example.model.SubtitleLanguageMode
import com.example.model.ZoomKeyframe
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

data class AiVideoAnalysisResult(
    val detectedTopic: String,
    val cues: List<SubtitleCue>,
    val zoomKeyframes: List<ZoomKeyframe>,
    val bRollSuggestions: List<BRollItem>
)

class KeplerAiService {

    companion object {
        private const val TAG = "KeplerAiService"
        private const val ANALYSIS_MODEL = "gemini-3.8-flash"
        private const val IMAGE_MODEL = "gemini-nano-banana-2.1"
    }

    private val studioAnalysisSchema = Schema.obj(
        properties = mapOf(
            "topic" to Schema.string(description = "Concise topic summary of the video in English"),
            "cues" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Subtitle start timestamp in milliseconds"),
                        "endMs" to Schema.integer(description = "Subtitle end timestamp in milliseconds"),
                        "primaryText" to Schema.string(description = "Primary subtitle text in requested script"),
                        "secondaryText" to Schema.string(description = "Optional English translation line for dual mode, else empty string")
                    )
                ),
                description = "Timed subtitle cues aligned to speech"
            ),
            "zoomMoments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Zoom emphasis start in milliseconds"),
                        "endMs" to Schema.integer(description = "Zoom emphasis end in milliseconds"),
                        "targetScale" to Schema.double(description = "Zoom scale factor between 1.10 and 1.28"),
                        "panX" to Schema.double(description = "Horizontal pan offset between -0.06 and 0.06"),
                        "panY" to Schema.double(description = "Vertical pan offset between -0.06 and 0.06"),
                        "label" to Schema.string(description = "Short description of why zoom occurs here")
                    )
                ),
                description = "3 to 5 speech-synced Ken Burns auto-zoom keyframes"
            ),
            "bRollSuggestions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Suggested B-Roll start time in milliseconds"),
                        "endMs" to Schema.integer(description = "Suggested B-Roll end time in milliseconds"),
                        "label" to Schema.string(description = "Short 2-3 word label for timeline chip"),
                        "prompt" to Schema.string(description = "Detailed photographic/cinematic English image prompt matching the speech topic")
                    )
                ),
                description = "2 to 4 AI B-Roll image suggestions matched to spoken topics"
            )
        )
    )

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ANALYSIS_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studioAnalysisSchema
            }
        )
    }

    suspend fun transcribeAndAnalyzeMedia(
        mediaBytes: ByteArray,
        mimeType: String,
        durationMs: Long,
        languageMode: SubtitleLanguageMode
    ): AiVideoAnalysisResult {
        val modeInstruction = when (languageMode) {
            SubtitleLanguageMode.SINHALA_ENGLISH_MIXED ->
                "Transcribe speech accurately using natural Sinhala Unicode script (සිංහල අකුරු) for Sinhala words and clean English for English words/terms. Keep secondaryText empty."
            SubtitleLanguageMode.SINHALA_UNICODE ->
                "Transcribe or translate all speech into pure, natural Sinhala Unicode script (සිංහල අකුරු). Keep secondaryText empty."
            SubtitleLanguageMode.DUAL_SINHALA_ENGLISH ->
                "Put accurate Sinhala Unicode script (සිංහල අකුරු) in primaryText and a concise English translation in secondaryText."
        }

        val promptText = """
            You are Kepler, a precision video subtitle and editing engine.
            Analyze the attached media (total duration: ${durationMs} ms).
            1. Perform exact speech-to-text transcription with tight Reels/YouTube subtitle phrasing (max 6-9 words per cue).
            2. $modeInstruction
            3. Ensure every startMs and endMs is strictly between 0 and $durationMs with endMs > startMs and non-overlapping order.
            4. Identify 3 to 5 key speech emphasis moments for Ken Burns Auto-Zoom.
            5. Identify 2 to 4 visual B-Roll moments matching what the speaker discusses, with rich cinematic English prompts.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            analysisModel.generateContent(
                content {
                    inlineData(mediaBytes, mimeType)
                    text(promptText)
                }
            )
        }

        val rawJson = response.text
            ?: throw IllegalStateException("AI returned an empty transcription response.")

        Log.d(TAG, "Received structured analysis JSON (${rawJson.length} chars)")
        val root = JSONObject(rawJson)
        val topic = root.optString("topic", "Creator Video")

        val cues = mutableListOf<SubtitleCue>()
        val cuesArray = root.optJSONArray("cues")
        if (cuesArray != null) {
            for (i in 0 until cuesArray.length()) {
                val obj = cuesArray.getJSONObject(i)
                val start = obj.optLong("startMs", 0L).coerceIn(0L, durationMs)
                val end = obj.optLong("endMs", start + 1500L).coerceIn(start + 250L, durationMs)
                val primary = obj.optString("primaryText", "").trim()
                val secondary = obj.optString("secondaryText", "").trim()
                if (primary.isNotEmpty()) {
                    cues.add(
                        SubtitleCue(
                            id = UUID.randomUUID().toString(),
                            startMs = start,
                            endMs = end,
                            primaryText = primary,
                            secondaryText = secondary
                        )
                    )
                }
            }
        }

        val zooms = mutableListOf<ZoomKeyframe>()
        val zoomArray = root.optJSONArray("zoomMoments")
        if (zoomArray != null) {
            for (i in 0 until zoomArray.length()) {
                val obj = zoomArray.getJSONObject(i)
                val start = obj.optLong("startMs", 0L).coerceIn(0L, durationMs)
                val end = obj.optLong("endMs", start + 1800L).coerceIn(start + 400L, durationMs)
                zooms.add(
                    ZoomKeyframe(
                        id = UUID.randomUUID().toString(),
                        startMs = start,
                        endMs = end,
                        targetScale = obj.optDouble("targetScale", 1.18).toFloat().coerceIn(1.08f, 1.32f),
                        panX = obj.optDouble("panX", 0.0).toFloat().coerceIn(-0.08f, 0.08f),
                        panY = obj.optDouble("panY", -0.02).toFloat().coerceIn(-0.08f, 0.08f),
                        label = obj.optString("label", "Speech Emphasis")
                    )
                )
            }
        }

        val bRolls = mutableListOf<BRollItem>()
        val bRollArray = root.optJSONArray("bRollSuggestions")
        if (bRollArray != null) {
            for (i in 0 until bRollArray.length()) {
                val obj = bRollArray.getJSONObject(i)
                val start = obj.optLong("startMs", 0L).coerceIn(0L, durationMs)
                val end = obj.optLong("endMs", start + 2400L).coerceIn(start + 600L, durationMs)
                val prompt = obj.optString("prompt", "").trim()
                if (prompt.isNotEmpty()) {
                    bRolls.add(
                        BRollItem(
                            id = UUID.randomUUID().toString(),
                            startMs = start,
                            endMs = end,
                            label = obj.optString("label", "B-Roll ${i + 1}"),
                            prompt = prompt
                        )
                    )
                }
            }
        }

        return AiVideoAnalysisResult(
            detectedTopic = topic,
            cues = cues.sortedBy { it.startMs },
            zoomKeyframes = zooms.sortedBy { it.startMs },
            bRollSuggestions = bRolls.sortedBy { it.startMs }
        )
    }

    suspend fun generateBRollImage(
        prompt: String,
        aspectRatio: ExportAspectRatio
    ): Bitmap {
        val targetRatio = when (aspectRatio) {
            ExportAspectRatio.REELS_9_16 -> AspectRatio.PORTRAIT_9x16
            ExportAspectRatio.YOUTUBE_16_9 -> AspectRatio.LANDSCAPE_16x9
            ExportAspectRatio.PORTRAIT_4_5 -> AspectRatio.PORTRAIT_3x4
            ExportAspectRatio.SQUARE_1_1 -> AspectRatio.SQUARE_1x1
        }

        val imageModel = Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(targetRatio)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )

        val cinematicPrompt = "$prompt, ultra-sharp editorial B-roll photography, dramatic natural lighting, high contrast, no text, no watermarks"
        val response = withTimeout(60_000L) {
            imageModel.generateContent(cinematicPrompt)
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("Image model did not return an image for B-Roll prompt.")
    }
}
