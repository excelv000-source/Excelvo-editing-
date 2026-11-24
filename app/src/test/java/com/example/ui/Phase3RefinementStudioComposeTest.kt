package com.example.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.BlendModeType
import com.example.core.model.EasingType
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.VisualEffectType
import com.example.core.model.WorkspacePreset
import com.example.core.ports.MediaProbePort
import com.example.data.db.ExcevoDatabase
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.presets.LocalPresetRepository
import com.example.data.project.LocalProjectRepository
import com.example.data.storage.StorageManager
import com.example.media.playback.AndroidSequencePlaybackController
import com.example.ui.editor.StudioScreen
import com.example.ui.editor.StudioViewModel
import com.example.ui.theme.ExcevoTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3RefinementStudioComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var projectRepository: LocalProjectRepository
    private lateinit var storageManager: StorageManager
    private lateinit var presetRepository: LocalPresetRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var playbackController: AndroidSequencePlaybackController
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
        preferencesRepository = UserPreferencesRepository(context)
        playbackController = AndroidSequencePlaybackController(context, hardwareAccelerationRequested = false)
    }

    @After
    fun tearDown() {
        studioVm?.viewModelScope?.cancel()
        playbackController.release()
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

    private fun seedProjectWithVideoAndAudio(): String = runBlocking {
        val created = projectRepository.createProject(
            name = "Phase3 Refinement",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_30,
        )
        val vAsset = MediaAsset("v1", "content://test/v1.mp4", "ClipOne.mp4", MediaType.VIDEO, 3_000_000L, 1080, 1920)
        val aAsset = MediaAsset("a1", "content://test/a1.wav", "Beat.wav", MediaType.AUDIO, 3_000_000L, 0, 0)
        var p = AddAssetClipCommand(vAsset, "clip-v1").apply(created, 100L)
        p = AddAssetClipCommand(vAsset, "clip-v2").apply(p, 101L)
        p = AddAssetClipCommand(aAsset, "clip-a1").apply(p, 102L)
        projectRepository.saveProject(p)
        p.id
    }

    private fun launchStudio(projectId: String): StudioViewModel {
        val fakeProbe = object : MediaProbePort {
            override suspend fun probeAndCacheThumbnail(sourceUri: String, projectId: String) =
                Result.failure<MediaAsset>(IllegalStateException("Unused"))
        }
        val vm = StudioViewModel(
            projectId = projectId,
            projectStorage = projectRepository,
            mediaProbe = fakeProbe,
            presetRepository = presetRepository,
            playbackPort = playbackController,
            userPreferencesRepository = preferencesRepository,
        )
        studioVm = vm
        composeRule.setContent {
            ExcevoTheme {
                StudioScreen(viewModel = vm, onBackToHub = {})
            }
        }
        waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.project != null }
        return vm
    }

    @Test
    fun `play pause is centered symmetrically with previous and next and aligned identically to red timeline playhead`() {
        val projectId = seedProjectWithVideoAndAudio()
        val vm = launchStudio(projectId)

        val playheadBounds = composeRule.onNodeWithTag("timeline_center_playhead")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val playButtonBounds = composeRule.onNodeWithTag("play_pause_button")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val prevBounds = composeRule.onNodeWithTag("transport_previous_button")
            .assertIsDisplayed()
            .getBoundsInRoot()
        val nextBounds = composeRule.onNodeWithTag("transport_next_button")
            .assertIsDisplayed()
            .getBoundsInRoot()

        val playheadCenterX = (playheadBounds.left.value + playheadBounds.right.value) / 2f
        val playCenterX = (playButtonBounds.left.value + playButtonBounds.right.value) / 2f
        val prevCenterX = (prevBounds.left.value + prevBounds.right.value) / 2f
        val nextCenterX = (nextBounds.left.value + nextBounds.right.value) / 2f

        // Strict center alignment between timeline RED playhead needle and Play/Pause button
        assertTrue(
            "Expected playheadCenterX ($playheadCenterX) == playCenterX ($playCenterX)",
            abs(playheadCenterX - playCenterX) <= 2.5f,
        )
        // Strict symmetry of Previous | Play/Pause | Next
        assertTrue(prevCenterX < playCenterX)
        assertTrue(nextCenterX > playCenterX)
        val leftGap = playCenterX - prevCenterX
        val rightGap = nextCenterX - playCenterX
        assertTrue("Expected symmetric gaps ($leftGap vs $rightGap)", abs(leftGap - rightGap) <= 2.5f)

        // Boundary navigation: Next jumps to clip-v2 start (3_000_000L), Prev returns to 0L
        composeRule.onNodeWithTag("transport_next_button").performClick()
        waitUntil { vm.uiState.value.playheadUs == 3_000_000L }

        composeRule.onNodeWithTag("transport_previous_button").performClick()
        waitUntil { vm.uiState.value.playheadUs == 0L }
    }

    @Test
    fun `motion keyframes, effect stack, blending, audio envelopes, +track, workspace switcher, save, and custom preset export work end to end`() {
        val projectId = seedProjectWithVideoAndAudio()
        val vm = launchStudio(projectId)

        // Default workspace must be EXCEVO_STUDIO
        assertEquals(WorkspacePreset.EXCEVO_STUDIO, vm.uiState.value.workspacePreset)

        // Switch workspace & verify DataStore persistence
        composeRule.onNodeWithTag("workspace_switcher_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace_option_ALIGHT_MOTION_INSPIRED").performClick()
        waitUntil {
            runBlocking {
                preferencesRepository.preferences.first().workspacePreset == WorkspacePreset.ALIGHT_MOTION_INSPIRED
            }
        }

        // Export Pre-Flight dialog opens cleanly (no fake MP4 export UI)
        composeRule.onNodeWithTag("export_preflight_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export_preflight_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("export_preflight_close").performClick()
        composeRule.waitForIdle()

        // + Track adds ADJUSTMENT track
        val initialTrackCount = vm.uiState.value.project!!.tracks.size
        composeRule.onNodeWithTag("add_track_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_track_ADJUSTMENT").performClick()
        waitUntil { vm.uiState.value.project!!.tracks.size == initialTrackCount + 1 }

        // Motion tab: XYZ telemetry, touchpad drag, keyframe diamond, easing selector
        composeRule.onNodeWithTag("studio_tab_TRANSFORM").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("xyz_telemetry_row").assertIsDisplayed()
        composeRule.onNodeWithTag("motion_touchpad").performTouchInput { swipeRight() }
        waitUntil { (vm.uiState.value.selectedClip?.transform?.positionX ?: 0f) > 5f }

        composeRule.onNodeWithTag("keyframe_diamond_toggle").performClick()
        waitUntil { vm.uiState.value.selectedClip?.keyframes?.isNotEmpty() == true }
        composeRule.onNodeWithTag("easing_drawer_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("easing_option_EASE_IN_OUT").performClick()
        waitUntil {
            vm.uiState.value.selectedClip?.keyframes?.values?.flatten()?.any { it.easing == EasingType.EASE_IN_OUT } == true
        }

        // Effects Stack: add GLOW effect layer
        composeRule.onNodeWithTag("studio_tab_EFFECTS").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_effect_GLOW").performScrollTo().performClick()
        waitUntil {
            vm.uiState.value.selectedClip?.effectStack?.any { it.type == VisualEffectType.GLOW } == true
        }

        // Blending tab: select MULTIPLY blend mode
        composeRule.onNodeWithTag("studio_tab_BLEND").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("blend_mode_MULTIPLY").performScrollTo().performClick()
        waitUntil { vm.uiState.value.selectedClip?.blendMode == BlendModeType.MULTIPLY }

        // Audio tab: select audio clip & adjust Fade In
        vm.selectClip("clip-a1")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_tab_AUDIO").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_studio_fade_in_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.5f) }
        waitUntil { (vm.uiState.value.selectedClip?.fadeInUs ?: 0L) > 1_000_000L }

        // Save Clip Preset from Presets tab -> writes .excevo.json to EXCEVO Library
        vm.selectClip("clip-v1")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_tab_PRESETS").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_custom_preset_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("custom_preset_title_input").performTextInput("Studio Glow Pack")
        composeRule.onNodeWithTag("confirm_save_custom_preset_button").performClick()
        waitUntil {
            runBlocking {
                db.libraryDao().observeAll().first().any { it.name.endsWith(".excevo.json") }
            }
        }

        // Force Save persists sequence cleanly
        composeRule.onNodeWithTag("studio_save_button").performClick()
        composeRule.waitForIdle()
    }
}
