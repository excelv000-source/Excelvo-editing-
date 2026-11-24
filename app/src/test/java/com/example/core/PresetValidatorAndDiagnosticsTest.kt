package com.example.core

import com.example.core.command.AddAssetClipCommand
import com.example.core.command.ApplyPresetCommand
import com.example.core.command.CommandHistoryManager
import com.example.core.engine.PresetSchemaValidator
import com.example.core.engine.PresetValidationResult
import com.example.core.engine.SequenceDiagnosticsEngine
import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ColorGradeSpec
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.TimelineProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetValidatorAndDiagnosticsTest {

    @Test
    fun `preset validator blocks dangerous extensions and payloads`() {
        assertNotNull(PresetSchemaValidator.validateFileName("malware.dex"))
        assertNotNull(PresetSchemaValidator.validateFileName("native.so"))
        assertNotNull(PresetSchemaValidator.validateFileName("script.js"))
        assertNotNull(PresetSchemaValidator.validateFileName("page.html"))
        assertNull(PresetSchemaValidator.validateFileName("violet_grade.json"))
        assertNull(PresetSchemaValidator.validateFileName("impact.excevo"))

        val scriptResult = PresetSchemaValidator.validateRawJsonText(
            """{"presetFileFormat":"excevo-preset/v1","title":"Bad","note":"<script>alert(1)</script>"}"""
        )
        assertTrue(scriptResult is PresetValidationResult.Invalid)

        val dclResult = PresetSchemaValidator.validateRawJsonText(
            """{"presetFileFormat":"excevo-preset/v1","title":"Bad","loader":"DexClassLoader"}"""
        )
        assertTrue(dclResult is PresetValidationResult.Invalid)

        val hugePayload = "{" + "\"pad\":\"" + "a".repeat(600_000) + "\"}"
        assertTrue(PresetSchemaValidator.validateRawJsonText(hugePayload) is PresetValidationResult.Invalid)
    }

    @Test
    fun `preset validator parses valid json and clamps parameters`() {
        val validJson = """
            {
              "presetFileFormat": "excevo-preset/v1",
              "title": "Violet Editorial",
              "category": "CC",
              "description": "Balanced dark studio look",
              "creatorName": "Ari Vance",
              "exposure": 9.0,
              "contrast": 0.35,
              "tint": -0.25,
              "vignette": 2.0
            }
        """.trimIndent()

        val result = PresetSchemaValidator.validateRawJsonText(validJson)
        assertTrue(result is PresetValidationResult.Valid)
        val preset = (result as PresetValidationResult.Valid).preset
        assertEquals(PresetCategory.CC, preset.category)
        assertEquals(2f, preset.colorGrade.exposure, 0.001f)
        assertEquals(0.35f, preset.colorGrade.contrast, 0.001f)
        assertEquals(1f, preset.colorGrade.vignette, 0.001f)
        assertEquals("@ari_vance", preset.creatorHandle)
    }

    @Test
    fun `apply preset command applies CC and Shake keyframes with undo support`() {
        val base = TimelineProject(
            id = "p1",
            name = "Test",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_30,
            createdAtMs = 1000L,
            updatedAtMs = 1000L,
        )
        val asset = MediaAsset("a1", "content://test/1.mp4", "clip1.mp4", MediaType.VIDEO, 3_000_000L, 1080, 1920)
        val history = CommandHistoryManager(base)
        history.execute(AddAssetClipCommand(asset, "c1"), 1100L)

        val ccPreset = PresetDefinition(
            id = "cc1",
            title = "Violet Noir",
            description = "Moody grade",
            category = PresetCategory.CC,
            creatorName = "EXCEVO",
            creatorHandle = "@excevo",
            colorGrade = ColorGradeSpec(contrast = 0.28f, tint = 0.22f),
        )
        val afterCc = history.execute(ApplyPresetCommand(ccPreset), 1200L)
        assertEquals(0.28f, afterCc.project.primaryVideoTrack().clips.first().colorGrade.contrast, 0.001f)

        val shakePreset = PresetDefinition(
            id = "sh1",
            title = "Impact Shake",
            description = "Punchy beat",
            category = PresetCategory.SHAKES,
            creatorName = "EXCEVO",
            creatorHandle = "@excevo",
            shakeAmplitude = 24f,
            shakeFrequencyHz = 12f,
        )
        val afterShake = history.execute(ApplyPresetCommand(shakePreset), 1300L)
        val keyframes = afterShake.project.primaryVideoTrack().clips.first().keyframes
        assertTrue((keyframes[AnimatableProperty.POSITION_X]?.size ?: 0) >= 5)
        assertTrue((keyframes[AnimatableProperty.POSITION_Y]?.size ?: 0) >= 5)

        val undone = history.undo()
        assertTrue(undone.project.primaryVideoTrack().clips.first().keyframes.isEmpty())
    }

    @Test
    fun `sequence diagnostics flags aspect mismatch and ungraded clips`() {
        val base = TimelineProject(
            id = "p2",
            name = "Mismatch",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_30,
            createdAtMs = 1000L,
            updatedAtMs = 1000L,
        )
        val landscapeAsset = MediaAsset("a1", "content://test/wide.mp4", "wide.mp4", MediaType.VIDEO, 2_000_000L, 1920, 1080)
        val withClip = AddAssetClipCommand(landscapeAsset, "c1").apply(base, 1100L)

        val report = SequenceDiagnosticsEngine.analyze(withClip)
        assertEquals(1, report.clipCount)
        assertTrue(report.pacingLabel.contains("Balanced"))
        assertTrue(report.recommendations.any { it.contains("neutral color grade") })
        assertTrue(report.recommendations.any { it.contains("differ from 9:16 orientation") })
    }
}
