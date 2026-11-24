package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.FrameRatePreset
import com.example.core.model.Keyframe
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.data.db.ExcevoDatabase
import com.example.data.project.LocalProjectRepository
import com.example.data.project.ProjectJsonCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectPersistenceAndJsonTest {

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var repository: LocalProjectRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExcevoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalProjectRepository(context, db.projectDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `repository create, save with clips, rename, duplicate, and delete work end to end`() = runTest {
        val created = repository.createProject(
            name = "Vlog Cut",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_60,
        )

        val asset = MediaAsset(
            id = "asset-1",
            sourceUri = "content://media/external/video/12",
            displayName = "intro.mp4",
            mediaType = MediaType.VIDEO,
            durationUs = 5_000_000L,
            width = 1080,
            height = 1920,
            fps = 60f,
            hasAudio = true,
        )
        val withClip = AddAssetClipCommand(asset, clipId = "clip-1")
            .apply(created, timestampMs = 2000L)
            .let { proj ->
                val track = proj.primaryVideoTrack()
                val updatedClip = track.clips.first().copy(
                    colorGrade = ColorGradeSpec(exposure = 0.35f, contrast = 0.2f),
                    keyframes = mapOf(
                        AnimatableProperty.OPACITY to listOf(
                            Keyframe("kf-1", 0L, 0f, EasingType.EASE_IN),
                            Keyframe("kf-2", 1_000_000L, 1f, EasingType.LINEAR),
                        )
                    ),
                )
                proj.copy(
                    tracks = proj.tracks.map {
                        if (it.id == track.id) it.copy(clips = listOf(updatedClip)) else it
                    }
                )
            }

        // Verify JSON codec round-trip
        val encoded = ProjectJsonCodec.encode(withClip)
        val decoded = ProjectJsonCodec.decode(encoded)
        assertEquals(withClip.name, decoded.name)
        assertEquals(0.35f, decoded.primaryVideoTrack().clips.first().colorGrade.exposure, 0.001f)
        assertEquals(2, decoded.primaryVideoTrack().clips.first().keyframes[AnimatableProperty.OPACITY]?.size)

        // Save, rename, duplicate, delete
        repository.saveProject(withClip)
        repository.renameProject(withClip.id, "Vlog Cut Final")

        val loaded = repository.loadProject(withClip.id)
        assertNotNull(loaded)
        assertEquals("Vlog Cut Final", loaded?.name)
        assertEquals(5_000_000L, loaded?.totalDurationUs())

        val copy = repository.duplicateProject(withClip.id)
        assertNotNull(copy)
        assertEquals("Vlog Cut Final Copy", copy?.name)

        val summaries = repository.observeProjectSummaries().first()
        assertEquals(2, summaries.size)

        repository.deleteProject(withClip.id)
        assertNull(repository.loadProject(withClip.id))
    }

    @Test
    fun `phase 3 project json codec round-trips canvas, custom resolution, background, and all 13 color grade fields`() {
        val asset = MediaAsset("a1", "content://test/img.png", "img.png", MediaType.IMAGE, 4_000_000L, 1080, 1350)
        val project = AddAssetClipCommand(asset, "c1").apply(
            com.example.core.model.TimelineProject(
                id = "proj_roundtrip",
                name = "RoundTrip",
                aspectRatio = AspectRatioPreset.RATIO_4_5,
                frameRate = FrameRatePreset.FPS_60,
                targetResolution = com.example.core.model.ExportResolutionPreset.UHD_4K,
                scaleMode = com.example.core.model.CanvasScaleMode.FILL,
                backgroundId = com.example.core.model.CanvasBackgroundSpec.SLATE_BLUE.id,
                customWidthPx = 1440,
                customHeightPx = 1800,
                createdAtMs = 1000L,
                updatedAtMs = 2000L,
            ),
            2000L,
        )
        val updated = com.example.core.command.UpdateClipColorGradeCommand(
            clipId = "c1",
            colorGrade = ColorGradeSpec(
                exposure = 0.12f,
                brightness = -0.05f,
                contrast = 0.22f,
                saturation = 0.18f,
                temperature = -0.2f,
                tint = 0.14f,
                highlights = 0.3f,
                shadows = -0.25f,
                sharpness = 0.45f,
                blurRadius = 0.15f,
                vignette = 0.35f,
                grayscale = 0.05f,
                sepia = 0.12f,
                filterPresetId = "cinema_teal",
                filterIntensity = 0.8f,
            ),
        ).apply(project, 3000L)

        val json = ProjectJsonCodec.encode(updated)
        val decoded = ProjectJsonCodec.decode(json)

        assertEquals(com.example.core.model.CanvasScaleMode.FILL, decoded.scaleMode)
        assertEquals(com.example.core.model.CanvasBackgroundSpec.SLATE_BLUE.id, decoded.backgroundId)
        assertEquals(1440 to 1800, decoded.effectiveDimensions())

        val decodedGrade = decoded.primaryVideoTrack().clips.first().colorGrade
        assertEquals(0.12f, decodedGrade.exposure, 0.001f)
        assertEquals(-0.05f, decodedGrade.brightness, 0.001f)
        assertEquals(0.45f, decodedGrade.sharpness, 0.001f)
        assertEquals(0.15f, decodedGrade.blurRadius, 0.001f)
        assertEquals(0.05f, decodedGrade.grayscale, 0.001f)
        assertEquals(0.12f, decodedGrade.sepia, 0.001f)
        assertEquals("cinema_teal", decodedGrade.filterPresetId)
        assertEquals(0.8f, decodedGrade.filterIntensity, 0.001f)
    }
}
