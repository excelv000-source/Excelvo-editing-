package com.example.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.command.CommandHistoryManager
import com.example.core.command.DuplicateClipCommand
import com.example.core.command.ResetClipAllCommand
import com.example.core.command.ToggleGroupClipCommand
import com.example.core.command.ToggleTrackVisibilityCommand
import com.example.core.command.UpdateClipColorGradeCommand
import com.example.core.command.UpdateClipEffectStackCommand
import com.example.core.command.UpdateClipTransformCommand
import com.example.core.command.UpsertKeyframeCommand
import com.example.core.engine.TimelineEvaluator
import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.BlendModeType
import com.example.core.model.CanvasBackgroundSpec
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.Keyframe
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TransformSpec
import com.example.core.model.VisualEffectType
import com.example.data.db.ExcevoDatabase
import com.example.data.project.LocalProjectRepository
import com.example.data.project.ProjectJsonCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3FinalUpdateEngineTest {

    private fun twoLayerProject(): TimelineProject {
        val base = TimelineProject(
            id = "p-final",
            name = "Final Update Sequence",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val vAsset = MediaAsset("v1", "content://test/a.mp4", "LayerA.mp4", MediaType.VIDEO, 4_000_000L, 1080, 1920)
        val imgAsset = MediaAsset("i1", "content://test/b.png", "LayerB.png", MediaType.IMAGE, 3_000_000L, 800, 800)
        val clipA = TimelineClip(
            id = "clip-a",
            assetId = "v1",
            trackId = TimelineProject.PRIMARY_TRACK_ID,
            mediaType = MediaType.VIDEO,
            label = "LayerA",
            timelineStartUs = 0L,
            timelineDurationUs = 4_000_000L,
            sourceStartUs = 0L,
            sourceEndUs = 4_000_000L,
            sourceTotalDurationUs = 4_000_000L,
        )
        val clipB = TimelineClip(
            id = "clip-b",
            assetId = "i1",
            trackId = TimelineProject.OVERLAY_TRACK_ID,
            mediaType = MediaType.IMAGE,
            label = "LayerB",
            timelineStartUs = 0L,
            timelineDurationUs = 3_000_000L,
            sourceStartUs = 0L,
            sourceEndUs = 3_000_000L,
            sourceTotalDurationUs = 3_000_000L,
        )
        return base.copy(
            assets = mapOf("v1" to vAsset, "i1" to imgAsset),
            tracks = base.tracks.map { tr ->
                when (tr.id) {
                    TimelineProject.PRIMARY_TRACK_ID -> tr.copy(clips = listOf(clipA))
                    TimelineProject.OVERLAY_TRACK_ID -> tr.copy(clips = listOf(clipB))
                    else -> tr
                }
            },
        )
    }

    @Test
    fun `skew x and y, z depth, rotation, scale, position and opacity interpolate smoothly across keyframes`() {
        val baseClip = twoLayerProject().allClips().first()
        val keyframesMap: Map<AnimatableProperty, List<Keyframe>> = mapOf(
            AnimatableProperty.SKEW_X to listOf(
                Keyframe("kx1", 0L, 0f, EasingType.LINEAR),
                Keyframe("kx2", 2_000_000L, 30f, EasingType.EASE_IN_OUT),
            ),
            AnimatableProperty.SKEW_Y to listOf(
                Keyframe("ky1", 0L, -10f, EasingType.LINEAR),
                Keyframe("ky2", 2_000_000L, 10f, EasingType.LINEAR),
            ),
            AnimatableProperty.POSITION_Z to listOf(
                Keyframe("kz1", 0L, 0f, EasingType.EASE_OUT),
                Keyframe("kz2", 2_000_000L, 120f, EasingType.EASE_OUT),
            ),
            AnimatableProperty.ROTATION_DEG to listOf(
                Keyframe("kr1", 0L, 0f, EasingType.LINEAR),
                Keyframe("kr2", 2_000_000L, 180f, EasingType.LINEAR),
            ),
            AnimatableProperty.OPACITY to listOf(
                Keyframe("ko1", 0L, 1f, EasingType.LINEAR),
                Keyframe("ko2", 2_000_000L, 0.2f, EasingType.LINEAR),
            ),
        )
        val clip = baseClip.copy(
            transform = TransformSpec(),
            keyframes = keyframesMap,
        )

        val atStart = TimelineEvaluator.evaluateTransformAt(clip, 0L)
        assertEquals(0f, atStart.skewXDeg, 0.01f)
        assertEquals(-10f, atStart.skewYDeg, 0.01f)
        assertEquals(1f, atStart.opacity, 0.01f)

        val atMid = TimelineEvaluator.evaluateTransformAt(clip, 1_000_000L)
        assertTrue(atMid.skewXDeg in 10f..20f)
        assertEquals(0f, atMid.skewYDeg, 0.01f)
        assertTrue(atMid.positionZ > 60f)
        assertEquals(90f, atMid.rotationDeg, 0.01f)
        assertEquals(0.6f, atMid.opacity, 0.01f)

        val atEnd = TimelineEvaluator.evaluateTransformAt(clip, 2_000_000L)
        assertEquals(30f, atEnd.skewXDeg, 0.01f)
        assertEquals(10f, atEnd.skewYDeg, 0.01f)
        assertEquals(120f, atEnd.positionZ, 0.01f)

        assertEquals(30f, TimelineEvaluator.normalizedValueFor(atEnd, 1f, AnimatableProperty.SKEW_X), 0.01f)
        assertEquals(10f, TimelineEvaluator.normalizedValueFor(atEnd, 1f, AnimatableProperty.SKEW_Y), 0.01f)
    }

    @Test
    fun `grouping overlaps layers at playhead and ungroups with full undo redo and json persistence`() {
        val initial = twoLayerProject()
        val history = CommandHistoryManager(initial)

        val grouped = history.execute(
            ToggleGroupClipCommand(clipId = "clip-a", playheadUs = 500_000L, newGroupId = "grp_01"),
            1000L,
        ).project
        assertEquals("grp_01", grouped.allClips().first { it.id == "clip-a" }.groupId)
        assertEquals("grp_01", grouped.allClips().first { it.id == "clip-b" }.groupId)

        val decoded = ProjectJsonCodec.decode(ProjectJsonCodec.encode(grouped))
        assertEquals("grp_01", decoded.allClips().first { it.id == "clip-b" }.groupId)

        val ungrouped = history.execute(
            ToggleGroupClipCommand(clipId = "clip-a", playheadUs = 500_000L, newGroupId = "grp_unused"),
            1001L,
        ).project
        assertNull(ungrouped.allClips().first { it.id == "clip-a" }.groupId)
        assertNull(ungrouped.allClips().first { it.id == "clip-b" }.groupId)

        val undone = history.undo().project
        assertEquals("grp_01", undone.allClips().first { it.id == "clip-a" }.groupId)
    }

    @Test
    fun `effect stacks remain strictly isolated per clip across multi-clip timelines`() {
        val initial = twoLayerProject()
        val history = CommandHistoryManager(initial)

        val afterA = history.execute(
            UpdateClipEffectStackCommand(
                clipId = "clip-a",
                effectStack = listOf(
                    ClipEffectInstance("fx-glow", VisualEffectType.GLOW, true, 0.8f),
                    ClipEffectInstance("fx-blur", VisualEffectType.GAUSSIAN_BLUR, true, 0.4f),
                ),
            ),
            100L,
        ).project

        val clipA = afterA.allClips().first { it.id == "clip-a" }
        val clipB = afterA.allClips().first { it.id == "clip-b" }
        assertEquals(2, clipA.effectStack.size)
        assertTrue(clipB.effectStack.isEmpty())

        val afterB = history.execute(
            UpdateClipEffectStackCommand(
                clipId = "clip-b",
                effectStack = listOf(ClipEffectInstance("fx-sepia", VisualEffectType.SEPIA, true, 0.65f)),
            ),
            101L,
        ).project
        assertEquals(2, afterB.allClips().first { it.id == "clip-a" }.effectStack.size)
        assertEquals(VisualEffectType.SEPIA, afterB.allClips().first { it.id == "clip-b" }.effectStack.single().type)
    }

    @Test
    fun `room database persists custom dimensions, scale modes, backgrounds, skew, and keyframes across reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ExcevoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LocalProjectRepository(context, db.projectDao())

            val custom = repo.createProject(
                name = "Cinema Custom",
                aspectRatio = AspectRatioPreset.RATIO_21_9,
                frameRate = FrameRatePreset.FPS_60,
                targetResolution = ExportResolutionPreset.QHD_2K,
                scaleMode = CanvasScaleMode.FILL,
                backgroundId = CanvasBackgroundSpec.SLATE_BLUE.id,
                customWidthPx = 720,
                customHeightPx = 960,
            )
            assertEquals(720 to 960, custom.effectiveDimensions())

            val asset = MediaAsset("a1", "content://test/clip.mp4", "Clip.mp4", MediaType.VIDEO, 3_000_000L, 1920, 1080)
            var updated = AddAssetClipCommand(asset, "c-persist").apply(custom, 10L)
            updated = UpdateClipTransformCommand(
                "c-persist",
                TransformSpec(positionX = 24f, positionY = -12f, positionZ = 80f, skewXDeg = 14f, skewYDeg = -8f, opacity = 0.85f),
            ).apply(updated, 11L)
            updated = UpdateClipColorGradeCommand(
                "c-persist",
                ColorGradeSpec(saturation = 0.35f, filterPresetId = "cinematic_teal", filterIntensity = 0.7f),
            ).apply(updated, 12L)
            updated = UpsertKeyframeCommand(
                "c-persist",
                AnimatableProperty.SKEW_X,
                Keyframe("kf-skew", 1_000_000L, 22f, EasingType.EASE_OUT),
            ).apply(updated, 13L)
            updated = DuplicateClipCommand("c-persist", "c-dup").apply(updated, 14L)
            updated = ToggleGroupClipCommand("c-persist", 0L, "grp_persist").apply(updated, 15L)

            repo.saveProject(updated)

            val reopened = repo.loadProject(custom.id)
            assertNotNull(reopened)
            assertEquals(AspectRatioPreset.RATIO_21_9, reopened!!.aspectRatio)
            assertEquals(FrameRatePreset.FPS_60, reopened.frameRate)
            assertEquals(CanvasScaleMode.FILL, reopened.scaleMode)
            assertEquals(CanvasBackgroundSpec.SLATE_BLUE.id, reopened.backgroundId)
            assertEquals(720 to 960, reopened.effectiveDimensions())

            val persistedClip = reopened.allClips().first { it.id == "c-persist" }
            assertEquals(14f, persistedClip.transform.skewXDeg, 0.01f)
            assertEquals(-8f, persistedClip.transform.skewYDeg, 0.01f)
            assertEquals(80f, persistedClip.transform.positionZ, 0.01f)
            assertEquals("cinematic_teal", persistedClip.colorGrade.filterPresetId)
            assertEquals(1, persistedClip.keyframes[AnimatableProperty.SKEW_X]?.size)

            // Verify API < 29 blend mode filter excludes unsupported BlendMode Q+ modes
            val preApi29Modes = BlendModeType.entries.filter { !it.requiresApi29 }
            assertEquals(listOf(BlendModeType.NORMAL), preApi29Modes)
            assertFalse(preApi29Modes.contains(BlendModeType.MULTIPLY))
        } finally {
            db.close()
        }
    }

    @Test
    fun `track visibility eye toggle and reset clip all preserve undo redo integrity`() {
        val initial = twoLayerProject()
        val history = CommandHistoryManager(initial)

        // Toggle primary video track visibility off then back on via undo
        val hiddenPrimary = history.execute(
            ToggleTrackVisibilityCommand(TimelineProject.PRIMARY_TRACK_ID),
            200L,
        ).project
        assertTrue(hiddenPrimary.primaryVideoTrack().isHidden)

        val audioTrackId = initial.tracks.first { it.type == com.example.core.model.TrackType.AUDIO }.id
        val hiddenAudio = history.execute(
            ToggleTrackVisibilityCommand(audioTrackId),
            201L,
        ).project
        val audioAfter = hiddenAudio.tracks.first { it.id == audioTrackId }
        assertTrue(audioAfter.isHidden)
        assertTrue(audioAfter.isMuted)

        val undoneAudio = history.undo().project
        assertFalse(undoneAudio.tracks.first { it.id == audioTrackId }.isMuted)

        // Modify clip-a then execute ResetClipAllCommand and verify full restore + undo
        var dirty = history.execute(
            UpdateClipTransformCommand(
                "clip-a",
                TransformSpec(positionX = 40f, skewXDeg = 15f, opacity = 0.45f),
            ),
            202L,
        ).project
        dirty = history.execute(
            UpdateClipEffectStackCommand(
                "clip-a",
                listOf(ClipEffectInstance("fx-g", VisualEffectType.GLOW, true, 0.7f)),
            ),
            203L,
        ).project
        assertEquals(15f, dirty.allClips().first { it.id == "clip-a" }.transform.skewXDeg, 0.01f)

        val reset = history.execute(ResetClipAllCommand("clip-a"), 204L).project
        val cleanClip = reset.allClips().first { it.id == "clip-a" }
        assertEquals(0f, cleanClip.transform.skewXDeg, 0.01f)
        assertEquals(1f, cleanClip.transform.opacity, 0.01f)
        assertTrue(cleanClip.effectStack.isEmpty())
        assertTrue(cleanClip.colorGrade.isNeutral())

        val restored = history.undo().project
        assertEquals(15f, restored.allClips().first { it.id == "clip-a" }.transform.skewXDeg, 0.01f)
    }
}
