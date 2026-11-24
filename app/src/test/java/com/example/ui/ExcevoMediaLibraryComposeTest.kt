package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TrackType
import com.example.data.db.ExcevoDatabase
import com.example.data.media.MediaLibraryRepository
import com.example.data.presets.LocalPresetRepository
import com.example.data.project.LocalProjectRepository
import com.example.data.storage.StorageManager
import com.example.media.playback.AndroidSequencePlaybackController
import com.example.media.probe.AndroidMediaProbeAdapter
import com.example.ui.editor.StudioScreen
import com.example.ui.editor.StudioViewModel
import com.example.ui.media.MediaLibraryTab
import com.example.ui.theme.ExcevoTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExcevoMediaLibraryComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var projectRepository: LocalProjectRepository
    private lateinit var storageManager: StorageManager
    private lateinit var presetRepository: LocalPresetRepository
    private lateinit var mediaLibraryRepository: MediaLibraryRepository
    private lateinit var probeAdapter: AndroidMediaProbeAdapter
    private lateinit var playbackController: AndroidSequencePlaybackController
    private lateinit var tempDir: File
    private var studioVm: StudioViewModel? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExcevoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = LocalProjectRepository(context, db.projectDao())
        storageManager = StorageManager(context, db.libraryDao())
        presetRepository = LocalPresetRepository(db.savedPresetDao(), projectRepository, storageManager)
        mediaLibraryRepository = MediaLibraryRepository(context, db.recentMediaDao())
        probeAdapter = AndroidMediaProbeAdapter(context)
        playbackController = AndroidSequencePlaybackController(context, hardwareAccelerationRequested = false)
        tempDir = File(context.cacheDir, "ui_media_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        studioVm?.viewModelScope?.cancel()
        playbackController.release()
        tempDir.deleteRecursively()
        File(context.filesDir, "excevo_projects").deleteRecursively()
        db.close()
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            composeRule.waitForIdle()
            if (condition()) return
            Thread.sleep(25)
        }
        composeRule.waitForIdle()
        assertTrue("Timed out after ${timeoutMs}ms", condition())
    }

    private fun createTempJpeg(name: String): File {
        val file = File(tempDir, name)
        val bmp = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF7C3AED.toInt())
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }

    private fun createTempWav(name: String): File {
        val file = File(tempDir, name)
        val sr = 44100
        val frames = sr // 1.0 second
        val dataSize = frames * 2
        val bb = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(36 + dataSize)
        bb.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(sr)
        bb.putInt(sr * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataSize)
        for (i in 0 until frames) {
            bb.putShort((sin(2.0 * PI * 330.0 * i / sr) * 20000.0).toInt().toShort())
        }
        file.writeBytes(bb.array())
        return file
    }

    @Test
    fun `studio opens unified MediaLibrarySheet imports real photo and WAV audio to timeline and supports mute undo`() {
        val jpgFile = createTempJpeg("cover_art.jpg")
        val wavFile = createTempWav("backing_track.wav")

        val projectId = runBlocking {
            val created = projectRepository.createProject(
                name = "Media Import Studio",
                aspectRatio = AspectRatioPreset.RATIO_9_16,
                frameRate = FrameRatePreset.FPS_30,
            )
            // Seed recent media entries backed by real files so the unified library can select & import them
            val jpgAsset = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(jpgFile).toString(), "seed").getOrThrow()
            val wavAsset = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(wavFile).toString(), "seed").getOrThrow()
            mediaLibraryRepository.recordImportedAsset(jpgAsset)
            mediaLibraryRepository.recordImportedAsset(wavAsset)
            created.id
        }

        val vm = StudioViewModel(
            projectId = projectId,
            projectStorage = projectRepository,
            mediaProbe = probeAdapter,
            presetRepository = presetRepository,
            playbackPort = playbackController,
            mediaLibraryRepository = mediaLibraryRepository,
        )
        studioVm = vm

        composeRule.setContent {
            ExcevoTheme {
                StudioScreen(viewModel = vm, onBackToHub = {})
            }
        }
        waitUntil { !vm.uiState.value.isLoading }

        // Open Unified Media Library from Studio Edit drawer
        composeRule.onNodeWithTag("import_media_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_library_sheet").assertIsDisplayed()

        // Filter to IMAGES tab, select cover_art.jpg, preview shows up
        composeRule.onNodeWithTag("media_library_tab_${MediaLibraryTab.IMAGES.name}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_library_entry_cover_art.jpg").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_library_preview_card").assertIsDisplayed()

        // Switch to AUDIO tab, also select backing_track.wav
        composeRule.onNodeWithTag("media_library_tab_${MediaLibraryTab.AUDIO.name}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_library_entry_backing_track.wav").performClick()
        composeRule.waitForIdle()

        // Confirm multi-format import (1 image + 1 WAV audio)
        composeRule.onNodeWithTag("media_library_confirm_import_button").performClick()
        waitUntil {
            val proj = vm.uiState.value.project
            proj != null &&
                proj.primaryVideoTrack().clips.size == 1 &&
                proj.tracks.first { it.type == TrackType.AUDIO }.clips.size == 1
        }

        val proj = vm.uiState.value.project!!
        val audioClip = proj.tracks.first { it.type == TrackType.AUDIO }.clips.first()
        composeRule.waitForIdle()
        // Real waveform bar node rendered on A1 track
        composeRule.onNodeWithTag("audio_waveform_${audioClip.id}", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        // Toggle Mute on selected audio clip -> Undo restores unmuted
        vm.selectClip(audioClip.id)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clip_mute_button").performScrollTo().performClick()
        waitUntil { vm.uiState.value.selectedClip?.isMuted == true }
        composeRule.onNodeWithTag("undo_button").performClick()
        waitUntil { vm.uiState.value.selectedClip?.isMuted == false }
    }

    @Test
    fun `studio displays dismissible missing media banner when uncached file is deleted`() {
        val tempVideo = File(tempDir, "missing_source.mp4").apply { writeBytes(ByteArray(64) { 2 }) }
        val projectId = runBlocking {
            val created = projectRepository.createProject(
                name = "Missing Asset UI",
                aspectRatio = AspectRatioPreset.RATIO_16_9,
                frameRate = FrameRatePreset.FPS_30,
            )
            val asset = MediaAsset(
                id = "a-missing",
                sourceUri = Uri.fromFile(tempVideo).toString(),
                displayName = "missing_source.mp4",
                mediaType = MediaType.VIDEO,
                durationUs = 2_000_000L,
                width = 1920,
                height = 1080,
                uriPersisted = true,
            )
            projectRepository.saveProject(AddAssetClipCommand(asset, "c-miss").apply(created, 1000L))
            created.id
        }
        assertTrue(tempVideo.delete())

        val vm = StudioViewModel(
            projectId = projectId,
            projectStorage = projectRepository,
            mediaProbe = probeAdapter,
            presetRepository = presetRepository,
            playbackPort = playbackController,
        )
        studioVm = vm

        composeRule.setContent {
            ExcevoTheme {
                StudioScreen(viewModel = vm, onBackToHub = {})
            }
        }
        waitUntil { !vm.uiState.value.isLoading }

        composeRule.onNodeWithTag("missing_assets_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Unavailable source media: missing_source.mp4").assertIsDisplayed()
        composeRule.onNodeWithTag("dismiss_missing_assets_button").performClick()
        composeRule.waitForIdle()
        assertFalse(vm.uiState.value.missingAssetNames.isNotEmpty())
        assertEquals(0, vm.uiState.value.missingAssetNames.size)
    }
}
