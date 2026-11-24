package com.example.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.ports.MediaProbePort
import com.example.data.db.ExcevoDatabase
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.presets.LocalPresetRepository
import com.example.data.project.LocalProjectRepository
import com.example.data.storage.StorageManager
import com.example.ui.navigation.MainShellScreen
import com.example.ui.presets.PresetsViewModel
import com.example.ui.profile.ProfileViewModel
import com.example.ui.projects.ProjectHubViewModel
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
class ExcevoFloatingDockComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var projectRepository: LocalProjectRepository
    private lateinit var presetRepository: LocalPresetRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var storageManager: StorageManager
    private lateinit var hubViewModel: ProjectHubViewModel
    private lateinit var presetsViewModel: PresetsViewModel
    private lateinit var profileViewModel: ProfileViewModel

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
    }

    @After
    fun tearDown() {
        if (::hubViewModel.isInitialized) hubViewModel.viewModelScope.cancel()
        if (::presetsViewModel.isInitialized) presetsViewModel.viewModelScope.cancel()
        if (::profileViewModel.isInitialized) profileViewModel.viewModelScope.cancel()
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
        if (condition()) return
        composeRule.waitForIdle()
        assertTrue("Timed out after ${timeoutMs}ms", condition())
    }

    private fun launchShell(
        mediaProbe: MediaProbePort? = null,
        onOpenProject: (String) -> Unit = {},
    ) {
        hubViewModel = ProjectHubViewModel(projectRepository, preferencesRepository, mediaProbe)
        presetsViewModel = PresetsViewModel(presetRepository, projectRepository)
        profileViewModel = ProfileViewModel(preferencesRepository, storageManager, projectRepository, presetRepository)
        composeRule.setContent {
            ExcevoTheme {
                MainShellScreen(
                    hubViewModel = hubViewModel,
                    presetsViewModel = presetsViewModel,
                    profileViewModel = profileViewModel,
                    onOpenProject = onOpenProject,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `floating dock renders 5 slots and navigates cleanly across all 4 sections`() {
        launchShell()

        composeRule.onNodeWithTag("excevo_floating_dock").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_HOME").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_PROJECTS").assertIsDisplayed()
        composeRule.onNodeWithTag("dock_center_plus_button").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_PRESETS").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_PROFILE").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_PROJECTS").performClick()
        waitUntilCondition { !hubViewModel.uiState.value.isLoading }
        composeRule.onNodeWithTag("empty_create_project_button").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_PRESETS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("presets_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tab_PROFILE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen_list").assertIsDisplayed()
    }

    @Test
    fun `center plus button opens composition sheet, locks dock, and creates Blank Project`() {
        var openedProjectId: String? = null
        launchShell(onOpenProject = { openedProjectId = it })

        composeRule.onNodeWithTag("dock_center_plus_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("new_composition_bottom_sheet").assertIsDisplayed()
        composeRule.onAllNodesWithTag("excevo_floating_dock").assertCountEquals(0)

        composeRule.onNodeWithTag("sheet_action_blank_project").performScrollTo().performClick()
        waitUntilCondition { openedProjectId != null }

        val createdId = openedProjectId!!
        runBlocking {
            val loaded = projectRepository.loadProject(createdId)
            assertNotNull(loaded)
            assertEquals("Blank Sequence", loaded?.name)
        }
    }

    @Test
    fun `New Composition shows all 6 ratio icons and resolution options and persists 4_3 at 4K`() {
        var openedProjectId: String? = null
        launchShell(onOpenProject = { openedProjectId = it })

        composeRule.onNodeWithTag("dock_center_plus_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sheet_action_new_project").performClick()
        composeRule.waitForIdle()

        // Verify all requested aspect ratio icons exist
        listOf("16:9", "9:16", "1:1", "4:5", "4:3", "3:4").forEach { id ->
            composeRule.onNodeWithTag("aspect_option_$id").performScrollTo().assertIsDisplayed()
        }
        // Verify resolution options exist
        listOf("720p", "1080p", "2K", "4K").forEach { resId ->
            composeRule.onNodeWithTag("resolution_option_$resId").performScrollTo().assertIsDisplayed()
        }

        composeRule.onNodeWithTag("project_name_input").performScrollTo().performTextInput("Classic 4K Cut")
        composeRule.onNodeWithTag("aspect_option_4:3").performScrollTo().performClick()
        composeRule.onNodeWithTag("resolution_option_4K").performScrollTo().performClick()
        composeRule.onNodeWithTag("fps_option_60").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_create_project_button").performScrollTo().performClick()
        waitUntilCondition { openedProjectId != null }

        runBlocking {
            val loaded = projectRepository.loadProject(openedProjectId!!)!!
            assertEquals("Classic 4K Cut", loaded.name)
            assertEquals(AspectRatioPreset.RATIO_4_3, loaded.aspectRatio)
            assertEquals(ExportResolutionPreset.UHD_4K, loaded.targetResolution)
            assertEquals(FrameRatePreset.FPS_60, loaded.frameRate)
        }
    }

    @Test
    fun `createProjectWithMedia probes real URI and attaches clip before opening Studio`() {
        val fakeProbe = object : MediaProbePort {
            override suspend fun probeAndCacheThumbnail(sourceUri: String, projectId: String) =
                Result.success(
                    MediaAsset(
                        id = "asset_probe_1",
                        sourceUri = sourceUri,
                        displayName = "ImportedClip.mp4",
                        mediaType = MediaType.VIDEO,
                        durationUs = 3_500_000L,
                        width = 1080,
                        height = 1920,
                    )
                )
        }
        var openedId: String? = null
        launchShell(mediaProbe = fakeProbe, onOpenProject = { openedId = it })

        hubViewModel.createProjectWithMedia(
            uriStrings = listOf("content://media/external/video/99"),
            defaultTitle = "Video Import",
            onCreated = { openedId = it },
        )
        waitUntilCondition { openedId != null }

        runBlocking {
            val loaded = projectRepository.loadProject(openedId!!)!!
            assertEquals(1, loaded.clipCount())
            assertEquals("ImportedClip.mp4", loaded.allClips().first().label)
        }
    }

    @Test
    fun `scrolling down hides floating dock and scrolling back shows it`() {
        launchShell()
        composeRule.onNodeWithTag("excevo_floating_dock").assertIsDisplayed()

        // Drag upward in content region above dock to scroll list down
        composeRule.onNodeWithTag("home_screen_list").performTouchInput {
            swipeUp(startY = bottom * 0.60f, endY = top + 20f, durationMillis = 150L)
        }
        waitUntilCondition {
            composeRule.onAllNodesWithTag("excevo_floating_dock").fetchSemanticsNodes().isEmpty()
        }

        // Scroll back down
        composeRule.onNodeWithTag("home_screen_list").performTouchInput {
            swipeDown(startY = top + 60f, endY = bottom * 0.72f, durationMillis = 150L)
        }
        waitUntilCondition {
            composeRule.onAllNodesWithTag("excevo_floating_dock").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("excevo_floating_dock").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h900dp")
    fun `expanded tablet layout renders NavigationRail and FAB instead of phone dock`() {
        launchShell()
        composeRule.onNodeWithTag("excevo_navigation_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("rail_new_composition_fab").assertIsDisplayed()
        composeRule.onAllNodesWithTag("excevo_floating_dock").assertCountEquals(0)
    }
}
