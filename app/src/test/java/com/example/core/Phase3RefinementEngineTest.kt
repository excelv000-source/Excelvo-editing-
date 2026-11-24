package com.example.core

import com.example.core.command.AddTrackCommand
import com.example.core.command.ApplyPresetCommand
import com.example.core.command.CommandHistoryManager
import com.example.core.command.DuplicateClipCommand
import com.example.core.command.MoveClipToTrackCommand
import com.example.core.command.RemoveKeyframeCommand
import com.example.core.command.RemoveTrackCommand
import com.example.core.command.UpdateClipAudioEnvelopeCommand
import com.example.core.command.UpdateClipBlendCommand
import com.example.core.command.UpdateClipEffectStackCommand
import com.example.core.command.UpdateClipSpeedCommand
import com.example.core.command.UpdateKeyframeEasingCommand
import com.example.core.command.UpsertKeyframeCommand
import com.example.core.engine.TimelineEvaluator
import com.example.core.model.AnimatableProperty
import com.example.core.model.BlendModeType
import com.example.core.model.ClipEffectInstance
import com.example.core.model.EasingType
import com.example.core.model.Keyframe
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TrackType
import com.example.core.model.TransformSpec
import com.example.core.model.VisualEffectType
import com.example.data.project.ProjectJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3RefinementEngineTest {

    private fun sampleProject(): TimelineProject {
        val base = TimelineProject(
            id = "p1",
            name = "Motion Lab",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val asset = MediaAsset(
            id = "a1",
            sourceUri = "content://test/video.mp4",
            displayName = "video.mp4",
            mediaType = MediaType.VIDEO,
            durationUs = 4_000_000L,
            width = 1080,
            height = 1920,
        )
        val audioAsset = MediaAsset(
            id = "a2",
            sourceUri = "content://test/beat.wav",
            displayName = "beat.wav",
            mediaType = MediaType.AUDIO,
            durationUs = 4_000_000L,
            width = 0,
            height = 0,
        )
        val videoClip = TimelineClip(
            id = "c1",
            assetId = "a1",
            trackId = TimelineProject.PRIMARY_TRACK_ID,
            mediaType = MediaType.VIDEO,
            label = "video.mp4",
            timelineStartUs = 0L,
            timelineDurationUs = 4_000_000L,
            sourceStartUs = 0L,
            sourceEndUs = 4_000_000L,
            sourceTotalDurationUs = 4_000_000L,
        )
        val audioClip = TimelineClip(
            id = "c2",
            assetId = "a2",
            trackId = TimelineProject.AUDIO_TRACK_ID,
            mediaType = MediaType.AUDIO,
            label = "beat.wav",
            timelineStartUs = 0L,
            timelineDurationUs = 4_000_000L,
            sourceStartUs = 0L,
            sourceEndUs = 4_000_000L,
            sourceTotalDurationUs = 4_000_000L,
        )
        return base.copy(
            assets = mapOf("a1" to asset, "a2" to audioAsset),
            tracks = base.tracks.map { tr ->
                when (tr.id) {
                    TimelineProject.PRIMARY_TRACK_ID -> tr.copy(clips = listOf(videoClip))
                    TimelineProject.AUDIO_TRACK_ID -> tr.copy(clips = listOf(audioClip))
                    else -> tr
                }
            },
        )
    }

    private fun TimelineProject.clipById(id: String): TimelineClip? =
        allClips().firstOrNull { it.id == id }

    @Test
    fun `timeline evaluator interpolates easing curves and evaluates audio fade envelopes`() {
        val proj = sampleProject()
        val clip = proj.clipById("c1")!!.copy(
            transform = TransformSpec(positionX = 0f, scaleX = 1f),
            keyframes = mapOf(
                AnimatableProperty.POSITION_X to listOf(
                    Keyframe("k1", 0L, 0f, EasingType.LINEAR),
                    Keyframe("k2", 1_000_000L, 100f, EasingType.EASE_IN),
                    Keyframe("k3", 2_000_000L, 200f, EasingType.HOLD),
                    Keyframe("k4", 3_000_000L, 300f, EasingType.LINEAR),
                ),
            ),
        )

        val mid1 = TimelineEvaluator.evaluateTransformAt(clip, 500_000L)
        assertTrue(mid1.positionX in 5f..95f)

        val atEnd = TimelineEvaluator.evaluateTransformAt(clip, 3_500_000L)
        assertEquals(300f, atEnd.positionX, 0.01f)

        val audio = proj.clipById("c2")!!.copy(
            volume = 1.0f,
            fadeInUs = 1_000_000L,
            fadeOutUs = 1_000_000L,
        )
        assertEquals(0f, TimelineEvaluator.evaluateVolumeAt(audio, false, 0L), 0.01f)
        assertEquals(0.5f, TimelineEvaluator.evaluateVolumeAt(audio, false, 500_000L), 0.01f)
        assertEquals(1.0f, TimelineEvaluator.evaluateVolumeAt(audio, false, 2_000_000L), 0.01f)
        assertEquals(0.5f, TimelineEvaluator.evaluateVolumeAt(audio, false, 3_500_000L), 0.01f)
        assertEquals(0f, TimelineEvaluator.evaluateVolumeAt(audio, true, 2_000_000L), 0.01f)
        assertEquals(0f, TimelineEvaluator.evaluateVolumeAt(audio.copy(isMuted = true), false, 2_000_000L), 0.01f)
    }

    @Test
    fun `boundary jumping navigates cleanly across clip boundaries and keyframes`() {
        var proj = sampleProject()
        proj = DuplicateClipCommand("c1", "c1_copy").apply(proj, 1000L)
        proj = UpsertKeyframeCommand(
            clipId = "c1",
            property = AnimatableProperty.POSITION_X,
            keyframe = Keyframe("kf-mid", 1_500_000L, 45f, EasingType.EASE_OUT),
        ).apply(proj, 1001L)

        assertEquals(1_500_000L, TimelineEvaluator.findNextBoundaryUs(proj, 0L))
        assertEquals(4_000_000L, TimelineEvaluator.findNextBoundaryUs(proj, 1_500_000L))
        assertEquals(8_000_000L, TimelineEvaluator.findNextBoundaryUs(proj, 4_000_000L))

        assertEquals(4_000_000L, TimelineEvaluator.findPreviousBoundaryUs(proj, 8_000_000L))
        assertEquals(1_500_000L, TimelineEvaluator.findPreviousBoundaryUs(proj, 4_000_000L))
        assertEquals(0L, TimelineEvaluator.findPreviousBoundaryUs(proj, 1_500_000L))
    }

    @Test
    fun `all phase 3 refinement commands execute, undo cleanly, and round-trip through ProjectJsonCodec`() {
        var proj = sampleProject()
        val history = CommandHistoryManager(proj)

        fun run(cmd: com.example.core.command.EditCommand, ts: Long) {
            proj = history.execute(cmd, ts).project
        }

        run(AddTrackCommand("tr_v3", TrackType.OVERLAY, "V3 Overlay"), 100L)
        assertNotNull(proj.tracks.find { it.id == "tr_v3" })

        run(DuplicateClipCommand("c1", "c1_dup"), 101L)
        assertNotNull(proj.clipById("c1_dup"))

        run(MoveClipToTrackCommand("c1_dup", "tr_v3"), 102L)
        assertEquals("tr_v3", proj.clipById("c1_dup")?.trackId)

        run(UpdateClipBlendCommand("c1_dup", BlendModeType.SCREEN, 0.65f), 103L)
        run(UpdateClipSpeedCommand("c1_dup", 1.5f), 104L)
        run(UpdateClipAudioEnvelopeCommand("c2", 0.8f, false, 500_000L, 600_000L), 105L)
        run(
            UpdateClipEffectStackCommand(
                clipId = "c1_dup",
                effectStack = listOf(ClipEffectInstance("fx1", VisualEffectType.GLOW, true, 0.72f)),
            ),
            106L,
        )
        run(
            UpsertKeyframeCommand(
                clipId = "c1_dup",
                property = AnimatableProperty.POSITION_Z,
                keyframe = Keyframe("kfz", 500_000L, 42f, EasingType.EASE_IN_OUT),
            ),
            107L,
        )
        run(UpdateKeyframeEasingCommand("c1_dup", AnimatableProperty.POSITION_Z, "kfz", EasingType.EASE_OUT), 108L)

        // JSON codec round-trip preserves all fields cleanly
        val encoded = ProjectJsonCodec.encode(proj)
        val decoded = ProjectJsonCodec.decode(encoded)
        val decodedClip = decoded.clipById("c1_dup")!!
        assertEquals(0.65f, decodedClip.transform.opacity, 0.001f)
        assertEquals(BlendModeType.SCREEN, decodedClip.blendMode)
        assertEquals(1.5f, decodedClip.speedMultiplier, 0.001f)
        assertEquals(1, decodedClip.effectStack.size)
        assertEquals(VisualEffectType.GLOW, decodedClip.effectStack.first().type)
        assertEquals(EasingType.EASE_OUT, decodedClip.keyframes[AnimatableProperty.POSITION_Z]?.first()?.easing)
        val decodedAudio = decoded.clipById("c2")!!
        assertEquals(500_000L, decodedAudio.fadeInUs)
        assertEquals(600_000L, decodedAudio.fadeOutUs)

        // Apply MOTION preset applies keyframes
        val motionPreset = PresetDefinition(
            id = "m_test",
            title = "Test Motion",
            description = "Motion test",
            category = PresetCategory.MOTION,
            creatorName = "Test",
            creatorHandle = "@test",
            motionKeyframes = mapOf(
                AnimatableProperty.SCALE_X to listOf(Keyframe("sx1", 0L, 1f), Keyframe("sx2", 1_000_000L, 1.25f)),
            ),
        )
        run(ApplyPresetCommand(motionPreset, "c1"), 109L)
        assertTrue(proj.clipById("c1")?.keyframes?.containsKey(AnimatableProperty.SCALE_X) == true)

        // Keyframe removal & track removal
        run(RemoveKeyframeCommand("c1_dup", AnimatableProperty.POSITION_Z, "kfz"), 110L)
        assertTrue(proj.clipById("c1_dup")?.keyframes?.get(AnimatableProperty.POSITION_Z).isNullOrEmpty())

        run(RemoveTrackCommand("tr_v3"), 111L)
        assertNull(proj.tracks.find { it.id == "tr_v3" })
        assertNull(proj.clipById("c1_dup"))

        // Undo RemoveTrack via CommandHistoryManager restores track & clip
        proj = history.undo().project
        assertNotNull(proj.tracks.find { it.id == "tr_v3" })
        assertNotNull(proj.clipById("c1_dup"))
    }
}
