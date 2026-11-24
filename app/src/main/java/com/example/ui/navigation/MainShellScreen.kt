package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ports.CrossPlatformDesignTokens
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.NewCompositionBottomSheet
import com.example.ui.home.HomeScreen
import com.example.ui.media.MediaLibrarySheet
import com.example.ui.media.MediaLibraryTab
import com.example.ui.presets.PresetsScreen
import com.example.ui.presets.PresetsViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ProfileViewModel
import com.example.ui.projects.ProjectHubViewModel
import com.example.ui.projects.ProjectsScreen
import com.example.ui.theme.ExcevoColors

enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    PROJECTS(R.string.nav_projects, Icons.Filled.Folder, Icons.Outlined.Folder),
    PRESETS(R.string.nav_presets, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainShellScreen(
    hubViewModel: ProjectHubViewModel,
    presetsViewModel: PresetsViewModel,
    profileViewModel: ProfileViewModel,
    onOpenProject: (String) -> Unit,
) {
    val hubUiState by hubViewModel.uiState.collectAsStateWithLifecycle()
    var activeTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showNewCompositionSheet by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showMediaLibrarySheet by remember { mutableStateOf(false) }
    var childModalOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val dockController = rememberFloatingDockController()
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardOpen = imeBottomPx > 0
    val isAnyModalOpen = showNewCompositionSheet || showCreateProjectDialog || showMediaLibrarySheet || childModalOpen

    LaunchedEffect(isKeyboardOpen) {
        dockController.onKeyboardOpenChanged(isKeyboardOpen)
    }

    LaunchedEffect(isAnyModalOpen) {
        dockController.onModalActiveChanged(isAnyModalOpen)
    }

    LaunchedEffect(hubUiState.errorMessage) {
        val err = hubUiState.errorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            hubViewModel.dismissError()
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            showNewCompositionSheet = false
            hubViewModel.createProjectWithMedia(
                uriStrings = listOf(uri.toString()),
                defaultTitle = "Video Import",
                onCreated = onOpenProject,
            )
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
    ) { uris ->
        if (uris.isNotEmpty()) {
            showNewCompositionSheet = false
            hubViewModel.createProjectWithMedia(
                uriStrings = uris.map { it.toString() },
                defaultTitle = "Photo Sequence",
                onCreated = onOpenProject,
            )
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            showNewCompositionSheet = false
            hubViewModel.createProjectWithMedia(
                uriStrings = uris.map { it.toString() },
                defaultTitle = "Audio Project",
                onCreated = onOpenProject,
            )
        }
    }

    BackHandler(enabled = activeTab != MainTab.HOME && !isAnyModalOpen) {
        activeTab = MainTab.HOME
        dockController.onTabChanged()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val useRail = maxWidth >= 720.dp

            if (useRail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    NavigationRail(
                        containerColor = ExcevoColors.StudioPanel,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .testTag("excevo_navigation_rail"),
                        header = {
                            FloatingActionButton(
                                onClick = { showNewCompositionSheet = true },
                                containerColor = ExcevoColors.AccentPurple,
                                contentColor = ExcevoColors.TextPrimary,
                                shape = CircleShape,
                                modifier = Modifier.testTag("rail_new_composition_fab"),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "New Composition")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        },
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    activeTab = tab
                                    dockController.onTabChanged()
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label,
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ExcevoColors.AccentLavender,
                                    selectedTextColor = ExcevoColors.AccentLavender,
                                    indicatorColor = ExcevoColors.AccentPurpleContainer,
                                    unselectedIconColor = ExcevoColors.TextSecondary,
                                    unselectedTextColor = ExcevoColors.TextSecondary,
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name}"),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(ExcevoColors.HairlineBorder),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding(),
                    ) {
                        MainTabContent(
                            activeTab = activeTab,
                            hubViewModel = hubViewModel,
                            presetsViewModel = presetsViewModel,
                            profileViewModel = profileViewModel,
                            bottomContentPadding = 24.dp,
                            onAtTopChanged = dockController::onAtTopChanged,
                            onChildModalChanged = { childModalOpen = it },
                            onOpenProject = onOpenProject,
                            onSelectTab = {
                                activeTab = it
                                dockController.onTabChanged()
                            },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .nestedScroll(dockController.nestedScrollConnection),
                ) {
                    MainTabContent(
                        activeTab = activeTab,
                        hubViewModel = hubViewModel,
                        presetsViewModel = presetsViewModel,
                        profileViewModel = profileViewModel,
                        bottomContentPadding = CrossPlatformDesignTokens.DOCK_BOTTOM_CLEARANCE_DP.dp,
                        onAtTopChanged = dockController::onAtTopChanged,
                        onChildModalChanged = { childModalOpen = it },
                        onOpenProject = onOpenProject,
                        onSelectTab = {
                            activeTab = it
                            dockController.onTabChanged()
                        },
                    )

                    ExcevoFloatingDock(
                        activeTab = activeTab,
                        visibilityState = dockController.state,
                        onSelectTab = { tab ->
                            activeTab = tab
                            dockController.onTabChanged()
                        },
                        onCenterPlusClick = { showNewCompositionSheet = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        NewCompositionBottomSheet(
            visible = showNewCompositionSheet,
            isBusy = hubUiState.isCreatingComposition,
            onDismiss = { showNewCompositionSheet = false },
            onNewProject = {
                showNewCompositionSheet = false
                showCreateProjectDialog = true
            },
            onImportVideo = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onImportImages = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onImportAudio = {
                audioPickerLauncher.launch(arrayOf("audio/*"))
            },
            onBrowseMediaLibrary = {
                showNewCompositionSheet = false
                showMediaLibrarySheet = true
            },
            onBlankProject = {
                showNewCompositionSheet = false
                hubViewModel.createBlankProject(onOpenProject)
            },
        )

        if (showCreateProjectDialog) {
            CreateProjectDialog(
                onDismiss = { showCreateProjectDialog = false },
                onCreate = { req ->
                    showCreateProjectDialog = false
                    hubViewModel.createProject(
                        name = req.name,
                        aspectRatio = req.aspectRatio,
                        frameRate = req.frameRate,
                        targetResolution = req.targetResolution,
                        scaleMode = req.scaleMode,
                        backgroundId = req.backgroundId,
                        customWidthPx = req.customWidthPx,
                        customHeightPx = req.customHeightPx,
                        onCreated = onOpenProject,
                    )
                },
                onImportVideoElement = {
                    showCreateProjectDialog = false
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onImportImagesElement = {
                    showCreateProjectDialog = false
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onImportAudioElement = {
                    showCreateProjectDialog = false
                    audioPickerLauncher.launch(arrayOf("audio/*"))
                },
            )
        }

        MediaLibrarySheet(
            visible = showMediaLibrarySheet,
            initialTab = MediaLibraryTab.RECENT,
            mediaLibraryRepository = hubViewModel.mediaLibraryRepository,
            isImporting = hubUiState.isCreatingComposition,
            errorMessage = hubUiState.errorMessage,
            onDismiss = { showMediaLibrarySheet = false },
            onImportUris = { uris ->
                showMediaLibrarySheet = false
                hubViewModel.createProjectWithMedia(
                    uriStrings = uris,
                    defaultTitle = "Media Sequence",
                    onCreated = onOpenProject,
                )
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = CrossPlatformDesignTokens.DOCK_BOTTOM_CLEARANCE_DP.dp),
        )
    }
}

@Composable
private fun MainTabContent(
    activeTab: MainTab,
    hubViewModel: ProjectHubViewModel,
    presetsViewModel: PresetsViewModel,
    profileViewModel: ProfileViewModel,
    bottomContentPadding: Dp,
    onAtTopChanged: (Boolean) -> Unit,
    onChildModalChanged: (Boolean) -> Unit,
    onOpenProject: (String) -> Unit,
    onSelectTab: (MainTab) -> Unit,
) {
    when (activeTab) {
        MainTab.HOME -> HomeScreen(
            viewModel = hubViewModel,
            bottomContentPadding = bottomContentPadding,
            onAtTopChanged = onAtTopChanged,
            onModalVisibilityChanged = onChildModalChanged,
            onOpenProject = onOpenProject,
            onNavigateToProjects = { onSelectTab(MainTab.PROJECTS) },
            onNavigateToPresets = { onSelectTab(MainTab.PRESETS) },
        )
        MainTab.PROJECTS -> ProjectsScreen(
            viewModel = hubViewModel,
            bottomContentPadding = bottomContentPadding,
            onAtTopChanged = onAtTopChanged,
            onModalVisibilityChanged = onChildModalChanged,
            onOpenProject = onOpenProject,
        )
        MainTab.PRESETS -> PresetsScreen(
            viewModel = presetsViewModel,
            bottomContentPadding = bottomContentPadding,
            onAtTopChanged = onAtTopChanged,
            onModalVisibilityChanged = onChildModalChanged,
            onOpenProject = onOpenProject,
        )
        MainTab.PROFILE -> ProfileScreen(
            viewModel = profileViewModel,
            bottomContentPadding = bottomContentPadding,
            onAtTopChanged = onAtTopChanged,
            onModalVisibilityChanged = onChildModalChanged,
        )
    }
}
