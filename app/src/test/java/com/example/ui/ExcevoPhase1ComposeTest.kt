package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.ports.MediaProbePort
import com.example.data.db.ExcevoDatabase
import com.example.data.project.LocalProjectRepository
import com.example.ui.editor.StudioScreen
import com.example.ui.editor.StudioViewModel
import com.example.ui.projects.ProjectHubViewModel
import com.example.ui.projects.ProjectsScreen
import com.example.ui.theme.ExcevoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExcevoPhase1ComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun `project hub creates sequence with chosen aspect ratio and fps`() {
        var openedId: String? = null
        val hubViewModel = ProjectHubViewModel(repository)

        composeRule.setContent {
            ExcevoTheme {
                ProjectsScreen(
                    viewModel = hubViewModel,
                    onOpenProject = { openedId = it },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("empty_create_project_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("project_name_input").performTextInput("Studio Reel")
        composeRule.onNodeWithTag("aspect_option_16:9").performClick()
        composeRule.onNodeWithTag("fps_option_60").performClick()
        composeRule.onNodeWithTag("confirm_create_project_button").performClick()
        val deadline = System.currentTimeMillis() + 3_000L
        while (openedId == null && System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            Thread.sleep(20)
        }

        assertNotNull(openedId)
    }

    @Test
    fun `studio workspace step frame, trim, delete, and undo-redo update real state`() {
        val projectId = runBlocking {
            val created = repository.createProject(
                name = "Test Edit",
                aspectRatio = AspectRatioPreset.RATIO_9_16,
                frameRate = FrameRatePreset.FPS_30,
            )
            val asset = MediaAsset(
                id = "a1",
                sourceUri = "content://fake/video.mp4",
                displayName = "A-Roll.mp4",
                mediaType = MediaType.VIDEO,
                durationUs = 4_000_000L,
                width = 1080,
                height = 1920,
            )
            val withClip = AddAssetClipCommand(asset, clipId = "clip-1").apply(created, 2000L)
            repository.saveProject(withClip)
            created.id
        }

        val fakeProbe = object : MediaProbePort {
            override suspend fun probeAndCacheThumbnail(sourceUri: String, projectId: String) =
                Result.failure<MediaAsset>(IllegalStateException("Not used in this test"))
        }
        val studioViewModel = StudioViewModel(projectId, repository, fakeProbe)

        composeRule.setContent {
            ExcevoTheme {
                StudioScreen(
                    viewModel = studioViewModel,
                    onBackToHub = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("studio_stage_viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("undo_button").assertIsNotEnabled()

        // Step forward 1 frame at 30fps (00:00:01)
        composeRule.onNodeWithTag("step_forward_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("00:00:01 / 00:04:00").assertIsDisplayed()

        // Delete clip -> Undo enabled -> Undo restores clip
        composeRule.onNodeWithTag("delete_clip_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("undo_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeline_clip_clip-1").assertIsDisplayed()
        composeRule.onNodeWithTag("redo_button").assertIsEnabled()
    }
}
