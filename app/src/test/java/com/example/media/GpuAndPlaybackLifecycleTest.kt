package com.example.media

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ColorGradeSpec
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineProject
import com.example.core.model.TransformSpec
import com.example.media.gpu.ExcevoGlslShaders
import com.example.media.gpu.ExcevoLiveRenderState
import com.example.media.gpu.ExcevoTransformMatrixEffect
import com.example.media.gpu.GpuCapabilityChecker
import com.example.media.playback.AndroidSequencePlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GpuAndPlaybackLifecycleTest {

    @Test
    fun `glsl shaders declare all phase 3 uniforms in both ES 3 and ES 2 fallback`() {
        val requiredUniforms = listOf(
            "uTexSampler",
            "uTexelSize",
            "uExposure",
            "uBrightness",
            "uContrast",
            "uSaturation",
            "uTemperature",
            "uTint",
            "uHighlights",
            "uShadows",
            "uSharpness",
            "uBlur",
            "uVignette",
            "uGrayscale",
            "uSepia",
        )
        assertTrue(ExcevoGlslShaders.VERTEX_SHADER_ES3.contains("#version 300 es"))
        assertTrue(ExcevoGlslShaders.FRAGMENT_SHADER_ES3.contains("#version 300 es"))
        assertTrue(ExcevoGlslShaders.FRAGMENT_SHADER_ES2.contains("gl_FragColor"))

        requiredUniforms.forEach { uniform ->
            assertTrue("ES 3.0 shader missing $uniform", ExcevoGlslShaders.FRAGMENT_SHADER_ES3.contains(uniform))
            assertTrue("ES 2.0 fallback shader missing $uniform", ExcevoGlslShaders.FRAGMENT_SHADER_ES2.contains(uniform))
        }
    }

    @Test
    fun `gpu capability checker respects user hardware toggle and robolectric safe fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val disabled = GpuCapabilityChecker.inspect(context, hardwareAccelerationRequested = false)
        assertFalse(disabled.useHardwareGpuPipeline)
        assertTrue(disabled.statusLabel.contains("Software Fallback"))

        val autoDetected = GpuCapabilityChecker.inspect(context, hardwareAccelerationRequested = true)
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            assertFalse(autoDetected.useHardwareGpuPipeline)
            assertEquals("Software Fallback (Test Env)", autoDetected.statusLabel)
        }
    }

    @Test
    fun `live render state and transform matrix effect apply scale, fill boost, rotation, and translation`() {
        val renderState = ExcevoLiveRenderState()
        renderState.updateGrade(ColorGradeSpec(exposure = 0.3f, blurRadius = 0.5f))
        assertEquals(0.3f, renderState.uniforms.get().exposure, 0.001f)
        assertEquals(0.5f, renderState.uniforms.get().blur, 0.001f)

        renderState.updateTransform(
            transform = TransformSpec(positionX = 108f, positionY = -54f, scaleX = 1.25f, scaleY = 1.25f, rotationDeg = 45f),
            scaleMode = CanvasScaleMode.FILL,
            targetAspect = 16f / 9f,
            sourceAspect = 9f / 16f,
        )
        val matrixEffect = ExcevoTransformMatrixEffect(renderState)
        val m = matrixEffect.getMatrix(0L)
        assertFalse(m.isIdentity)
    }

    @Test
    fun `playback controller handles sequence sync, seek clamping, frame stepping, host pause-resume, and release`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = AndroidSequencePlaybackController(
            context = context,
            hardwareAccelerationRequested = false,
        )

        val asset = MediaAsset("a1", "content://test/photo.png", "photo.png", MediaType.IMAGE, 3_000_000L, 1080, 1920)
        val project = AddAssetClipCommand(asset, "c1", defaultImageDurationUs = 3_000_000L).apply(
            TimelineProject(
                id = "pb-proj",
                name = "Playback Seq",
                aspectRatio = AspectRatioPreset.RATIO_9_16,
                frameRate = FrameRatePreset.FPS_30,
                createdAtMs = 100L,
                updatedAtMs = 100L,
            ),
            100L,
        )

        controller.syncSequence(project, 500_000L)
        assertEquals(500_000L, controller.status.value.playheadUs)

        // Clamping beyond duration
        controller.seekToTimelineUs(9_999_999L)
        assertEquals(3_000_000L, controller.status.value.playheadUs)

        // Step backward 1 frame at 30fps (33,333us)
        controller.stepFrames(-1)
        assertTrue(controller.status.value.playheadUs < 3_000_000L)

        // Play & host pause/resume lifecycle
        controller.seekToTimelineUs(0L)
        controller.play()
        assertTrue(controller.status.value.isPlaying)

        controller.onHostPause()
        assertFalse(controller.status.value.isPlaying)
        controller.onHostResume()
        controller.clearError()
        assertNull(controller.status.value.playbackError)

        controller.release()
    }
}
