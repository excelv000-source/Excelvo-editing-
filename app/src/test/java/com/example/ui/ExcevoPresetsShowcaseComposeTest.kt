package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.data.db.ExcevoDatabase
import com.example.data.presets.LocalPresetRepository
import com.example.data.project.LocalProjectRepository
import com.example.data.storage.StorageManager
import com.example.ui.presets.PresetTab
import com.example.ui.presets.PresetsScreen
import com.example.ui.presets.PresetsViewModel
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExcevoPresetsShowcaseComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var projectRepository: LocalProjectRepository
    private lateinit var storageManager: StorageManager
    private lateinit var presetRepository: LocalPresetRepository
    private lateinit var presetsVm: PresetsViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExcevoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = LocalProjectRepository(context, db.projectDao())
        storageManager = StorageManager(context, db.libraryDao())
        presetRepository = LocalPresetRepository(db.savedPresetDao(), projectRepository, storageManager)
    }

    @After
    fun tearDown() {
        if (::presetsVm.isInitialized) presetsVm.viewModelScope.cancel()
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

    private fun launchPresets() {
        presetsVm = PresetsViewModel(presetRepository, projectRepository)
        composeRule.setContent {
            ExcevoTheme {
                PresetsScreen(
                    viewModel = presetsVm,
                    onOpenProject = {},
                )
            }
        }
        waitUntilCondition { presetsVm.uiState.value.presets.isNotEmpty() }
    }

    @Test
    fun `presets screen renders all 8 category cards with accurate counts and horizontal sections`() {
        launchPresets()

        // Verify all 8 category cards are displayed
        PresetTab.categoryCards.forEach { tab ->
            composeRule.onNodeWithTag("preset_tab_${tab.name}").assertIsDisplayed()
        }

        // Verify real built-in category counts & zero fake community counts
        val counts = presetsVm.uiState.value.categoryCounts
        assertEquals(3, counts[PresetTab.SHAKES])
        assertEquals(3, counts[PresetTab.TRANSITIONS])
        assertEquals(3, counts[PresetTab.EFFECTS])
        assertEquals(3, counts[PresetTab.ONE_FRAME])
        assertEquals(3, counts[PresetTab.CC])
        assertEquals(3, counts[PresetTab.TEXT])
        assertEquals(0, counts[PresetTab.COMMUNITY])

        // Horizontal sections visible across scroll
        composeRule.onNodeWithTag("preset_section_SHAKES").assertIsDisplayed()
        composeRule.onNodeWithTag("presets_list").performScrollToNode(hasTestTag("preset_section_TRANSITIONS"))
        composeRule.onNodeWithTag("preset_section_TRANSITIONS").assertIsDisplayed()
        composeRule.onNodeWithTag("presets_list").performScrollToNode(hasTestTag("preset_section_ONE_FRAME"))
        composeRule.onNodeWithTag("preset_section_ONE_FRAME").assertIsDisplayed()
        composeRule.onNodeWithTag("presets_list").performScrollToNode(hasTestTag("preset_section_TEXT"))
        composeRule.onNodeWithTag("preset_section_TEXT").assertIsDisplayed()
    }

    @Test
    fun `1 Frames and Transitions detail preview applies real keyframes to Room sequence`() {
        val projectId = runBlocking {
            val created = projectRepository.createProject("Beat Edit", AspectRatioPreset.RATIO_9_16, FrameRatePreset.FPS_30)
            val asset = MediaAsset("a1", "content://test/beat.mp4", "beat.mp4", MediaType.VIDEO, 2_000_000L, 1080, 1920)
            projectRepository.saveProject(AddAssetClipCommand(asset, "c1").apply(created, 1000L))
            created.id
        }

        launchPresets()

        // Focus 1 Frames category -> open 1F Strobe Impact preview -> Save + Apply
        composeRule.onNodeWithTag("preset_tab_ONE_FRAME").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preset_card_builtin_1f_strobe_flash").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("preset_detail_modal").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_save_button").performClick()
        waitUntilCondition { "builtin_1f_strobe_flash" in presetsVm.uiState.value.savedIds }

        composeRule.onNodeWithTag("apply_preset_to_$projectId").performClick()
        waitUntilCondition {
            runBlocking {
                val clip = projectRepository.loadProject(projectId)?.primaryVideoTrack()?.clips?.firstOrNull()
                clip?.keyframes?.containsKey(AnimatableProperty.SCALE_X) == true
            }
        }
    }

    @Test
    fun `validated community preset shows Free Download and saves real json file to EXCEVO Library`() {
        launchPresets()

        // Validate & load community preset JSON
        composeRule.onNodeWithTag("validate_preset_json_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_validate_preset_button").performClick()
        waitUntilCondition { (presetsVm.uiState.value.categoryCounts[PresetTab.COMMUNITY] ?: 0) == 1 }

        // Open From the Community tab & open detail modal
        composeRule.onNodeWithTag("preset_tab_COMMUNITY").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preset_card_custom-preset").performClick()
        composeRule.waitForIdle()

        // Tap Free Download (strictly free, no paywall)
        composeRule.onNodeWithTag("detail_free_download_button").assertIsDisplayed().performClick()
        waitUntilCondition {
            val hasSaved = "custom-preset" in presetsVm.uiState.value.savedIds
            val hasLibrary = runBlocking {
                db.libraryDao().observeAll().first().any { it.name.endsWith(".excevo.json") }
            }
            hasSaved && hasLibrary
        }

        // Saved + Library both updated cleanly
        assertTrue("custom-preset" in presetsVm.uiState.value.savedIds)
        assertEquals(0, composeRule.onAllNodesWithText("Buy").fetchSemanticsNodes().size)
    }
}
