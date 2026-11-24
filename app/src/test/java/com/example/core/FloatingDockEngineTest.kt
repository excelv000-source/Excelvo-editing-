package com.example.core

import com.example.core.engine.DockScrollConfig
import com.example.core.engine.FloatingDockStateMachine
import com.example.core.engine.PresetSchemaValidator
import com.example.core.engine.PresetValidationResult
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDockEngineTest {

    @Test
    fun `initial state shows dock cleanly`() {
        val machine = FloatingDockStateMachine()
        assertTrue(machine.state.isVisible)
        assertTrue(machine.state.isAtTop)
        assertTrue(machine.state.shouldRenderDock)
    }

    @Test
    fun `micro upward jitter below threshold keeps dock visible`() {
        val machine = FloatingDockStateMachine(DockScrollConfig(hideDeltaThresholdPx = 50f))
        machine.onAtTopChanged(false, nowMs = 100L)
        machine.onScrollDelta(deltaY = 20f, nowMs = 120L)
        machine.onScrollDelta(deltaY = 18f, nowMs = 140L)
        assertTrue(machine.state.isVisible)
    }

    @Test
    fun `upward scroll above threshold when away from top hides dock`() {
        val machine = FloatingDockStateMachine(DockScrollConfig(hideDeltaThresholdPx = 40f, toggleDebounceMs = 50L))
        machine.onAtTopChanged(false, nowMs = 100L)
        machine.onScrollDelta(deltaY = 45f, nowMs = 200L)
        assertFalse(machine.state.isVisible)
        assertFalse(machine.state.shouldRenderDock)
    }

    @Test
    fun `scrolling back down shows dock immediately after threshold`() {
        val machine = FloatingDockStateMachine(
            DockScrollConfig(hideDeltaThresholdPx = 40f, showDeltaThresholdPx = 25f, toggleDebounceMs = 50L)
        )
        machine.onAtTopChanged(false, nowMs = 100L)
        machine.onScrollDelta(deltaY = 50f, nowMs = 200L)
        assertFalse(machine.state.isVisible)

        machine.onScrollDelta(deltaY = -30f, nowMs = 300L)
        assertTrue(machine.state.isVisible)
    }

    @Test
    fun `returning to top forces dock visible regardless of delta`() {
        val machine = FloatingDockStateMachine(DockScrollConfig(hideDeltaThresholdPx = 30f, toggleDebounceMs = 50L))
        machine.onAtTopChanged(false, nowMs = 100L)
        machine.onScrollDelta(deltaY = 40f, nowMs = 200L)
        assertFalse(machine.state.isVisible)

        machine.onAtTopChanged(true, nowMs = 220L)
        assertTrue(machine.state.isVisible)
    }

    @Test
    fun `debounce blocks rapid hide-show flicker inside toggle window`() {
        val machine = FloatingDockStateMachine(
            DockScrollConfig(hideDeltaThresholdPx = 30f, showDeltaThresholdPx = 20f, toggleDebounceMs = 100L)
        )
        machine.onAtTopChanged(false, nowMs = 100L)
        machine.onScrollDelta(deltaY = 35f, nowMs = 200L)
        assertFalse(machine.state.isVisible)

        // Attempt immediate reverse inside 100ms debounce window
        machine.onScrollDelta(deltaY = -40f, nowMs = 240L)
        assertFalse(machine.state.isVisible)

        // Outside debounce window
        machine.onScrollDelta(deltaY = -25f, nowMs = 320L)
        assertTrue(machine.state.isVisible)
    }

    @Test
    fun `modal or keyboard hides dock while preserving underlying visibility`() {
        val machine = FloatingDockStateMachine()
        machine.onModalActiveChanged(true)
        assertFalse(machine.state.shouldRenderDock)

        machine.onModalActiveChanged(false)
        assertTrue(machine.state.shouldRenderDock)

        machine.onKeyboardOpenChanged(true)
        assertFalse(machine.state.shouldRenderDock)

        machine.onKeyboardOpenChanged(false)
        assertTrue(machine.state.shouldRenderDock)
    }

    @Test
    fun `tab change resets dock to visible at top`() {
        val machine = FloatingDockStateMachine(DockScrollConfig(hideDeltaThresholdPx = 30f, toggleDebounceMs = 50L))
        machine.onAtTopChanged(false, nowMs = 100L)
        machine.onScrollDelta(deltaY = 40f, nowMs = 200L)
        assertFalse(machine.state.isVisible)

        machine.onTabChanged(nowMs = 250L)
        assertTrue(machine.state.isVisible)
        assertTrue(machine.state.isAtTop)
    }

    @Test
    fun `aspect ratios and export resolution presets compute exact dimensions`() {
        assertEquals(1920 to 1080, ExportResolutionPreset.FHD_1080P.dimensionsFor(AspectRatioPreset.RATIO_16_9))
        assertEquals(1080 to 1920, ExportResolutionPreset.FHD_1080P.dimensionsFor(AspectRatioPreset.RATIO_9_16))
        assertEquals(1080 to 1080, ExportResolutionPreset.FHD_1080P.dimensionsFor(AspectRatioPreset.RATIO_1_1))
        assertEquals(1080 to 1350, ExportResolutionPreset.FHD_1080P.dimensionsFor(AspectRatioPreset.RATIO_4_5))
        assertEquals(1440 to 1080, ExportResolutionPreset.FHD_1080P.dimensionsFor(AspectRatioPreset.RATIO_4_3))
        assertEquals(1080 to 1440, ExportResolutionPreset.FHD_1080P.dimensionsFor(AspectRatioPreset.RATIO_3_4))
        assertEquals(3840 to 2160, ExportResolutionPreset.UHD_4K.dimensionsFor(AspectRatioPreset.RATIO_16_9))
    }

    @Test
    fun `preset schema validator roundtrips 1 frames and transitions JSON safely`() {
        val sample = PresetDefinition(
            id = "test_1f",
            title = "Beat Flash 1F",
            description = "Sharp 1 frame strobe",
            category = PresetCategory.ONE_FRAME,
            creatorName = "Test Studio",
            creatorHandle = "@test",
            oneFrameStyle = "StrobeFlash",
            effectIntensity = 0.8f,
        )
        val exported = PresetSchemaValidator.toExcevoJson(sample)
        val validated = PresetSchemaValidator.validateRawJsonText(exported)
        assertTrue(validated is PresetValidationResult.Valid)
        val parsed = (validated as PresetValidationResult.Valid).preset
        assertEquals(PresetCategory.ONE_FRAME, parsed.category)
        assertEquals("StrobeFlash", parsed.oneFrameStyle)
    }
}
