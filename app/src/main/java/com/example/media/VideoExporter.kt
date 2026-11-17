package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.BRollDisplayMode
import com.example.model.BRollItem
import com.example.model.ExportResolution
import com.example.model.StudioProject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

data class VideoExportOutcome(
    val internalFilePath: String,
    val publicUriString: String?,
    val fileSizeBytes: Long
)

@OptIn(UnstableApi::class)
class VideoExporter(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun exportProject(
        project: StudioProject,
        onProgress: (Int, String) -> Unit
    ): VideoExportOutcome {
        val metadata = project.videoMetadata
            ?: throw IllegalStateException("No source video loaded.")

        val outWidth = when (project.resolution) {
            ExportResolution.FHD_1080P -> project.aspectRatio.outWidth1080
            ExportResolution.HD_720P -> project.aspectRatio.outWidth720
        }
        val outHeight = when (project.resolution) {
            ExportResolution.FHD_1080P -> project.aspectRatio.outHeight1080
            ExportResolution.HD_720P -> project.aspectRatio.outHeight720
        }

        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, "kepler_${System.currentTimeMillis()}.mp4")

        val videoEffects = mutableListOf<Effect>()

        // 1. Target Aspect Ratio Presentation (9:16 Reels / 16:9 YouTube / 1:1 Square)
        videoEffects.add(
            Presentation.createForAspectRatio(
                project.aspectRatio.ratioFloat,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        // 2. Ken Burns Auto-Zoom synced to speech
        if (project.burnAutoZoomOnExport) {
            val intensity = project.autoZoomIntensity
            val keyframes = project.zoomKeyframes
            val durationMs = metadata.durationMs
            videoEffects.add(
                MatrixTransformation { presentationTimeUs ->
                    val timeMs = presentationTimeUs / 1000L
                    val kb = KenBurnsMath.computeTransformAt(
                        timeMs = timeMs,
                        durationMs = durationMs,
                        intensity = intensity,
                        keyframes = keyframes
                    )
                    Matrix().apply {
                        setScale(kb.scale, kb.scale)
                        postTranslate(kb.panX * 2f, kb.panY * 2f)
                    }
                }
            )
        }

        // 3. Burned-in AI B-Roll + Sinhala Unicode Subtitles OverlayEffect
        val overlays = mutableListOf<androidx.media3.effect.TextureOverlay>()
        if (project.burnBRollOnExport && project.bRollItems.any { it.enabled && it.imagePath != null }) {
            overlays.add(
                BRollBitmapOverlay(
                    items = project.bRollItems.filter { it.enabled && it.imagePath != null },
                    frameWidth = outWidth,
                    frameHeight = outHeight
                )
            )
        }
        if (project.burnSubtitlesOnExport && project.subtitles.isNotEmpty()) {
            overlays.add(
                SubtitleLayerBitmapOverlay(
                    renderer = SubtitleBitmapRenderer(context),
                    project = project,
                    frameWidth = outWidth,
                    frameHeight = outHeight
                )
            )
        }
        if (overlays.isNotEmpty()) {
            videoEffects.add(OverlayEffect(overlays))
        }

        val mediaItem = MediaItem.fromUri(Uri.parse(metadata.uriString))
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val deferred = CompletableDeferred<ExportResult>()
        val progressHolder = ProgressHolder()
        var polling = true

        withContext(Dispatchers.Main) {
            onProgress(2, "Initializing Media3 GPU pipeline...")
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        polling = false
                        deferred.complete(exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        polling = false
                        deferred.completeExceptionally(exportException)
                    }
                })
                .build()

            activeTransformer = transformer
            transformer.start(editedMediaItem, outputFile.absolutePath)

            val poller = object : Runnable {
                override fun run() {
                    if (!polling) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val pct = progressHolder.progress.coerceIn(1, 99)
                        val phase = when {
                            pct < 35 -> "Encoding Ken Burns & B-Roll..."
                            pct < 80 -> "Burning Sinhala Unicode captions..."
                            else -> "Finalizing MP4 container..."
                        }
                        onProgress(pct, phase)
                    }
                    if (polling && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            }
            mainHandler.postDelayed(poller, 250L)
        }

        deferred.await()
        activeTransformer = null
        onProgress(100, "Saving to Movies/Kepler...")

        val publicUri = saveToMediaStoreMovies(outputFile, project.title)
        return VideoExportOutcome(
            internalFilePath = outputFile.absolutePath,
            publicUriString = publicUri?.toString(),
            fileSizeBytes = outputFile.length()
        )
    }

    fun cancelExport() {
        mainHandler.post {
            runCatching { activeTransformer?.cancel() }
            activeTransformer = null
        }
    }

    private suspend fun saveToMediaStoreMovies(file: File, projectTitle: String): Uri? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
            val safeName = projectTitle
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .ifBlank { "Kepler_Video" }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${safeName}_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Kepler")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (_: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        }

    private class SubtitleLayerBitmapOverlay(
        private val renderer: SubtitleBitmapRenderer,
        private val project: StudioProject,
        private val frameWidth: Int,
        private val frameHeight: Int
    ) : BitmapOverlay() {
        private val emptyBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        private var cachedCueId: String? = null
        private var cachedBitmap: Bitmap = emptyBitmap
        private val defaultSettings = StaticOverlaySettings.Builder().build()

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val timeMs = presentationTimeUs / 1000L
            val cue = project.subtitles.firstOrNull { timeMs in it.startMs..it.endMs }
            if (cue == null) {
                cachedCueId = null
                return emptyBitmap
            }
            if (cue.id != cachedCueId) {
                cachedBitmap = renderer.renderSubtitleOverlayBitmap(
                    cue = cue,
                    style = project.styleConfig,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight
                )
                cachedCueId = cue.id
            }
            return cachedBitmap
        }

        override fun getOverlaySettings(presentationTimeUs: Long): StaticOverlaySettings {
            return defaultSettings
        }
    }

    private class BRollBitmapOverlay(
        private val items: List<BRollItem>,
        private val frameWidth: Int,
        private val frameHeight: Int
    ) : BitmapOverlay() {
        private val emptyBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        private val decodedCache = mutableMapOf<String, Bitmap>()
        private val defaultSettings = StaticOverlaySettings.Builder().build()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = 0xFF576BFF.toInt()
        }

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val timeMs = presentationTimeUs / 1000L
            val activeItem = items.firstOrNull { timeMs in it.startMs..it.endMs } ?: return emptyBitmap
            val path = activeItem.imagePath ?: return emptyBitmap
            val source = decodedCache.getOrPut(activeItem.id) {
                BitmapFactory.decodeFile(path) ?: return emptyBitmap
            }

            val span = max(400L, activeItem.endMs - activeItem.startMs).toFloat()
            val progress = ((timeMs - activeItem.startMs).toFloat() / span).coerceIn(0f, 1f)
            val kenBurnsZoom = 1.0f + 0.08f * progress

            val frame = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)

            when (activeItem.displayMode) {
                BRollDisplayMode.CUTAWAY_FULL -> {
                    drawCenterCropped(
                        canvas = canvas,
                        src = source,
                        destRect = RectF(0f, 0f, frameWidth.toFloat(), frameHeight.toFloat()),
                        zoom = kenBurnsZoom,
                        cornerRadius = 0f
                    )
                }
                BRollDisplayMode.SPLIT_TOP -> {
                    val splitRect = RectF(0f, 0f, frameWidth.toFloat(), frameHeight * 0.48f)
                    drawCenterCropped(
                        canvas = canvas,
                        src = source,
                        destRect = splitRect,
                        zoom = kenBurnsZoom,
                        cornerRadius = 0f
                    )
                    canvas.drawLine(0f, splitRect.bottom, frameWidth.toFloat(), splitRect.bottom, borderPaint)
                }
                BRollDisplayMode.FLOATING_CARD -> {
                    val cardW = frameWidth * 0.72f
                    val cardH = cardW * 0.62f
                    val left = (frameWidth - cardW) * 0.5f
                    val top = frameHeight * 0.14f
                    val cardRect = RectF(left, top, left + cardW, top + cardH)
                    drawCenterCropped(
                        canvas = canvas,
                        src = source,
                        destRect = cardRect,
                        zoom = kenBurnsZoom,
                        cornerRadius = 28f
                    )
                    canvas.drawRoundRect(cardRect, 28f, 28f, borderPaint)
                }
            }
            return frame
        }

        private fun drawCenterCropped(
            canvas: Canvas,
            src: Bitmap,
            destRect: RectF,
            zoom: Float,
            cornerRadius: Float
        ) {
            canvas.save()
            if (cornerRadius > 0f) {
                val clipPath = android.graphics.Path().apply {
                    addRoundRect(destRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(clipPath)
            } else {
                canvas.clipRect(destRect)
            }
            canvas.drawColor(Color.BLACK)

            val scaleX = destRect.width() / src.width.toFloat()
            val scaleY = destRect.height() / src.height.toFloat()
            val scale = max(scaleX, scaleY) * zoom

            val drawW = src.width * scale
            val drawH = src.height * scale
            val dx = destRect.centerX() - drawW * 0.5f
            val dy = destRect.centerY() - drawH * 0.5f

            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(src, matrix, paint)
            canvas.restore()
        }

        override fun getOverlaySettings(presentationTimeUs: Long): StaticOverlaySettings {
            return defaultSettings
        }
    }
}
