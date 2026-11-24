package com.example.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.engine.TimelineMath
import com.example.core.model.AspectRatioPreset
import com.example.core.model.CanvasBackgroundSpec
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.WorkspacePreset
import com.example.ui.media.MediaLibrarySheet
import com.example.ui.media.MediaLibraryTab
import com.example.ui.theme.ExcevoColors

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onBackToHub: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showTrimDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMediaLibrary by remember { mutableStateOf(false) }
    var showWorkspaceDialog by remember { mutableStateOf(false) }
    var showExportPreflightDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onHostPause()
                Lifecycle.Event.ON_RESUME -> viewModel.onHostResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.attachVideoTextureView(null)
        }
    }

    BackHandler(enabled = uiState.focusedLayerPanel != null || uiState.isLayerMenuVisible) {
        if (uiState.focusedLayerPanel != null) {
            viewModel.navigateBackFromLayerPanel()
        } else {
            viewModel.closeLayerMenu()
        }
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessages()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val project = uiState.project
            StudioTopBar(
                projectName = project?.name ?: "Loading...",
                workspacePreset = uiState.workspacePreset,
                showSafeGuides = uiState.showSafeGuides,
                onBack = onBackToHub,
                onSave = viewModel::forceSaveProject,
                onToggleSafeGuides = viewModel::toggleSafeGuides,
                onOpenSettings = { showSettingsDialog = true },
                onOpenWorkspaceSwitcher = { showWorkspaceDialog = true },
                onOpenExportPreflight = { showExportPreflightDialog = true },
            )
        },
        bottomBar = {
            if (!uiState.isCleanPreview) {
                StudioBottomToolbox(
                    activeTab = uiState.activeToolTab,
                    workspacePreset = uiState.workspacePreset,
                    selectedClip = uiState.selectedClip,
                    isLayerMenuVisible = uiState.isLayerMenuVisible,
                    focusedLayerPanel = uiState.focusedLayerPanel,
                    tracks = uiState.project?.tracks.orEmpty(),
                    playheadUs = uiState.playheadUs,
                    scaleMode = uiState.project?.scaleMode ?: CanvasScaleMode.FIT,
                    isImporting = uiState.isImporting,
                    presetsCatalog = uiState.presetsCatalog,
                    onSelectTab = viewModel::selectToolTab,
                    onOpenLayerPanel = viewModel::openLayerEditingPanel,
                    onBackFromLayerPanel = viewModel::navigateBackFromLayerPanel,
                    onCloseLayerMenu = viewModel::closeLayerMenu,
                    onToggleTrackVisibility = viewModel::toggleTrackVisibility,
                    onResetClipAll = viewModel::resetSelectedClipAll,
                    onToggleGroup = viewModel::toggleGroupSelectedClip,
                    onImport = { showMediaLibrary = true },
                    onSplit = viewModel::splitSelectedClipAtPlayhead,
                    onTrim = { showTrimDialog = true },
                    onDuplicate = viewModel::duplicateSelectedClip,
                    onMoveEarlier = { viewModel.moveSelectedClip(-1) },
                    onMoveLater = { viewModel.moveSelectedClip(1) },
                    onMoveToTrack = viewModel::moveSelectedClipToTrack,
                    onDelete = viewModel::deleteSelectedClip,
                    onOpenSettings = { showSettingsDialog = true },
                    onPreviewColorGrade = viewModel::previewColorGrade,
                    onCommitColorGrade = viewModel::commitColorGrade,
                    onResetSingleAdjust = viewModel::resetSingleColorParam,
                    onResetAllAdjust = viewModel::resetAllAdjustments,
                    onResetSingleEffect = viewModel::resetSingleEffectParam,
                    onResetAllEffects = viewModel::resetVisualEffects,
                    onAddEffectToStack = viewModel::addEffectToStack,
                    onToggleEffectEnabled = viewModel::toggleEffectEnabled,
                    onMoveEffectInStack = viewModel::moveEffectInStack,
                    onRemoveEffectFromStack = viewModel::removeEffectFromStack,
                    onPreviewEffectStack = viewModel::previewEffectStack,
                    onCommitEffectStack = viewModel::commitEffectStack,
                    onApplyFilter = viewModel::applyColorFilter,
                    onPreviewTransform = viewModel::previewTransform,
                    onCommitTransform = viewModel::commitTransform,
                    onRotate90 = viewModel::rotateClip90Deg,
                    onResetTransform = viewModel::resetTransform,
                    onSetScaleMode = viewModel::setCanvasScaleMode,
                    onSetSpeed = viewModel::setClipSpeed,
                    onSetClipBlend = viewModel::setClipBlend,
                    onSetAudioEnvelope = viewModel::setClipAudioEnvelope,
                    onToggleKeyframe = viewModel::toggleKeyframeAtPlayhead,
                    onRemoveKeyframe = viewModel::removeKeyframe,
                    onJumpKeyframe = viewModel::jumpToAdjacentKeyframe,
                    onSeekToUs = viewModel::seekToUs,
                    onSetKeyframeEasing = viewModel::setKeyframeEasing,
                    onApplyPresetToClip = viewModel::applyPresetToSelectedClip,
                    onSaveCustomPreset = viewModel::saveSelectedClipAsCustomPreset,
                    onToggleMute = {
                        val clip = uiState.selectedClip ?: return@StudioBottomToolbox
                        viewModel.setClipAudioEnvelope(clip.volume, !clip.isMuted, clip.fadeInUs, clip.fadeOutUs)
                    },
                    onVolumeChange = { newVolume ->
                        val clip = uiState.selectedClip ?: return@StudioBottomToolbox
                        viewModel.setClipAudioEnvelope(newVolume, newVolume <= 0.01f, clip.fadeInUs, clip.fadeOutUs)
                    },
                )
            }
        },
    ) { innerPadding ->
        val project = uiState.project
        if (uiState.isLoading || project == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ExcevoColors.AccentPurple)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (uiState.isImporting) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            color = ExcevoColors.AccentPurple,
                            trackColor = ExcevoColors.ElevatedSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        uiState.importProgress?.let { prog ->
                            Text(
                                text = if (prog.totalCount > 0) {
                                    "Importing ${prog.currentIndex}/${prog.totalCount}: ${prog.currentLabel}"
                                } else {
                                    prog.currentLabel
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.AccentLavender,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .testTag("studio_import_progress_label"),
                            )
                        }
                    }
                }

                if (uiState.missingAssetNames.isNotEmpty()) {
                    Surface(
                        color = ExcevoColors.DangerRed.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExcevoColors.DangerRed.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("missing_assets_banner"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = "Missing media",
                                tint = ExcevoColors.DangerRed,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Unavailable source media: ${uiState.missingAssetNames.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = viewModel::dismissMissingAssetsBanner,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("dismiss_missing_assets_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss missing media notice",
                                    tint = ExcevoColors.TextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                StudioPreviewStage(
                    project = project,
                    activeClip = uiState.activePrimaryClip,
                    activeOverlayClips = uiState.activeOverlayClips,
                    activeAsset = uiState.activePrimaryClip?.let { project.assets[it.assetId] },
                    playheadUs = uiState.playheadUs,
                    hasLiveTransformOverride = uiState.liveTransformOverride != null,
                    liveOverrideClipId = uiState.selectedClipId,
                    showSafeGuides = uiState.showSafeGuides,
                    isCleanPreview = uiState.isCleanPreview,
                    isBuffering = uiState.isBuffering,
                    isUsingGpuEffects = uiState.isUsingGpuEffects,
                    gpuCapabilities = uiState.gpuCapabilities,
                    playbackError = uiState.playbackError,
                    hasPlaybackController = uiState.gpuCapabilities.useHardwareGpuPipeline,
                    onAttachTextureView = viewModel::attachVideoTextureView,
                    onToggleScaleMode = {
                        val next = if (project.scaleMode == CanvasScaleMode.FIT) {
                            CanvasScaleMode.FILL
                        } else {
                            CanvasScaleMode.FIT
                        }
                        viewModel.setCanvasScaleMode(next)
                    },
                    onPreviewTransform = viewModel::previewTransform,
                    onCommitTransform = { viewModel.commitTransform(it, "Drag reposition") },
                    onDismissError = viewModel::dismissMessages,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (uiState.isCleanPreview) 1f else 0.30f)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                )

                // CRITICAL ALIGNMENT: Unified Transport Bar with Play/Pause strictly dead-center (50%)
                // aligned identically with the continuous RED Playhead vertical axis below it.
                UnifiedTransportBar(
                    playheadUs = uiState.playheadUs,
                    totalDurationUs = project.totalDurationUs(),
                    fps = project.frameRate.fps,
                    isPlaying = uiState.isPlaying,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    isCleanPreview = uiState.isCleanPreview,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onStepBack = { viewModel.stepFrame(-1) },
                    onStepForward = { viewModel.stepFrame(1) },
                    onPrevious = viewModel::jumpToPreviousBoundary,
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::jumpToNextBoundary,
                    onToggleCleanPreview = viewModel::toggleCleanPreview,
                )

                if (!uiState.isCleanPreview) {
                    Spacer(modifier = Modifier.height(2.dp))

                    MultiTrackTimelineArea(
                        project = project,
                        playheadUs = uiState.playheadUs,
                        selectedClipId = uiState.selectedClipId,
                        pxPerSecond = uiState.pxPerSecond,
                        showImportFab = !uiState.isLayerMenuVisible && uiState.focusedLayerPanel == null,
                        onSeekUs = viewModel::seekToUs,
                        onSelectClip = viewModel::openLayerMenuForClip,
                        onZoomChange = viewModel::setZoom,
                        onAddTrack = viewModel::addTrack,
                        onRemoveTrack = viewModel::removeTrack,
                        onToggleTrackVisibility = viewModel::toggleTrackVisibility,
                        onImportMedia = { showMediaLibrary = true },
                        onTrimClipDrag = { clipId, startUs, endUs ->
                            viewModel.trimClipById(clipId, startUs, endUs)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.70f),
                    )
                }
            }
        }
    }

    val selectedClip = uiState.selectedClip
    if (showTrimDialog && selectedClip != null) {
        TrimClipDialog(
            clip = selectedClip,
            onDismiss = { showTrimDialog = false },
            onApply = { newStart, newEnd ->
                viewModel.trimSelectedClip(newStart, newEnd)
                showTrimDialog = false
            },
        )
    }

    val currentProject = uiState.project
    if (showSettingsDialog && currentProject != null) {
        SequenceSettingsDialog(
            project = currentProject,
            onDismiss = { showSettingsDialog = false },
            onApply = { newName, newAspect, newFps, newRes, newScaleMode, newBgId, customW, customH, keepCustom ->
                viewModel.updateProjectSettings(
                    name = newName,
                    aspectRatio = newAspect,
                    frameRate = newFps,
                    targetResolution = newRes,
                    scaleMode = newScaleMode,
                    backgroundId = newBgId,
                    customWidthPx = customW,
                    customHeightPx = customH,
                    keepCustomResolution = keepCustom,
                )
                showSettingsDialog = false
            },
        )
    }

    if (showWorkspaceDialog) {
        WorkspaceSwitcherDialog(
            activePreset = uiState.workspacePreset,
            onSelect = { preset ->
                viewModel.setWorkspacePreset(preset)
                showWorkspaceDialog = false
            },
            onDismiss = { showWorkspaceDialog = false },
        )
    }

    if (showExportPreflightDialog && currentProject != null) {
        ExportPreflightDialog(
            project = currentProject,
            missingAssets = uiState.missingAssetNames,
            onOpenSettings = {
                showExportPreflightDialog = false
                showSettingsDialog = true
            },
            onDismiss = { showExportPreflightDialog = false },
        )
    }

    MediaLibrarySheet(
        visible = showMediaLibrary,
        initialTab = MediaLibraryTab.RECENT,
        mediaLibraryRepository = viewModel.mediaLibraryRepository,
        isImporting = uiState.isImporting,
        importProgress = uiState.importProgress,
        errorMessage = uiState.errorMessage,
        onDismiss = { showMediaLibrary = false },
        onImportUris = { uris ->
            showMediaLibrary = false
            viewModel.importMediaUris(uris)
        },
    )
}

@Composable
private fun StudioTopBar(
    projectName: String,
    workspacePreset: WorkspacePreset,
    showSafeGuides: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onToggleSafeGuides: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorkspaceSwitcher: () -> Unit,
    onOpenExportPreflight: () -> Unit,
) {
    Surface(color = ExcevoColors.VoidCharcoal) {
        Column {
            // Single sleek studio header matching the private UI (Screenshots 1 & 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("studio_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to Project Hub",
                        tint = ExcevoColors.TextPrimary,
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ExcevoColors.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Sequence Settings",
                        tint = ExcevoColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                IconButton(
                    onClick = onToggleSafeGuides,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("toggle_safe_guides_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridOn,
                        contentDescription = stringResource(R.string.safe_guides),
                        tint = if (showSafeGuides) ExcevoColors.AccentLavender else ExcevoColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onOpenWorkspaceSwitcher,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("workspace_switcher_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DashboardCustomize,
                        contentDescription = "Switch Workspace (${workspacePreset.label})",
                        tint = ExcevoColors.AccentLavender,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("studio_save_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = "Save Sequence",
                        tint = ExcevoColors.SuccessEmerald,
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("studio_settings_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Sequence Settings",
                        tint = ExcevoColors.TextPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }

                IconButton(
                    onClick = onOpenExportPreflight,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("export_preflight_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = "Export Pre-Flight",
                        tint = ExcevoColors.TextPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ExcevoColors.HairlineBorder),
            )
        }
    }
}

/**
 * Private-UI Unified Transport Strip + Centered Timecode Pill:
 * - Row 1: Left Undo/Redo | Dead-Center Prev / Play / Next | Right Frame-Step & Clean Preview
 * - Row 2: Left 00:00:00 | Centered Dark-Purple Timecode Pill aligned with Playhead | Right total duration
 */
@Composable
private fun UnifiedTransportBar(
    playheadUs: Long,
    totalDurationUs: Long,
    fps: Int,
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isCleanPreview: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onToggleCleanPreview: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExcevoColors.StudioPanel),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("undo_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = stringResource(R.string.undo),
                        tint = if (canUndo) ExcevoColors.TextPrimary else ExcevoColors.TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("redo_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Redo,
                        contentDescription = stringResource(R.string.redo),
                        tint = if (canRedo) ExcevoColors.TextPrimary else ExcevoColors.TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("transport_previous_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "Previous clip or keyframe",
                        tint = ExcevoColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Surface(
                    color = ExcevoColors.AccentPurple,
                    shape = CircleShape,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlay)
                        .testTag("play_pause_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = ExcevoColors.TextPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("transport_next_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Next clip or keyframe",
                        tint = ExcevoColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onStepBack,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("step_back_button"),
                ) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Step back 1 frame", tint = ExcevoColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onStepForward,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("step_forward_button"),
                ) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Step forward 1 frame", tint = ExcevoColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onToggleCleanPreview,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("clean_preview_button"),
                ) {
                    Icon(
                        imageVector = if (isCleanPreview) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = "Clean Preview Mode",
                        tint = if (isCleanPreview) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Centered Dark-Purple Timecode Pill aligned under Play button (Screenshot 1 & 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ExcevoColors.VoidCharcoal)
                .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Text(
                text = "00:00:00",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.TextMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Surface(
                color = ExcevoColors.TileCardSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.5f)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    text = "${TimelineMath.formatTimecode(playheadUs, fps)} / ${TimelineMath.formatTimecode(totalDurationUs, fps)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = ExcevoColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .testTag("transport_timecode"),
                )
            }
            Text(
                text = TimelineMath.formatTimecode(totalDurationUs, fps),
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.TextMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun WorkspaceSwitcherDialog(
    activePreset: WorkspacePreset,
    onSelect: (WorkspacePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = { Text("Select Studio Workspace", color = ExcevoColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Reorders editing drawers only. Project tracks, keyframes, and clips stay intact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExcevoColors.TextSecondary,
                )
                WorkspacePreset.entries.forEach { preset ->
                    val selected = preset == activePreset
                    Surface(
                        color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(preset) }
                            .testTag("workspace_option_${preset.name}"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ExcevoColors.AccentLavender)
            }
        },
    )
}

@Composable
private fun ExportPreflightDialog(
    project: TimelineProject,
    missingAssets: List<String>,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dims = project.effectiveDimensions()
    val clipCount = project.clipCount()
    val ready = clipCount > 0 && missingAssets.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        modifier = Modifier.testTag("export_preflight_dialog"),
        title = { Text("Sequence Pre-Flight", color = ExcevoColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${project.name} · ${dims.first}×${dims.second} @ ${project.frameRate.fps}fps",
                            style = MaterialTheme.typography.labelLarge,
                            color = ExcevoColors.AccentLavender,
                        )
                        Text(
                            text = "${project.tracks.size} Tracks · $clipCount Clips · ${TimelineMath.formatDurationCompact(project.totalDurationUs())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExcevoColors.TextPrimary,
                        )
                        Text(
                            text = if (ready) "All media URIs & sequence timelines verified ready." else "Resolve empty timeline or missing media before encoding.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ready) ExcevoColors.SuccessEmerald else ExcevoColors.PlayheadAmber,
                        )
                    }
                }
                Text(
                    text = "Phase 3 Status: Non-destructive edits, effects, keyframes, and project JSON are saved locally. Full Media3 Transformer MP4/HEVC encoding & muxing is scheduled for Phase 4.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExcevoColors.TextSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
            ) {
                Text("Canvas Specs")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_preflight_close"),
            ) {
                Text("Close", color = ExcevoColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun TrimClipDialog(
    clip: TimelineClip,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit,
) {
    val totalMax = clip.sourceTotalDurationUs.coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_US).toFloat()
    var range by remember(clip.id) {
        mutableStateOf(
            clip.sourceStartUs.toFloat()..clip.sourceEndUs.toFloat().coerceAtMost(totalMax)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Text(
                text = "Trim · ${clip.label}",
                style = MaterialTheme.typography.titleLarge,
                color = ExcevoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val startUs = range.start.toLong()
                val endUs = range.endInclusive.toLong().coerceAtLeast(startUs + TimelineMath.MIN_CLIP_DURATION_US)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("In: ${TimelineMath.formatDurationCompact(startUs)}", style = MaterialTheme.typography.labelMedium, color = ExcevoColors.AccentLavender)
                    Text("Dur: ${TimelineMath.formatDurationCompact(endUs - startUs)}", style = MaterialTheme.typography.labelMedium, color = ExcevoColors.PlayheadAmber)
                    Text("Out: ${TimelineMath.formatDurationCompact(endUs)}", style = MaterialTheme.typography.labelMedium, color = ExcevoColors.AccentLavender)
                }
                RangeSlider(
                    value = range,
                    onValueChange = { updated ->
                        if (updated.endInclusive - updated.start >= TimelineMath.MIN_CLIP_DURATION_US) {
                            range = updated
                        }
                    },
                    valueRange = 0f..totalMax,
                    colors = SliderDefaults.colors(
                        thumbColor = ExcevoColors.AccentPurple,
                        activeTrackColor = ExcevoColors.AccentPurple,
                        inactiveTrackColor = ExcevoColors.HairlineBorder,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startUs = range.start.toLong()
                    val endUs = range.endInclusive.toLong().coerceAtLeast(startUs + TimelineMath.MIN_CLIP_DURATION_US)
                    onApply(startUs, endUs)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple, contentColor = ExcevoColors.TextPrimary),
                modifier = Modifier.testTag("confirm_trim_button"),
            ) {
                Text("Apply Trim")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcevoColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun SequenceSettingsDialog(
    project: TimelineProject,
    onDismiss: () -> Unit,
    onApply: (
        String,
        AspectRatioPreset,
        FrameRatePreset,
        ExportResolutionPreset,
        CanvasScaleMode,
        String,
        Int?,
        Int?,
        Boolean,
    ) -> Unit,
) {
    var name by remember(project.id) { mutableStateOf(project.name) }
    var aspect by remember(project.id) { mutableStateOf(project.aspectRatio) }
    var fps by remember(project.id) { mutableStateOf(project.frameRate) }
    var resolution by remember(project.id) { mutableStateOf(project.targetResolution) }
    var scaleMode by remember(project.id) { mutableStateOf(project.scaleMode) }
    var backgroundId by remember(project.id) { mutableStateOf(project.backgroundId) }
    var useCustom by remember(project.id) {
        mutableStateOf(project.customWidthPx != null && project.customHeightPx != null)
    }
    var customW by remember(project.id) { mutableStateOf((project.customWidthPx ?: 1080).toString()) }
    var customH by remember(project.id) { mutableStateOf((project.customHeightPx ?: 1920).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Crop, contentDescription = null, tint = ExcevoColors.AccentLavender)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sequence Canvas", color = ExcevoColors.TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.project_name_label)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExcevoColors.AccentPurple,
                        unfocusedBorderColor = ExcevoColors.HairlineBorder,
                        focusedTextColor = ExcevoColors.TextPrimary,
                        unfocusedTextColor = ExcevoColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AspectRatioPreset.entries.forEach { preset ->
                        val selected = preset == aspect
                        Surface(
                            color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { aspect = preset },
                        ) {
                            Text(
                                text = preset.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CanvasScaleMode.entries.forEach { mode ->
                        val selected = mode == scaleMode
                        Surface(
                            color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { scaleMode = mode },
                        ) {
                            Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text("Scale: ${mode.label}", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CanvasBackgroundSpec.entries.forEach { bg ->
                        val selected = bg.id == backgroundId
                        Surface(
                            color = Color(bg.colorArgb),
                            shape = CircleShape,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                            modifier = Modifier.size(30.dp).clip(CircleShape).clickable { backgroundId = bg.id }.testTag("settings_bg_${bg.id}"),
                        ) {}
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Custom Resolution", style = MaterialTheme.typography.labelMedium, color = ExcevoColors.TextPrimary)
                    Switch(checked = useCustom, onCheckedChange = { useCustom = it })
                }
                if (useCustom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customW,
                            onValueChange = { customW = it.filter(Char::isDigit).take(4) },
                            label = { Text("Width") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = customH,
                            onValueChange = { customH = it.filter(Char::isDigit).take(4) },
                            label = { Text("Height") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExportResolutionPreset.entries.forEach { res ->
                            val selected = res == resolution
                            Surface(
                                color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { resolution = res },
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(res.shortLabel, style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrameRatePreset.entries.forEach { preset ->
                        val selected = preset == fps
                        Surface(
                            color = if (selected) ExcevoColors.PlayheadAmberContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) ExcevoColors.PlayheadAmber else ExcevoColors.HairlineBorder),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { fps = preset },
                        ) {
                            Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text(preset.label, style = MaterialTheme.typography.labelMedium, color = if (selected) ExcevoColors.PlayheadAmber else ExcevoColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cw = if (useCustom) customW.toIntOrNull()?.coerceIn(240, 4096) else null
                    val ch = if (useCustom) customH.toIntOrNull()?.coerceIn(240, 4096) else null
                    onApply(name, aspect, fps, resolution, scaleMode, backgroundId, cw, ch, useCustom)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple, contentColor = ExcevoColors.TextPrimary),
                modifier = Modifier.testTag("confirm_sequence_settings_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcevoColors.TextSecondary)
            }
        },
    )
}
