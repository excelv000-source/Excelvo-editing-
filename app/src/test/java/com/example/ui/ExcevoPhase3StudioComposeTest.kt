package com.example.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.CanvasBackgroundSpec
import com.example.core.model.CanvasScaleMode
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.ports.MediaProbePort
import com.example.data.db.ExcevoDatabase
import com.example.data.presets.LocalPresetRepository
import com.example.data.project.LocalProjectRepository
import com.example.data.storage.StorageManager
import com.example.media.playback.AndroidSequencePlaybackController
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.CreateProjectRequest
import com.example.ui.editor.StudioScreen
import com.example.ui.editor.StudioViewModel
import com.example.ui.presets.PresetTab
import com.example.ui.presets.PresetsScreen
import com.example.ui.presets.PresetsViewModel
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
class ExcevoPhase3StudioComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var projectRepository: LocalProjectRepository
    private lateinit var storageManager: StorageManager
    private lateinit var presetRepository: LocalPresetRepository
    private lateinit var playbackController: AndroidSequencePlaybackController
    private var studioVm: StudioViewModel? = null
    private var presetsVm: PresetsViewModel? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExcevoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = LocalProjectRepository(context, db.projectDao())
        storageManager = StorageManager(context, db.libraryDao())
        presetRepository = LocalPresetRepository(db.savedPresetDao(), projectRepository, storageManager)
        playbackController = AndroidSequencePlaybackController(context, hardwareAccelerationRequested = false)
    }

    @After
    fun tearDown() {
        studioVm?.viewModelScope?.cancel()
        presetsVm?.viewModelScope?.cancel()
        playbackController.release()
        db.close()
    }

    private fun waitUntilCondition(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
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

    private fun seedStudioProject(): String = runBlocking {
        val created = projectRepository.createProject(
            name = "Phase3 Studio",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_30,
        )
        val asset = MediaAsset(
            id = "a1",
            sourceUri = "content://test/sample.mp4",
            displayName = "HeroClip.mp4",
            mediaType = MediaType.VIDEO,
            durationUs = 4_000_000L,
            width = 1080,
            height = 1920,
        )
        projectRepository.saveProject(AddAssetClipCommand(asset, "clip-1").apply(created, 1000L))
        created.id
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
        )
        studioVm = vm
        composeRule.setContent {
            ExcevoTheme {
                StudioScreen(
                    viewModel = vm,
                    onBackToHub = {},
                )
            }
        }
        waitUntilCondition { !vm.uiState.value.isLoading && vm.uiState.value.presetsCatalog.isNotEmpty() }
        return vm
    }

    @Test
    fun `studio bottom toolbox applies filters, color adjust with reset, transform rotate and fit-fill, and presets with room persistence`() {
        val projectId = seedStudioProject()
        val vm = launchStudio(projectId)

        // GPU badge & interactive Fit/Fill toggle
        composeRule.onNodeWithTag("gpu_pipeline_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("stage_scale_mode_chip").assertIsDisplayed().performClick()
        waitUntilCondition { vm.uiState.value.project?.scaleMode == CanvasScaleMode.FILL }

        // Filters tab -> select Cinema Teal -> persists on clip
        composeRule.onNodeWithTag("studio_tab_FILTERS").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("filter_card_cinema_teal").performScrollTo().performClick()
        waitUntilCondition {
            vm.uiState.value.selectedClip?.colorGrade?.filterPresetId == "cinema_teal"
        }

        // Adjust tab -> SetProgress commits brightness + single reset restores 0f
        composeRule.onNodeWithTag("studio_tab_ADJUST").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("adjust_slider_BRIGHTNESS")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.45f) }
        waitUntilCondition {
            (vm.uiState.value.selectedClip?.colorGrade?.brightness ?: 0f) > 0.40f
        }
        composeRule.onNodeWithTag("undo_button").assertIsEnabled()

        composeRule.onNodeWithTag("reset_adjust_BRIGHTNESS").performClick()
        waitUntilCondition {
            (vm.uiState.value.selectedClip?.colorGrade?.brightness ?: 1f) == 0f
        }

        // Effects tab -> Blur SetProgress + Reset FX
        composeRule.onNodeWithTag("studio_tab_EFFECTS").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("effect_slider_BLUR")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.55f) }
        waitUntilCondition {
            (vm.uiState.value.selectedClip?.colorGrade?.blurRadius ?: 0f) > 0.50f
        }
        composeRule.onNodeWithTag("reset_all_effects_button").performScrollTo().performClick()
        waitUntilCondition {
            (vm.uiState.value.selectedClip?.colorGrade?.blurRadius ?: 1f) == 0f
        }

        // Transform tab -> +90 deg rotate + Reset Transform
        composeRule.onNodeWithTag("studio_tab_TRANSFORM").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transform_rotate_90_button").performScrollTo().performClick()
        waitUntilCondition {
            (vm.uiState.value.selectedClip?.transform?.rotationDeg ?: 0f) == 90f
        }
        composeRule.onNodeWithTag("transform_reset_button").performScrollTo().performClick()
        waitUntilCondition {
            (vm.uiState.value.selectedClip?.transform?.rotationDeg ?: 90f) == 0f
        }

        // Clean preview toggle hides bottom toolbox and timeline, then restores them
        composeRule.onNodeWithTag("clean_preview_button").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.isCleanPreview)
        composeRule.onNodeWithTag("clean_preview_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_tab_EDIT").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(4_500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline_zoom_out").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trim_handle_left_clip-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("trim_handle_right_clip-1", useUnmergedTree = true).assertIsDisplayed()

        // Presets tab -> Apply preset inside Studio
        composeRule.onNodeWithTag("studio_tab_PRESETS").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_preset_apply_button").performClick()
        composeRule.waitForIdle()

        // Verify Room persisted scaleMode FILL & preset color grade
        runBlocking {
            val reloaded = projectRepository.loadProject(projectId)!!
            assertEquals(CanvasScaleMode.FILL, reloaded.scaleMode)
        }
    }

    @Test
    fun `create project dialog supports customize resolution, background swatch, and elements tab`() {
        var createdRequest: CreateProjectRequest? = null
        var importedVideo = false

        composeRule.setContent {
            ExcevoTheme {
                CreateProjectDialog(
                    onDismiss = {},
                    onCreate = { createdRequest = it },
                    onImportVideoElement = {
                        importedVideo = true
                        createdRequest = it
                    },
                    onImportImagesElement = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("project_name_input").performTextInput("Custom Showcase")
        composeRule.onNodeWithTag("bg_option_violet").performClick()
        composeRule.onNodeWithTag("customize_resolution_toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("create_scale_mode_FILL").performClick()
        composeRule.onNodeWithTag("create_tab_ELEMENTS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("elements_import_video_button").performClick()

        assertTrue(importedVideo)
        val req = createdRequest
        assertNotNull(req)
        assertEquals("Custom Showcase", req?.name)
        assertEquals(CanvasBackgroundSpec.MIDNIGHT_VIOLET.id, req?.backgroundId)
        assertEquals(CanvasScaleMode.FILL, req?.scaleMode)
    }

    @Test
    fun `preset detail modal displays engine compatibility, aspect ratios, and applies scaled intensity`() {
        val projectId = seedStudioProject()
        presetsVm = PresetsViewModel(presetRepository, projectRepository)

        composeRule.setContent {
            ExcevoTheme {
                PresetsScreen(
                    viewModel = presetsVm!!,
                    onOpenProject = {},
                )
            }
        }
        waitUntilCondition { presetsVm!!.uiState.value.presets.isNotEmpty() }

        composeRule.onNodeWithTag("preset_tab_${PresetTab.CC.name}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preset_card_builtin_cc_violet_noir").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Engine v1.0+ · Ratios: 16:9·9:16·1:1·4:5·21:9").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_intensity_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("apply_preset_to_$projectId").performClick()
        waitUntilCondition {
            runBlocking {
                val clip = projectRepository.loadProject(projectId)?.primaryVideoTrack()?.clips?.firstOrNull()
                clip != null && !clip.colorGrade.isNeutral() && clip.colorGrade.contrast < 0.20f
            }
        }
    }
}
