package com.example.core

import com.example.core.command.AddAssetClipCommand
import com.example.core.command.ApplyPresetCommand
import com.example.core.command.CommandHistoryManager
import com.example.core.command.UpdateClipColorGradeCommand
import com.example.core.command.UpdateClipTransformCommand
import com.example.core.command.UpdateProjectSettingsCommand
import com.example.core.engine.ColorFilterCatalog
import com.example.core.engine.EffectGradeMath
import com.example.core.engine.GpuEffectUniforms
import com.example.core.model.AspectRatioPreset
import com.example.core.model.CanvasBackgroundSpec
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ColorGradeSpec
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.TimelineProject
import com.example.core.model.TransformSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectGradeMathTest {

    @Test
    fun `neutral grade yields identity uniforms and identity 4x5 color matrix`() {
        val neutral = ColorGradeSpec()
        assertTrue(neutral.isNeutral())

        val uniforms = EffectGradeMath.computeUniforms(neutral)
        assertTrue(uniforms.isIdentity())

        val m = EffectGradeMath.buildColorMatrix20(uniforms)
        assertEquals(20, m.size)
        // Diagonal RGB + Alpha = 1f, offsets = 0f
        assertEquals(1f, m[0], 0.001f)
        assertEquals(0f, m[1], 0.001f)
        assertEquals(1f, m[6], 0.001f)
        assertEquals(1f, m[12], 0.001f)
        assertEquals(1f, m[18], 0.001f)
        assertEquals(0f, m[4], 0.001f)
    }

    @Test
    fun `color filters combine with manual adjustments and clamp safely within uniform bounds`() {
        assertEquals(7, ColorFilterCatalog.filters.size)

        val grade = ColorGradeSpec(
            exposure = 0.25f,
            brightness = 0.15f,
            contrast = 0.30f,
            saturation = 0.20f,
            temperature = -0.10f,
            tint = 0.10f,
            highlights = 0.20f,
            shadows = -0.15f,
            sharpness = 0.40f,
            blurRadius = 0.25f,
            vignette = 0.50f,
            grayscale = 0.10f,
            sepia = 0.20f,
            filterPresetId = "violet_dusk",
            filterIntensity = 0.5f,
        )
        assertFalse(grade.isNeutral())

        val u = EffectGradeMath.computeUniforms(grade)
        val violet = ColorFilterCatalog.findById("violet_dusk").gradeDelta

        assertEquals(0.30f + violet.contrast * 0.5f, u.contrast, 0.001f)
        assertEquals(0.10f + violet.tint * 0.5f, u.tint, 0.001f)
        assertEquals(0.50f + violet.vignette * 0.5f, u.vignette, 0.001f)
        assertEquals(0.25f, u.blur, 0.001f)
        assertEquals(0.40f, u.sharpness, 0.001f)
        assertEquals(0.10f, u.grayscale, 0.001f)
        assertEquals(0.20f, u.sepia, 0.001f)
    }

    @Test
    fun `grayscale matrix equalizes RGB channel weights to Rec709 luminance`() {
        val m = EffectGradeMath.buildColorMatrix20(GpuEffectUniforms(grayscale = 1.0f))
        assertEquals(0.2126f, m[0], 0.001f)
        assertEquals(0.7152f, m[1], 0.001f)
        assertEquals(0.0722f, m[2], 0.001f)
        // Green and Blue rows match Red row for true monochrome luminance
        assertEquals(m[0], m[5], 0.001f)
        assertEquals(m[1], m[6], 0.001f)
        assertEquals(m[2], m[7], 0.001f)
        assertEquals(m[0], m[10], 0.001f)
    }

    @Test
    fun `scalePresetGrade scales every parameter proportionally to intensity`() {
        val base = ColorGradeSpec(
            exposure = 0.4f,
            brightness = -0.2f,
            contrast = 0.5f,
            saturation = 0.6f,
            temperature = 0.2f,
            tint = -0.4f,
            highlights = 0.3f,
            shadows = -0.3f,
            sharpness = 0.8f,
            blurRadius = 0.4f,
            vignette = 0.5f,
            grayscale = 1.0f,
            sepia = 0.6f,
            filterPresetId = "cinema_teal",
            filterIntensity = 1.0f,
        )
        val half = EffectGradeMath.scalePresetGrade(base, 0.5f)
        assertEquals(0.2f, half.exposure, 0.001f)
        assertEquals(-0.1f, half.brightness, 0.001f)
        assertEquals(0.25f, half.contrast, 0.001f)
        assertEquals(0.4f, half.sharpness, 0.001f)
        assertEquals(0.2f, half.blurRadius, 0.001f)
        assertEquals(0.5f, half.grayscale, 0.001f)
        assertEquals(0.3f, half.sepia, 0.001f)
        assertEquals("cinema_teal", half.filterPresetId)
        assertEquals(0.5f, half.filterIntensity, 0.001f)
    }

    @Test
    fun `edit commands update color grade, transform, project canvas settings, and preset intensity with undo-redo`() {
        val baseProject = TimelineProject(
            id = "p1",
            name = "Phase 3 Test",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_30,
            createdAtMs = 100L,
            updatedAtMs = 100L,
        )
        val asset = MediaAsset("a1", "content://test/v.mp4", "v.mp4", MediaType.VIDEO, 3_000_000L, 1080, 1920)
        val withClip = AddAssetClipCommand(asset, "clip1").apply(baseProject, 200L)
        val history = CommandHistoryManager(withClip)

        val newGrade = ColorGradeSpec(brightness = 0.25f, blurRadius = 0.4f, filterPresetId = "arctic_crisp")
        val afterGrade = history.execute(UpdateClipColorGradeCommand("clip1", newGrade), 300L)
        assertEquals(0.25f, afterGrade.project.primaryVideoTrack().clips.first().colorGrade.brightness, 0.001f)
        assertEquals("arctic_crisp", afterGrade.project.primaryVideoTrack().clips.first().colorGrade.filterPresetId)

        val newTransform = TransformSpec(positionX = 40f, positionY = -20f, scaleX = 1.4f, scaleY = 1.4f, rotationDeg = 90f, opacity = 0.85f)
        val afterTransform = history.execute(UpdateClipTransformCommand("clip1", newTransform), 400L)
        val transformed = afterTransform.project.primaryVideoTrack().clips.first().transform
        assertEquals(90f, transformed.rotationDeg, 0.001f)
        assertEquals(1.4f, transformed.scaleX, 0.001f)
        assertEquals(0.85f, transformed.opacity, 0.001f)

        val afterSettings = history.execute(
            UpdateProjectSettingsCommand(
                name = "Cinema Cut",
                aspectRatio = AspectRatioPreset.RATIO_21_9,
                frameRate = FrameRatePreset.FPS_60,
                targetResolution = ExportResolutionPreset.QHD_2K,
                scaleMode = CanvasScaleMode.FILL,
                backgroundId = CanvasBackgroundSpec.MIDNIGHT_VIOLET.id,
                customWidthPx = 2560,
                customHeightPx = 1080,
                keepCustomResolution = true,
            ),
            500L,
        )
        assertEquals(CanvasScaleMode.FILL, afterSettings.project.scaleMode)
        assertEquals(CanvasBackgroundSpec.MIDNIGHT_VIOLET, afterSettings.project.effectiveBackground())
        assertEquals(2560 to 1080, afterSettings.project.effectiveDimensions())

        val preset = PresetDefinition(
            id = "cc_test",
            title = "Amber Pop",
            description = "Test CC",
            category = PresetCategory.CC,
            creatorName = "EXCEVO",
            creatorHandle = "@excevo",
            colorGrade = ColorGradeSpec(contrast = 0.4f, saturation = 0.2f),
        )
        val afterPreset = history.execute(ApplyPresetCommand(preset, targetClipId = "clip1", intensity = 0.5f), 600L)
        assertEquals(0.2f, afterPreset.project.primaryVideoTrack().clips.first().colorGrade.contrast, 0.001f)

        val undone = history.undo()
        assertEquals(0.25f, undone.project.primaryVideoTrack().clips.first().colorGrade.brightness, 0.001f)
        val redone = history.redo()
        assertEquals(0.2f, redone.project.primaryVideoTrack().clips.first().colorGrade.contrast, 0.001f)
    }
}
