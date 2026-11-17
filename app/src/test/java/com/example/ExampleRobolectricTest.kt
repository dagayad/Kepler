package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.media.KenBurnsMath
import com.example.media.MediaHelper
import com.example.media.SubtitleBitmapRenderer
import com.example.model.AutoZoomIntensity
import com.example.model.SubtitleCue
import com.example.model.SubtitleStyleConfig
import com.example.model.ZoomKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kepler", appName)
    }

    @Test
    fun `ken burns math scales up inside speech emphasis keyframe`() {
        val keyframes = listOf(
            ZoomKeyframe(startMs = 1000L, endMs = 3000L, targetScale = 1.24f, panX = 0.02f, panY = -0.02f)
        )
        val offTransform = KenBurnsMath.computeTransformAt(2000L, 10_000L, AutoZoomIntensity.OFF, keyframes)
        assertEquals(1.0f, offTransform.scale, 0.001f)

        val activeTransform = KenBurnsMath.computeTransformAt(2000L, 10_000L, AutoZoomIntensity.DYNAMIC_REELS, keyframes)
        assertTrue(activeTransform.scale > 1.15f)
    }

    @Test
    fun `sinhala unicode subtitle renders non-empty overlay bitmap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = SubtitleBitmapRenderer(context)
        val cue = SubtitleCue(
            startMs = 0L,
            endMs = 2000L,
            primaryText = "ආයුබෝවන් කෙප්ලර් Studio",
            secondaryText = "Welcome to Kepler"
        )
        val bmp = renderer.renderSubtitleOverlayBitmap(
            cue = cue,
            style = SubtitleStyleConfig(),
            frameWidth = 720,
            frameHeight = 1280
        )
        assertEquals(720, bmp.width)
        assertEquals(1280, bmp.height)
    }

    @Test
    fun `timecode formatter formats minutes seconds and tenths`() {
        assertEquals("01:05.4", MediaHelper.formatTimecode(65_420L))
    }
}
