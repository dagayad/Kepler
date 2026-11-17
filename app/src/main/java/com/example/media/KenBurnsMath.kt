package com.example.media

import com.example.model.AutoZoomIntensity
import com.example.model.ZoomKeyframe
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class KenBurnsTransform(
    val scale: Float,
    val panX: Float,
    val panY: Float
)

object KenBurnsMath {

    fun computeTransformAt(
        timeMs: Long,
        durationMs: Long,
        intensity: AutoZoomIntensity,
        keyframes: List<ZoomKeyframe>
    ): KenBurnsTransform {
        if (intensity == AutoZoomIntensity.OFF) {
            return KenBurnsTransform(scale = 1.0f, panX = 0f, panY = 0f)
        }

        val intensityFactor = when (intensity) {
            AutoZoomIntensity.OFF -> 0f
            AutoZoomIntensity.SUBTLE -> 0.55f
            AutoZoomIntensity.DYNAMIC_REELS -> 1.0f
            AutoZoomIntensity.ENERGETIC -> 1.25f
        }

        val activeKeyframe = keyframes.firstOrNull { timeMs in it.startMs..it.endMs }
        if (activeKeyframe != null) {
            val span = (activeKeyframe.endMs - activeKeyframe.startMs).coerceAtLeast(300L).toFloat()
            val progress = ((timeMs - activeKeyframe.startMs).toFloat() / span).coerceIn(0f, 1f)
            // Smooth bell-shaped ease in/out inside the speech emphasis segment
            val envelope = if (progress < 0.25f) {
                easeInOut(progress / 0.25f)
            } else if (progress > 0.78f) {
                easeInOut((1f - progress) / 0.22f)
            } else {
                1f
            }

            val baseExtraScale = (activeKeyframe.targetScale - 1f).coerceIn(0.06f, 0.35f) * intensityFactor
            val currentScale = (1f + baseExtraScale * envelope).coerceIn(1f, intensity.maxScale + 0.04f)
            val panProgress = easeInOut(progress)
            return KenBurnsTransform(
                scale = currentScale,
                panX = activeKeyframe.panX * intensityFactor * panProgress,
                panY = activeKeyframe.panY * intensityFactor * panProgress
            )
        }

        // Gentle baseline Ken Burns breathing motion between speech keyframes
        val safeDuration = durationMs.coerceAtLeast(4000L).toFloat()
        val cycle = ((timeMs % 8000L) / 8000f) * 2f * PI.toFloat()
        val baseExtra = (intensity.maxScale - 1f) * 0.38f
        val smoothZoom = 1f + baseExtra * (0.5f - 0.5f * cos(cycle))
        val driftX = 0.015f * intensityFactor * sin(cycle * 0.5f)
        val driftY = 0.012f * intensityFactor * cos((timeMs / safeDuration) * PI.toFloat())
        return KenBurnsTransform(
            scale = smoothZoom,
            panX = driftX,
            panY = driftY
        )
    }

    private fun easeInOut(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return (0.5f - 0.5f * cos(clamped * PI.toFloat()))
    }
}
