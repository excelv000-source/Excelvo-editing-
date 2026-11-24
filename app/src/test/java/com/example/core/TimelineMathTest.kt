package com.example.core

import com.example.core.engine.KeyframeInterpolator
import com.example.core.engine.TimelineMath
import com.example.core.model.EasingType
import com.example.core.model.Keyframe
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {

    @Test
    fun `formatTimecode formats sub-hour and multi-hour timestamps accurately`() {
        assertEquals("00:00:00", TimelineMath.formatTimecode(0L, 30))
        assertEquals("00:01:15", TimelineMath.formatTimecode(1_500_000L, 30))
        assertEquals("01:02:03:12", TimelineMath.formatTimecode(3_723_500_000L, 24))
    }

    @Test
    fun `stepFrameUs advances and clamps within sequence duration`() {
        val step1 = TimelineMath.stepFrameUs(0L, 1, fps = 30, maxDurationUs = 2_000_000L)
        assertEquals(33_334L, step1)
        assertEquals("00:00:01", TimelineMath.formatTimecode(step1, 30))

        val clampedBack = TimelineMath.stepFrameUs(10_000L, -5, fps = 30, maxDurationUs = 2_000_000L)
        assertEquals(0L, clampedBack)

        val clampedForward = TimelineMath.stepFrameUs(1_990_000L, 5, fps = 30, maxDurationUs = 2_000_000L)
        assertEquals(2_000_000L, clampedForward)
    }

    @Test
    fun `usToPx and pxToUs invert accurately`() {
        val px = TimelineMath.usToPx(2_500_000L, pxPerSecond = 80f)
        assertEquals(200f, px, 0.01f)
        val us = TimelineMath.pxToUs(200f, pxPerSecond = 80f)
        assertEquals(2_500_000L, us)
    }

    @Test
    fun `findActiveClipAt and recalculateSequentialStarts work deterministically`() {
        val c1 = sampleClip("c1", startUs = 500_000L, durationUs = 2_000_000L)
        val c2 = sampleClip("c2", startUs = 9_000_000L, durationUs = 1_500_000L)
        val normalized = TimelineMath.recalculateSequentialStarts(listOf(c1, c2))

        assertEquals(0L, normalized[0].timelineStartUs)
        assertEquals(2_000_000L, normalized[1].timelineStartUs)

        val track = TimelineTrack("t1", TrackType.VIDEO_PRIMARY, "Main", clips = normalized)
        assertEquals("c1", TimelineMath.findActiveClipAt(track, 0L)?.id)
        assertEquals("c2", TimelineMath.findActiveClipAt(track, 2_000_000L)?.id)
        assertEquals("c2", TimelineMath.findActiveClipAt(track, 3_500_000L)?.id)
        assertNull(TimelineMath.findActiveClipAt(track, 4_000_000L))
    }

    @Test
    fun `keyframe interpolator evaluates linear, hold, and ease-in-out curves`() {
        val linear = listOf(
            Keyframe("k1", 0L, 0f, EasingType.LINEAR),
            Keyframe("k2", 1_000_000L, 100f, EasingType.LINEAR),
        )
        assertEquals(0f, KeyframeInterpolator.evaluate(linear, -50_000L, -1f), 0.01f)
        assertEquals(50f, KeyframeInterpolator.evaluate(linear, 500_000L, -1f), 0.01f)
        assertEquals(100f, KeyframeInterpolator.evaluate(linear, 2_000_000L, -1f), 0.01f)

        val hold = listOf(
            Keyframe("h1", 0L, 10f, EasingType.HOLD),
            Keyframe("h2", 1_000_000L, 90f, EasingType.LINEAR),
        )
        assertEquals(10f, KeyframeInterpolator.evaluate(hold, 999_999L, 0f), 0.01f)
    }

    private fun sampleClip(id: String, startUs: Long, durationUs: Long) = TimelineClip(
        id = id,
        assetId = "a_$id",
        trackId = "t1",
        mediaType = MediaType.VIDEO,
        label = id,
        timelineStartUs = startUs,
        timelineDurationUs = durationUs,
        sourceStartUs = 0L,
        sourceEndUs = durationUs,
        sourceTotalDurationUs = durationUs,
    )
}
