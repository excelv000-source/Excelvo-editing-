package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.command.MoveClipToTrackCommand
import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.EasingType
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineProject
import com.example.core.model.VisualEffectType
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3FinalUpdateComposeTest {

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

    private fun seedMultiLayerProject(): String = runBlocking {
        val created = projectRepository.createProject(
            name = "Final UI Studio",
            aspectRatio = AspectRatioPreset.RATIO_4_5,
            frameRate = FrameRatePreset.FPS_60,
        )
        val vAsset = MediaAsset("v1", "content://test/main.mp4", "MainLayer.mp4", MediaType.VIDEO, 4_000_000L, 1080, 1350)
        val oAsset = MediaAsset("o1", "content://test/overlay.png", "OverlayBadge.png", MediaType.IMAGE, 3_000_000L, 512, 512)
        var p = AddAssetClipCommand(vAsset, "clip-v1").apply(created, 100L)
        p = AddAssetClipCommand(oAsset, "clip-o1").apply(p, 101L)
        p = MoveClipToTrackCommand("clip-o1", TimelineProject.OVERLAY_TRACK_ID).apply(p, 102L)
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
    fun `tapping timeline clip opens Layer Menu, panels hide global toolbar exclusively, and Back restores selection and state`() {
        val projectId = seedMultiLayerProject()
        val vm = launchStudio(projectId)

        // Initially global studio toolbar and bottom-right Import FAB are visible; Layer Menu is closed
        composeRule.onNodeWithTag("studio_tab_EDIT").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_fab_import").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_editing_menu").assertDoesNotExist()

        // Move playhead inside overlapping clips to verify playhead preservation across Back navigation
        vm.seekToUs(1_000_000L)
        composeRule.waitForIdle()

        // 1. Tap clip on timeline -> opens 4x3 Layer Editing Sheet and hides Import FAB
        composeRule.onNodeWithTag("timeline_clip_clip-v1").performScrollTo().performClick()
        waitUntil { vm.uiState.value.isLayerMenuVisible }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("layer_editing_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_fab_import").assertDoesNotExist()
        composeRule.onNodeWithTag("layer_menu_MOVE_TRANSFORM").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_COLOR_FILL").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_EFFECTS").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_BLENDING").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_OPACITY").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_KEYFRAMES").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_PRESETS").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_DUPLICATE").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_GROUP").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_SPLIT").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_RESET").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_menu_DELETE").assertIsDisplayed()

        // Track visibility Eye toggle in Layer Sheet hides primary track & clears activePrimaryClip, then timeline eye restores it
        composeRule.onNodeWithTag("layer_menu_visibility_toggle").performClick()
        waitUntil { vm.uiState.value.activePrimaryClip == null }
        composeRule.onNodeWithTag("track_visibility_${TimelineProject.PRIMARY_TRACK_ID}").assertIsDisplayed().performClick()
        waitUntil { vm.uiState.value.activePrimaryClip?.id == "clip-v1" }

        // Open Presets tile -> focused drawer exclusively, then Back restores Layer Sheet
        composeRule.onNodeWithTag("layer_menu_PRESETS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focused_layer_panel_header").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_tab_EDIT").assertDoesNotExist()
        composeRule.onNodeWithTag("layer_panel_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("layer_editing_menu").assertIsDisplayed()

        // 2. Open Move & Transform -> ONLY Move & Transform is shown; Layer Menu & global toolbar hidden
        composeRule.onNodeWithTag("layer_menu_MOVE_TRANSFORM").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focused_layer_panel_header").assertIsDisplayed()
        composeRule.onNodeWithTag("motion_touchpad").assertIsDisplayed()
        composeRule.onNodeWithTag("layer_editing_menu").assertDoesNotExist()
        composeRule.onNodeWithTag("studio_tab_EDIT").assertDoesNotExist()
        composeRule.onNodeWithTag("studio_tab_EFFECTS").assertDoesNotExist()

        // Edit X/Y via Touchpad + numeric inputs + Z + Rotate + Scale + Skew
        composeRule.onNodeWithTag("motion_touchpad").performTouchInput { swipeRight() }
        waitUntil { (vm.uiState.value.selectedClip?.transform?.positionX ?: 0f) > 5f }

        composeRule.onNodeWithTag("motion_pos_y_input").performTextReplacement("-25")
        waitUntil { (vm.uiState.value.selectedClip?.transform?.positionY ?: 0f) == -25f }

        composeRule.onNodeWithTag("motion_z_move_input").performTextReplacement("45")
        waitUntil { (vm.uiState.value.selectedClip?.transform?.positionZ ?: 0f) == 45f }

        // Rotate subtab + numeric angle input
        composeRule.onNodeWithTag("motion_subtab_ROTATE").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("motion_rotate_input").performTextReplacement("35")
        waitUntil { (vm.uiState.value.selectedClip?.transform?.rotationDeg ?: 0f) == 35f }

        // Size subtab: unlock uniform & set non-uniform Scale X / Scale Y
        composeRule.onNodeWithTag("motion_subtab_SCALE").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("motion_uniform_scale_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("motion_scale_x_input").performTextReplacement("1.40")
        composeRule.onNodeWithTag("motion_scale_y_input").performTextReplacement("0.80")
        waitUntil {
            val t = vm.uiState.value.selectedClip?.transform
            t != null && kotlin.math.abs(t.scaleX - 1.4f) < 0.05f && kotlin.math.abs(t.scaleY - 0.8f) < 0.05f
        }

        // Skew subtab: set Skew X and Skew Y
        composeRule.onNodeWithTag("motion_subtab_SKEW").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("motion_skew_x_input").performTextReplacement("18")
        composeRule.onNodeWithTag("motion_skew_y_input").performTextReplacement("-12")
        waitUntil {
            val t = vm.uiState.value.selectedClip?.transform
            t != null && t.skewXDeg == 18f && t.skewYDeg == -12f
        }

        // 3. Back returns cleanly to Layer Editing Menu preserving clip selection, playhead, and transforms
        composeRule.onNodeWithTag("layer_panel_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("layer_editing_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("focused_layer_panel_header").assertDoesNotExist()
        assertEquals("clip-v1", vm.uiState.value.selectedClipId)
        assertEquals(1_000_000L, vm.uiState.value.playheadUs)
        assertEquals(18f, vm.uiState.value.selectedClip!!.transform.skewXDeg, 0.01f)

        // Open Keyframes Inspector -> add keyframe for SKEW_X and switch easing
        composeRule.onNodeWithTag("layer_menu_KEYFRAMES").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("keyframes_inspector_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("kf_prop_SKEW_X").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("kf_panel_toggle_diamond").performClick()
        waitUntil { vm.uiState.value.selectedClip?.keyframes?.get(AnimatableProperty.SKEW_X)?.isNotEmpty() == true }
        composeRule.onNodeWithTag("kf_panel_easing_EASE_OUT").performScrollTo().performClick()
        waitUntil {
            vm.uiState.value.selectedClip?.keyframes?.get(AnimatableProperty.SKEW_X)?.firstOrNull()?.easing == EasingType.EASE_OUT
        }
        composeRule.onNodeWithTag("layer_panel_back_button").performClick()
        composeRule.waitForIdle()

        // Open Effects -> add GLOW only to clip-v1, verify clip-o1 untouched
        composeRule.onNodeWithTag("layer_menu_EFFECTS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_effect_GLOW").performScrollTo().performClick()
        waitUntil { vm.uiState.value.selectedClip?.effectStack?.any { it.type == VisualEffectType.GLOW } == true }
        val overlayClip = vm.uiState.value.project!!.allClips().first { it.id == "clip-o1" }
        assertTrue(overlayClip.effectStack.isEmpty())
        composeRule.onNodeWithTag("layer_panel_back_button").performClick()
        composeRule.waitForIdle()

        // Open Color & Fill -> adjust grading param + apply cinema_teal filter preset
        composeRule.onNodeWithTag("layer_menu_COLOR_FILL").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("adjust_param_SATURATION").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("color_fill_tab_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_card_cinema_teal").performScrollTo().performClick()
        waitUntil { vm.uiState.value.selectedClip?.colorGrade?.filterPresetId == "cinema_teal" }
        composeRule.onNodeWithTag("layer_panel_back_button").performClick()
        composeRule.waitForIdle()

        // Open Blending -> select MULTIPLY
        composeRule.onNodeWithTag("layer_menu_BLENDING").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("blend_mode_MULTIPLY").performScrollTo().performClick()
        waitUntil { vm.uiState.value.selectedClip?.blendMode == com.example.core.model.BlendModeType.MULTIPLY }
        composeRule.onNodeWithTag("layer_panel_back_button").performClick()
        composeRule.waitForIdle()

        // Open Opacity -> tap 50% preset
        composeRule.onNodeWithTag("layer_menu_OPACITY").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("opacity_preset_50").performScrollTo().performClick()
        waitUntil { kotlin.math.abs((vm.uiState.value.selectedClip?.transform?.opacity ?: 1f) - 0.5f) < 0.01f }
        composeRule.onNodeWithTag("layer_panel_back_button").performClick()
        composeRule.waitForIdle()

        // Group overlapping layers -> Group badge appears on timeline
        composeRule.onNodeWithTag("layer_menu_GROUP").performClick()
        waitUntil { vm.uiState.value.selectedClip?.groupId != null }
        composeRule.onNodeWithTag("clip_group_badge_clip-v1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        // Save to Room and verify persistence on reload
        composeRule.onNodeWithTag("studio_save_button").performClick()
        waitUntil {
            runBlocking {
                projectRepository.loadProject(projectId)
                    ?.allClips()
                    ?.firstOrNull { it.id == "clip-v1" }
                    ?.groupId != null
            }
        }
        val saved = runBlocking { projectRepository.loadProject(projectId) }
        assertNotNull(saved)
        val savedMain = saved!!.allClips().first { it.id == "clip-v1" }
        assertEquals(18f, savedMain.transform.skewXDeg, 0.01f)
        assertEquals("cinema_teal", savedMain.colorGrade.filterPresetId)
        assertEquals(com.example.core.model.BlendModeType.MULTIPLY, savedMain.blendMode)
        assertNotNull(savedMain.groupId)

        // Reset tile resets clip transforms, grade, effects, blend & keyframes
        composeRule.onNodeWithTag("layer_menu_RESET").performClick()
        waitUntil {
            val resetClip = vm.uiState.value.selectedClip
            resetClip != null &&
                resetClip.transform.skewXDeg == 0f &&
                resetClip.colorGrade.filterPresetId == null &&
                resetClip.blendMode == com.example.core.model.BlendModeType.NORMAL &&
                resetClip.effectStack.isEmpty()
        }

        // Close Layer Menu restores global bottom toolbar and Import FAB
        composeRule.onNodeWithTag("layer_menu_close_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_tab_EDIT").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_fab_import").assertIsDisplayed()
    }
}
