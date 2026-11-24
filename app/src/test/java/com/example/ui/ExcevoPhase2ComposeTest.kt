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
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.ThemeIntensity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.viewModelScope
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExcevoPhase2ComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var projectRepository: LocalProjectRepository
    private lateinit var presetRepository: LocalPresetRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var storageManager: StorageManager
    private lateinit var presetsVm: PresetsViewModel
    private lateinit var profileVm: ProfileViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExcevoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = LocalProjectRepository(context, db.projectDao())
        presetRepository = LocalPresetRepository(db.savedPresetDao(), projectRepository)
        preferencesRepository = UserPreferencesRepository(context)
        storageManager = StorageManager(context, db.libraryDao())
    }

    @After
    fun tearDown() {
        if (::presetsVm.isInitialized) presetsVm.viewModelScope.cancel()
        if (::profileVm.isInitialized) profileVm.viewModelScope.cancel()
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
        assertTrue("Timed out after ${timeoutMs}ms waiting for condition", condition())
    }

    private fun setShellContent() {
        val hubVm = ProjectHubViewModel(projectRepository, preferencesRepository)
        presetsVm = PresetsViewModel(presetRepository, projectRepository)
        profileVm = ProfileViewModel(preferencesRepository, storageManager, projectRepository, presetRepository)

        composeRule.setContent {
            ExcevoTheme {
                MainShellScreen(
                    hubViewModel = hubVm,
                    presetsViewModel = presetsVm,
                    profileViewModel = profileVm,
                    onOpenProject = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `bottom navigation switches across Home, Projects, Presets, and Profile and runs AI Lab diagnostics`() {
        runBlocking {
            val created = projectRepository.createProject("Launch Cut", AspectRatioPreset.RATIO_9_16, FrameRatePreset.FPS_30)
            val asset = MediaAsset("a1", "content://test/a.mp4", "a.mp4", MediaType.VIDEO, 2_000_000L, 1080, 1920)
            projectRepository.saveProject(AddAssetClipCommand(asset, "c1").apply(created, 2000L))
        }

        setShellContent()

        // Home screen visible initially
        composeRule.onNodeWithTag("home_new_composition_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_ai_lab_card").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Active Tool: Deterministic Sequence Pacing & Framing Analyzer").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        // Projects tab
        composeRule.onNodeWithTag("nav_tab_PROJECTS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("projects_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("projects_view_mode_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("projects_list").assertIsDisplayed()

        // Presets tab
        composeRule.onNodeWithTag("nav_tab_PRESETS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("presets_list").assertIsDisplayed()

        // Profile tab
        composeRule.onNodeWithTag("nav_tab_PROFILE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen_list")
            .performScrollToNode(hasTestTag("profile_clear_cache_button"))
        composeRule.onNodeWithTag("profile_clear_cache_button").assertIsDisplayed()
    }

    @Test
    fun `presets bookmarking, json validation, and applying CC preset to real Room project work`() {
        val projectId = runBlocking {
            val created = projectRepository.createProject("Grade Target", AspectRatioPreset.RATIO_16_9, FrameRatePreset.FPS_24)
            val asset = MediaAsset("a1", "content://test/b.mp4", "b.mp4", MediaType.VIDEO, 3_000_000L, 1920, 1080)
            projectRepository.saveProject(AddAssetClipCommand(asset, "c1").apply(created, 2000L))
            created.id
        }

        setShellContent()
        composeRule.onNodeWithTag("nav_tab_PRESETS").performClick()
        waitUntilCondition { presetsVm.uiState.value.presets.isNotEmpty() }

        // Save built-in shake preset and verify in Saved tab
        composeRule.onNodeWithTag("save_preset_builtin_shake_impact").performClick()
        waitUntilCondition {
            "builtin_shake_impact" in presetsVm.uiState.value.savedIds
        }
        composeRule.onNodeWithTag("preset_tab_SAVED").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Impact Punch Shake").assertIsDisplayed()

        // Switch to CCs tab and apply Violet Noir Cinema directly to projectId
        composeRule.onNodeWithTag("preset_tab_CC").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preset_card_builtin_cc_violet_noir").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apply_preset_to_$projectId").performClick()
        waitUntilCondition {
            runBlocking {
                val reloaded = projectRepository.loadProject(projectId)
                reloaded?.primaryVideoTrack()?.clips?.firstOrNull()?.colorGrade?.contrast == 0.28f
            }
        }

        runBlocking {
            val reloaded = projectRepository.loadProject(projectId)!!
            assertEquals(0.28f, reloaded.primaryVideoTrack().clips.first().colorGrade.contrast, 0.001f)
        }

        // Validate & Load custom JSON into Community tab
        composeRule.onNodeWithTag("validate_preset_json_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_validate_preset_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preset_tab_COMMUNITY").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Custom Purple Punch").assertIsDisplayed()
    }

    @Test
    fun `projects search and aspect filter narrow sequence list accurately`() {
        runBlocking {
            projectRepository.createProject("Alpha Vertical", AspectRatioPreset.RATIO_9_16, FrameRatePreset.FPS_30)
            projectRepository.createProject("Beta Cinema", AspectRatioPreset.RATIO_16_9, FrameRatePreset.FPS_24)
        }

        setShellContent()
        composeRule.onNodeWithTag("nav_tab_PROJECTS").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("filter_chip_16:9").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beta Cinema").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Alpha Vertical").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("filter_chip_All").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("projects_search_input").performTextInput("Alpha")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Alpha Vertical").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Beta Cinema").fetchSemanticsNodes().size)
    }

    @Test
    fun `profile theme toggle persists in DataStore and clear cache removes temp files`() {
        val dummyCacheFile = File(context.cacheDir, "scrub_frame_test.tmp").apply {
            writeText("0123456789")
        }
        assertTrue(dummyCacheFile.exists())

        setShellContent()
        composeRule.onNodeWithTag("nav_tab_PROFILE").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_screen_list")
            .performScrollToNode(hasTestTag("profile_clear_cache_button"))
        composeRule.onNodeWithTag("profile_clear_cache_button").performClick()
        waitUntilCondition { !dummyCacheFile.exists() }
        assertFalse(dummyCacheFile.exists())

        composeRule.onNodeWithTag("profile_screen_list")
            .performScrollToNode(hasTestTag("theme_option_DEEP_OBSIDIAN"))
        composeRule.onNodeWithTag("theme_option_DEEP_OBSIDIAN").performClick()
        waitUntilCondition {
            profileVm.uiState.value.preferences.themeIntensity == ThemeIntensity.DEEP_OBSIDIAN
        }
        assertEquals(ThemeIntensity.DEEP_OBSIDIAN, profileVm.uiState.value.preferences.themeIntensity)
    }

    @Test
    fun `room database MIGRATION_1_2 creates saved_presets and library_items tables cleanly`() {
        val dbFile = File(context.cacheDir, "migration_test_v1.db").apply { delete() }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `aspectRatioId` TEXT NOT NULL, `frameRateFps` INTEGER NOT NULL, `durationUs` INTEGER NOT NULL, `clipCount` INTEGER NOT NULL, `thumbnailPath` TEXT, `documentPath` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sqliteDb = helper.writableDatabase
        ExcevoDatabase.MIGRATION_1_2.migrate(sqliteDb)

        val tables = mutableSetOf<String>()
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        sqliteDb.close()
        helper.close()
        dbFile.delete()

        assertTrue("saved_presets" in tables)
        assertTrue("library_items" in tables)
    }
}
