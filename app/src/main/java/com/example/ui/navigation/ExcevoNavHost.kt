package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.di.AppContainer
import com.example.ui.editor.StudioScreen
import com.example.ui.editor.StudioViewModel
import com.example.ui.presets.PresetsViewModel
import com.example.ui.profile.ProfileViewModel
import com.example.ui.projects.ProjectHubViewModel

object ExcevoRoutes {
    const val MAIN_SHELL = "main_shell"
    const val STUDIO = "studio/{projectId}"
    fun studio(projectId: String): String = "studio/$projectId"
}

@Composable
fun ExcevoNavHost(
    appContainer: AppContainer,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ExcevoRoutes.MAIN_SHELL,
    ) {
        composable(route = ExcevoRoutes.MAIN_SHELL) {
            val hubViewModel: ProjectHubViewModel = viewModel(
                factory = ProjectHubViewModel.Factory(
                    projectStorage = appContainer.projectStorage,
                    preferencesRepository = appContainer.userPreferences,
                    mediaProbe = appContainer.mediaProbe,
                    mediaLibraryRepository = appContainer.mediaLibraryRepository,
                ),
            )
            val presetsViewModel: PresetsViewModel = viewModel(
                factory = PresetsViewModel.Factory(
                    presetRepository = appContainer.localPresetRepository,
                    projectStorage = appContainer.projectStorage,
                ),
            )
            val profileViewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModel.Factory(
                    preferencesRepository = appContainer.userPreferences,
                    storageManager = appContainer.storageManager,
                    projectStorage = appContainer.projectStorage,
                    presetRepository = appContainer.localPresetRepository,
                ),
            )
            MainShellScreen(
                hubViewModel = hubViewModel,
                presetsViewModel = presetsViewModel,
                profileViewModel = profileViewModel,
                onOpenProject = { projectId ->
                    navController.navigate(ExcevoRoutes.studio(projectId))
                },
            )
        }

        composable(
            route = ExcevoRoutes.STUDIO,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val playbackPort = androidx.compose.runtime.remember(projectId) {
                appContainer.createSequencePlaybackController()
            }
            val studioViewModel: StudioViewModel = viewModel(
                key = "studio_$projectId",
                factory = StudioViewModel.Factory(
                    projectId = projectId,
                    projectStorage = appContainer.projectStorage,
                    mediaProbe = appContainer.mediaProbe,
                    presetRepository = appContainer.localPresetRepository,
                    playbackPort = playbackPort,
                    mediaLibraryRepository = appContainer.mediaLibraryRepository,
                    userPreferencesRepository = appContainer.userPreferences,
                ),
            )
            StudioScreen(
                viewModel = studioViewModel,
                onBackToHub = { navController.popBackStack() },
            )
        }
    }
}
