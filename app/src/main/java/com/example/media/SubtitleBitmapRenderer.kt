package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.SubtitleCue
import com.example.model.SubtitleFontOption
import com.example.model.SubtitlePosition
import com.example.model.SubtitlePresetStyle
import com.example.model.SubtitleStyleConfig
import kotlin.math.max
import kotlin.math.min

class SubtitleBitmapRenderer(private val context: Context) {

    private fun loadTypeface(fontOption: SubtitleFontOption): Typeface {
        val fontRes = when (fontOption) {
            SubtitleFontOption.NOTO_SANS_SINHALA -> R.font.noto_sans_sinhala
            SubtitleFontOption.ABHAYA_LIBRE -> R.font.abhaya_libre
        }
        return ResourcesCompat.getFont(context, fontRes) ?: Typeface.DEFAULT_BOLD
    }

    /**
     * Renders a full-frame transparent ARGB_8888 Bitmap containing the Sinhala/English
     * subtitle styled exactly per [style], ready for Media3 BitmapOverlay burning.
     */
    fun renderSubtitleOverlayBitmap(
        cue: SubtitleCue,
        style: SubtitleStyleConfig,
        frameWidth: Int,
        frameHeight: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val typeface = loadTypeface(style.fontOption)

        val scaleFactor = min(frameWidth, frameHeight) / 1080f
        val primaryTextSizePx = (style.fontSizeSp * 2.25f * scaleFactor).coerceAtLeast(28f)
        val secondaryTextSizePx = (primaryTextSizePx * 0.72f).coerceAtLeast(22f)
        val maxContentWidth = (frameWidth * 0.84f).toInt().coerceAtLeast(200)

        val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = primaryTextSizePx
            color = style.primaryTextHex.toInt()
        }

        val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.NORMAL)
            textSize = secondaryTextSizePx
            color = style.accentHex.toInt()
        }

        val primaryLayout = StaticLayout.Builder
            .obtain(cue.primaryText, 0, cue.primaryText.length, primaryPaint, maxContentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(6f * scaleFactor, 1.08f)
            .setIncludePad(true)
            .build()

        val hasSecondary = cue.secondaryText.isNotBlank()
        val secondaryLayout = if (hasSecondary) {
            StaticLayout.Builder
                .obtain(cue.secondaryText, 0, cue.secondaryText.length, secondaryPaint, maxContentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(4f * scaleFactor, 1.05f)
                .setIncludePad(true)
                .build()
        } else null

        var measuredTextWidth = 0f
        for (i in 0 until primaryLayout.lineCount) {
            measuredTextWidth = max(measuredTextWidth, primaryLayout.getLineWidth(i))
        }
        if (secondaryLayout != null) {
            for (i in 0 until secondaryLayout.lineCount) {
                measuredTextWidth = max(measuredTextWidth, secondaryLayout.getLineWidth(i))
            }
        }

        val padH = 34f * scaleFactor
        val padV = 20f * scaleFactor
        val lineGap = if (hasSecondary) 10f * scaleFactor else 0f
        val totalTextHeight = primaryLayout.height +
            (secondaryLayout?.height ?: 0) +
            lineGap

        val boxWidth = min(frameWidth * 0.92f, measuredTextWidth + padH * 2f)
        val boxHeight = totalTextHeight + padV * 2f

        val boxTop = when (style.position) {
            SubtitlePosition.TOP -> frameHeight * 0.10f
            SubtitlePosition.CENTER -> (frameHeight - boxHeight) * 0.5f
            SubtitlePosition.BOTTOM -> frameHeight * 0.84f - boxHeight
        }.coerceIn(24f, (frameHeight - boxHeight - 24f).coerceAtLeast(24f))

        val boxLeft = (frameWidth - boxWidth) * 0.5f
        val rect = RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)

        when (style.presetStyle) {
            SubtitlePresetStyle.VEED_NEON_PILL -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((style.boxAlpha * 255).toInt().coerceIn(40, 255), 14, 16, 26)
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 3f * scaleFactor
                    color = 0xFF576BFF.toInt()
                }
                val radius = 22f * scaleFactor
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
            }
            SubtitlePresetStyle.CINEMA_BAR -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((style.boxAlpha * 255).toInt().coerceIn(80, 255), 0, 0, 0)
                }
                canvas.drawRoundRect(rect, 10f * scaleFactor, 10f * scaleFactor, bgPaint)
            }
            SubtitlePresetStyle.REELS_BOLD_OUTLINE -> {
                primaryPaint.setShadowLayer(10f * scaleFactor, 0f, 4f * scaleFactor, Color.BLACK)
                secondaryPaint.setShadowLayer(8f * scaleFactor, 0f, 3f * scaleFactor, Color.BLACK)
            }
            SubtitlePresetStyle.MINIMAL_SHADOW -> {
                primaryPaint.setShadowLayer(8f * scaleFactor, 0f, 3f * scaleFactor, Color.argb(210, 0, 0, 0))
                secondaryPaint.setShadowLayer(6f * scaleFactor, 0f, 2f * scaleFactor, Color.argb(200, 0, 0, 0))
            }
        }

        val textStartX = (frameWidth - maxContentWidth) * 0.5f
        val primaryTop = boxTop + padV

        canvas.save()
        canvas.translate(textStartX, primaryTop)
        if (style.presetStyle == SubtitlePresetStyle.REELS_BOLD_OUTLINE) {
            val strokePaint = TextPaint(primaryPaint).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 8f * scaleFactor
                color = Color.BLACK
            }
            StaticLayout.Builder
                .obtain(cue.primaryText, 0, cue.primaryText.length, strokePaint, maxContentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(6f * scaleFactor, 1.08f)
                .setIncludePad(true)
                .build()
                .draw(canvas)
        }
        primaryLayout.draw(canvas)
        canvas.restore()

        if (secondaryLayout != null) {
            canvas.save()
            canvas.translate(textStartX, primaryTop + primaryLayout.height + lineGap)
            secondaryLayout.draw(canvas)
            canvas.restore()
        }

        return bitmap
    }
}
