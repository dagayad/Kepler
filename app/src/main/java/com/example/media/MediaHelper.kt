package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

data class ExtractedMediaPayload(
    val bytes: ByteArray,
    val mimeType: String
)

object MediaHelper {
    private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // 18 MB safe ceiling under 20 MB limit

    suspend fun extractVideoMetadata(context: Context, uri: Uri): VideoMetadata =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            var width = 1080
            var height = 1920
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 1080
                val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 1920
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    width = rawH
                    height = rawW
                } else {
                    width = rawW
                    height = rawH
                }
            } finally {
                runCatching { retriever.release() }
            }

            var displayName = "video_clip.mp4"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            VideoMetadata(
                uriString = uri.toString(),
                displayName = displayName,
                durationMs = max(durationMs, 1000L),
                width = width,
                height = height,
                fileSizeBytes = fileSize
            )
        }

    suspend fun extractAudioForAi(context: Context, uri: Uri): ExtractedMediaPayload =
        withContext(Dispatchers.IO) {
            val tempAudio = File(context.cacheDir, "kepler_audio_${System.currentTimeMillis()}.m4a")
            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            var hasAudioTrack = false

            try {
                extractor.setDataSource(context, uri, null)
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }

                if (audioTrackIndex >= 0 && audioFormat != null) {
                    hasAudioTrack = true
                    extractor.selectTrack(audioTrackIndex)
                    muxer = MediaMuxer(tempAudio.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    val muxerTrackIndex = muxer.addTrack(audioFormat)
                    muxer.start()

                    val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        max(audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 256 * 1024)
                    } else {
                        256 * 1024
                    }
                    val buffer = ByteBuffer.allocate(maxBufferSize)
                    val bufferInfo = android.media.MediaCodec.BufferInfo()

                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) {
                            break
                        }
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    muxer.stop()
                    muxer.release()
                    muxer = null
                }
            } catch (_: Exception) {
                hasAudioTrack = false
            } finally {
                runCatching { muxer?.release() }
                runCatching { extractor.release() }
            }

            if (hasAudioTrack && tempAudio.exists() && tempAudio.length() > 0L) {
                if (tempAudio.length() <= MAX_INLINE_BYTES) {
                    val bytes = tempAudio.readBytes()
                    tempAudio.delete()
                    return@withContext ExtractedMediaPayload(bytes = bytes, mimeType = "audio/mp4")
                } else {
                    tempAudio.delete()
                    throw IllegalStateException(
                        "Extracted audio exceeds 18 MB inline limit. Please trim or use a shorter clip."
                    )
                }
            }
            tempAudio.delete()

            // Fallback: send raw video if small enough
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val data = stream.readBytes()
                if (data.size > MAX_INLINE_BYTES) {
                    throw IllegalStateException(
                        "Video has no extractable AAC audio track and exceeds 18 MB inline limit."
                    )
                }
                data
            } ?: throw IllegalStateException("Unable to read selected video file.")

            ExtractedMediaPayload(bytes = rawBytes, mimeType = "video/mp4")
        }

    suspend fun extractWaveformPeaks(
        context: Context,
        uri: Uri,
        bucketCount: Int = 80
    ): List<Float> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else 0L
                    break
                }
            }
            if (audioTrack < 0) {
                return@withContext List(bucketCount) { 0.14f }
            }
            extractor.selectTrack(audioTrack)
            val buckets = DoubleArray(bucketCount)
            val counts = IntArray(bucketCount)
            val buffer = ByteBuffer.allocate(64 * 1024)
            var maxVal = 1.0
            val safeDuration = max(durationUs, 1_000_000L)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val timeUs = extractor.sampleTime.coerceAtLeast(0L)
                val idx = ((timeUs.toDouble() / safeDuration) * bucketCount)
                    .toInt()
                    .coerceIn(0, bucketCount - 1)
                buckets[idx] += sampleSize.toDouble()
                counts[idx]++
                extractor.advance()
            }

            val averages = DoubleArray(bucketCount) { i ->
                if (counts[i] > 0) buckets[i] / counts[i] else 0.0
            }
            maxVal = max(averages.maxOrNull() ?: 1.0, 1.0)
            averages.map { avg ->
                min(1.0f, max(0.14f, (avg / maxVal).toFloat()))
            }
        } catch (_: Exception) {
            List(bucketCount) { 0.18f }
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun createVideoCaptureUri(context: Context): Uri {
        val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(capturesDir, "kepler_capture_${System.currentTimeMillis()}.mp4")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun saveBitmapToBRollFile(context: Context, bitmap: Bitmap, itemId: String): String {
        val dir = File(context.filesDir, "broll").apply { mkdirs() }
        val file = File(dir, "broll_${itemId}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    fun formatTimecode(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val tenths = ((ms % 1000L) / 100L).coerceIn(0L, 9L)
        return "%02d:%02d.%d".format(minutes, seconds, tenths)
    }
}
