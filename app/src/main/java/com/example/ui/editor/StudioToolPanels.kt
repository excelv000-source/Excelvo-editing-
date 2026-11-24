package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.engine.ColorFilterCatalog
import com.example.core.model.AnimatableProperty
import com.example.core.model.BlendModeType
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.MediaType
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import com.example.core.model.TransformSpec
import com.example.core.model.VisualEffectType
import com.example.core.model.WorkspacePreset
import com.example.ui.theme.ExcevoColors
import kotlin.math.roundToInt

@Composable
fun StudioBottomToolbox(
    activeTab: StudioToolTab,
    workspacePreset: WorkspacePreset = WorkspacePreset.EXCEVO_STUDIO,
    selectedClip: TimelineClip?,
    isLayerMenuVisible: Boolean = false,
    focusedLayerPanel: LayerMenuPanel? = null,
    tracks: List<TimelineTrack> = emptyList(),
    playheadUs: Long = 0L,
    scaleMode: CanvasScaleMode,
    isImporting: Boolean,
    presetsCatalog: List<PresetDefinition>,
    onSelectTab: (StudioToolTab) -> Unit,
    onOpenLayerPanel: (LayerMenuPanel) -> Unit = {},
    onBackFromLayerPanel: () -> Unit = {},
    onCloseLayerMenu: () -> Unit = {},
    onToggleTrackVisibility: (String) -> Unit = {},
    onResetClipAll: () -> Unit = {},
    onToggleGroup: () -> Unit = {},
    onImport: () -> Unit,
    onSplit: () -> Unit,
    onTrim: () -> Unit,
    onDuplicate: () -> Unit = {},
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onMoveToTrack: (String) -> Unit = {},
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreviewColorGrade: (ColorGradeSpec) -> Unit,
    onCommitColorGrade: (ColorGradeSpec, String) -> Unit,
    onResetSingleAdjust: (ColorAdjustParam) -> Unit,
    onResetAllAdjust: () -> Unit,
    onResetSingleEffect: (VisualEffectParam) -> Unit,
    onResetAllEffects: () -> Unit,
    onAddEffectToStack: (VisualEffectType) -> Unit = {},
    onToggleEffectEnabled: (String) -> Unit = {},
    onMoveEffectInStack: (String, Int) -> Unit = { _, _ -> },
    onRemoveEffectFromStack: (String) -> Unit = {},
    onPreviewEffectStack: (List<ClipEffectInstance>) -> Unit = {},
    onCommitEffectStack: (List<ClipEffectInstance>, String) -> Unit = { _, _ -> },
    onApplyFilter: (String, Float) -> Unit,
    onPreviewTransform: (TransformSpec) -> Unit,
    onCommitTransform: (TransformSpec, String) -> Unit,
    onRotate90: () -> Unit,
    onResetTransform: () -> Unit,
    onSetScaleMode: (CanvasScaleMode) -> Unit,
    onSetSpeed: (Float) -> Unit = {},
    onSetClipBlend: (BlendModeType, Float) -> Unit = { _, _ -> },
    onSetAudioEnvelope: (Float, Boolean, Long, Long) -> Unit = { _, _, _, _ -> },
    onToggleKeyframe: (AnimatableProperty, Float) -> Unit = { _, _ -> },
    onRemoveKeyframe: (AnimatableProperty, String) -> Unit = { _, _ -> },
    onJumpKeyframe: (AnimatableProperty, Boolean) -> Unit = { _, _ -> },
    onSeekToUs: (Long) -> Unit = {},
    onSetKeyframeEasing: (AnimatableProperty, String, EasingType) -> Unit = { _, _, _ -> },
    onApplyPresetToClip: (PresetDefinition, Float) -> Unit,
    onSaveCustomPreset: (String, PresetCategory) -> Unit = { _, _ -> },
    onToggleMute: () -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
) {
    val orderedTabs = remember(workspacePreset) {
        StudioToolTab.orderedForWorkspace(workspacePreset)
    }

    val useSheetShape = isLayerMenuVisible && focusedLayerPanel == null && selectedClip != null
    Surface(
        color = if (useSheetShape) ExcevoColors.SheetObsidian else ExcevoColors.StudioPanel,
        shape = if (useSheetShape) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, if (useSheetShape) ExcevoColors.TileCardBorder else ExcevoColors.HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            when {
                focusedLayerPanel != null && selectedClip != null -> {
                    FocusedLayerPanelHeader(
                        panel = focusedLayerPanel,
                        clip = selectedClip,
                        onBack = onBackFromLayerPanel,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        when (focusedLayerPanel) {
                            LayerMenuPanel.MOVE_TRANSFORM -> MotionTransformPanel(
                                clip = selectedClip,
                                playheadUs = playheadUs,
                                scaleMode = scaleMode,
                                onSetScaleMode = onSetScaleMode,
                                onRotate90 = onRotate90,
                                onResetTransform = onResetTransform,
                                onPreviewTransform = onPreviewTransform,
                                onCommitTransform = onCommitTransform,
                                onSetSpeed = onSetSpeed,
                                onToggleKeyframe = onToggleKeyframe,
                                onJumpKeyframe = onJumpKeyframe,
                                onSetKeyframeEasing = onSetKeyframeEasing,
                            )
                            LayerMenuPanel.EFFECTS -> EffectsStackPanel(
                                clip = selectedClip,
                                onPreviewGrade = onPreviewColorGrade,
                                onCommitGrade = onCommitColorGrade,
                                onResetSingle = onResetSingleEffect,
                                onResetAll = onResetAllEffects,
                                onAddEffectToStack = onAddEffectToStack,
                                onToggleEffectEnabled = onToggleEffectEnabled,
                                onMoveEffectInStack = onMoveEffectInStack,
                                onRemoveEffectFromStack = onRemoveEffectFromStack,
                                onPreviewEffectStack = onPreviewEffectStack,
                                onCommitEffectStack = onCommitEffectStack,
                            )
                            LayerMenuPanel.COLOR_FILL -> ColorFillPanel(
                                clip = selectedClip,
                                onPreviewGrade = onPreviewColorGrade,
                                onCommitGrade = onCommitColorGrade,
                                onResetSingleAdjust = onResetSingleAdjust,
                                onResetAllAdjust = onResetAllAdjust,
                                onApplyFilter = onApplyFilter,
                            )
                            LayerMenuPanel.BLENDING -> BlendingPanel(
                                clip = selectedClip,
                                onPreviewTransform = onPreviewTransform,
                                onCommitBlend = onSetClipBlend,
                                onToggleOpacityKeyframe = onToggleKeyframe,
                            )
                            LayerMenuPanel.OPACITY -> OpacityPanel(
                                clip = selectedClip,
                                playheadUs = playheadUs,
                                onPreviewTransform = onPreviewTransform,
                                onCommitOpacity = { op -> onSetClipBlend(selectedClip.blendMode, op) },
                                onToggleOpacityKeyframe = onToggleKeyframe,
                                onJumpOpacityKeyframe = onJumpKeyframe,
                            )
                            LayerMenuPanel.KEYFRAMES -> KeyframesInspectorPanel(
                                clip = selectedClip,
                                playheadUs = playheadUs,
                                onToggleKeyframe = onToggleKeyframe,
                                onRemoveKeyframe = onRemoveKeyframe,
                                onJumpKeyframe = onJumpKeyframe,
                                onSeekToUs = onSeekToUs,
                                onSetEasing = onSetKeyframeEasing,
                            )
                            LayerMenuPanel.PRESETS -> StudioPresetsDrawer(
                                hasSelectedClip = true,
                                presets = presetsCatalog,
                                onApplyPreset = onApplyPresetToClip,
                                onSaveCustomPreset = onSaveCustomPreset,
                            )
                        }
                    }
                }

                isLayerMenuVisible && selectedClip != null -> {
                    val selectedTrackHidden = tracks.firstOrNull { it.id == selectedClip.trackId }?.isHidden == true
                    LayerEditingMenu(
                        clip = selectedClip,
                        isLayerHidden = selectedTrackHidden,
                        onToggleVisibility = { onToggleTrackVisibility(selectedClip.trackId) },
                        onOpenPanel = onOpenLayerPanel,
                        onDuplicate = onDuplicate,
                        onToggleGroup = onToggleGroup,
                        onSplit = onSplit,
                        onResetClip = onResetClipAll,
                        onDelete = onDelete,
                        onCloseMenu = onCloseLayerMenu,
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        when (activeTab) {
                            StudioToolTab.EDIT -> EditDrawer(
                                selectedClip = selectedClip,
                                tracks = tracks,
                                isImporting = isImporting,
                                onImport = onImport,
                                onSplit = onSplit,
                                onTrim = onTrim,
                                onDuplicate = onDuplicate,
                                onMoveEarlier = onMoveEarlier,
                                onMoveLater = onMoveLater,
                                onMoveToTrack = onMoveToTrack,
                                onDelete = onDelete,
                                onToggleMute = onToggleMute,
                                onVolumeChange = onVolumeChange,
                                onSettings = onOpenSettings,
                            )
                            StudioToolTab.TRANSFORM -> MotionTransformPanel(
                                clip = selectedClip,
                                playheadUs = playheadUs,
                                scaleMode = scaleMode,
                                onSetScaleMode = onSetScaleMode,
                                onRotate90 = onRotate90,
                                onResetTransform = onResetTransform,
                                onPreviewTransform = onPreviewTransform,
                                onCommitTransform = onCommitTransform,
                                onSetSpeed = onSetSpeed,
                                onToggleKeyframe = onToggleKeyframe,
                                onJumpKeyframe = onJumpKeyframe,
                                onSetKeyframeEasing = onSetKeyframeEasing,
                            )
                            StudioToolTab.EFFECTS -> EffectsStackPanel(
                                clip = selectedClip,
                                onPreviewGrade = onPreviewColorGrade,
                                onCommitGrade = onCommitColorGrade,
                                onResetSingle = onResetSingleEffect,
                                onResetAll = onResetAllEffects,
                                onAddEffectToStack = onAddEffectToStack,
                                onToggleEffectEnabled = onToggleEffectEnabled,
                                onMoveEffectInStack = onMoveEffectInStack,
                                onRemoveEffectFromStack = onRemoveEffectFromStack,
                                onPreviewEffectStack = onPreviewEffectStack,
                                onCommitEffectStack = onCommitEffectStack,
                            )
                            StudioToolTab.ADJUST -> AdjustDrawer(
                                clip = selectedClip,
                                onPreviewGrade = onPreviewColorGrade,
                                onCommitGrade = onCommitColorGrade,
                                onResetSingle = onResetSingleAdjust,
                                onResetAll = onResetAllAdjust,
                            )
                            StudioToolTab.BLEND -> BlendingPanel(
                                clip = selectedClip,
                                onPreviewTransform = onPreviewTransform,
                                onCommitBlend = onSetClipBlend,
                                onToggleOpacityKeyframe = onToggleKeyframe,
                            )
                            StudioToolTab.AUDIO -> AudioStudioPanel(
                                clip = selectedClip,
                                onSetAudioEnvelope = onSetAudioEnvelope,
                                onToggleVolumeKeyframe = onToggleKeyframe,
                                onTrim = onTrim,
                                onSplit = onSplit,
                                onDelete = onDelete,
                            )
                            StudioToolTab.FILTERS -> FiltersDrawer(
                                clip = selectedClip,
                                onApplyFilter = onApplyFilter,
                            )
                            StudioToolTab.PRESETS -> StudioPresetsDrawer(
                                hasSelectedClip = selectedClip != null,
                                presets = presetsCatalog,
                                onApplyPreset = onApplyPresetToClip,
                                onSaveCustomPreset = onSaveCustomPreset,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ExcevoColors.HairlineBorder),
                    )

                    // 8-Tab Bottom Editing Toolbar (adaptively ordered by WorkspacePreset)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        orderedTabs.forEach { tab ->
                            val selected = tab == activeTab
                            val icon = when (tab) {
                                StudioToolTab.EDIT -> Icons.Outlined.ContentCut
                                StudioToolTab.TRANSFORM -> Icons.Outlined.Animation
                                StudioToolTab.EFFECTS -> Icons.Outlined.BlurOn
                                StudioToolTab.ADJUST -> Icons.Outlined.Tune
                                StudioToolTab.BLEND -> Icons.Outlined.Layers
                                StudioToolTab.AUDIO -> Icons.Outlined.GraphicEq
                                StudioToolTab.FILTERS -> Icons.Outlined.FilterVintage
                                StudioToolTab.PRESETS -> Icons.Outlined.AutoAwesome
                            }
                            Surface(
                                color = if (selected) ExcevoColors.AccentPurpleContainer else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ExcevoColors.AccentPurple else Color.Transparent,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectTab(tab) }
                                    .testTag("studio_tab_${tab.name}"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditDrawer(
    selectedClip: TimelineClip?,
    tracks: List<TimelineTrack>,
    isImporting: Boolean,
    onImport: () -> Unit,
    onSplit: () -> Unit,
    onTrim: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onMoveToTrack: (String) -> Unit,
    onDelete: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSettings: () -> Unit,
) {
    val hasSelectedClip = selectedClip != null
    var showMoveTrackMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioDrawerButton(
                icon = Icons.Outlined.AddPhotoAlternate,
                label = stringResource(R.string.import_media),
                enabled = !isImporting,
                highlighted = true,
                testTag = "import_media_button",
                onClick = onImport,
            )
            StudioDrawerButton(
                icon = Icons.Outlined.ContentCut,
                label = stringResource(R.string.split),
                enabled = hasSelectedClip,
                testTag = "split_clip_button",
                onClick = onSplit,
            )
            StudioDrawerButton(
                icon = Icons.Outlined.Tune,
                label = stringResource(R.string.trim),
                enabled = hasSelectedClip,
                testTag = "trim_clip_button",
                onClick = onTrim,
            )
            StudioDrawerButton(
                icon = Icons.Outlined.DeleteOutline,
                label = stringResource(R.string.delete_clip),
                enabled = hasSelectedClip,
                danger = true,
                testTag = "delete_clip_button",
                onClick = onDelete,
            )
            StudioDrawerButton(
                icon = Icons.Outlined.ContentCopy,
                label = stringResource(R.string.duplicate),
                enabled = hasSelectedClip,
                testTag = "duplicate_clip_button",
                onClick = onDuplicate,
            )
            StudioDrawerButton(
                icon = Icons.Outlined.SwapHoriz,
                label = stringResource(R.string.move_earlier),
                enabled = hasSelectedClip,
                testTag = "move_left_button",
                onClick = onMoveEarlier,
            )
            StudioDrawerButton(
                icon = Icons.Outlined.SwapHoriz,
                label = stringResource(R.string.move_later),
                enabled = hasSelectedClip,
                testTag = "move_right_button",
                onClick = onMoveLater,
            )
            Box {
                val compatibleTracks = tracks.filter { tr ->
                    selectedClip != null && tr.id != selectedClip.trackId &&
                        ((selectedClip.mediaType == MediaType.AUDIO && tr.type == TrackType.AUDIO) ||
                            (selectedClip.mediaType != MediaType.AUDIO && tr.type != TrackType.AUDIO))
                }
                StudioDrawerButton(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    label = "Track",
                    enabled = hasSelectedClip && compatibleTracks.isNotEmpty(),
                    testTag = "move_to_track_button",
                    onClick = { showMoveTrackMenu = true },
                )
                DropdownMenu(
                    expanded = showMoveTrackMenu,
                    onDismissRequest = { showMoveTrackMenu = false },
                    containerColor = ExcevoColors.ElevatedSurface,
                ) {
                    compatibleTracks.forEach { tr ->
                        DropdownMenuItem(
                            text = { Text("Move to ${tr.name}", color = ExcevoColors.TextPrimary) },
                            onClick = {
                                showMoveTrackMenu = false
                                onMoveToTrack(tr.id)
                            },
                        )
                    }
                }
            }
            StudioDrawerButton(
                icon = Icons.Outlined.Settings,
                label = "Canvas",
                enabled = true,
                testTag = "sequence_settings_button",
                onClick = onSettings,
            )
            StudioDrawerButton(
                icon = if (selectedClip?.isMuted == true) {
                    Icons.AutoMirrored.Outlined.VolumeOff
                } else {
                    Icons.AutoMirrored.Outlined.VolumeUp
                },
                label = if (selectedClip?.isMuted == true) "Unmute" else "Mute",
                enabled = hasSelectedClip,
                highlighted = selectedClip?.isMuted == true,
                testTag = "clip_mute_button",
                onClick = onToggleMute,
            )
        }

        if (selectedClip != null && selectedClip.mediaType == MediaType.AUDIO) {
            var draftVolume by remember(selectedClip.id, selectedClip.volume, selectedClip.isMuted) {
                mutableFloatStateOf(if (selectedClip.isMuted) 0f else selectedClip.volume)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (selectedClip.isMuted) "Muted" else "Vol ${(draftVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.PlayheadAmber,
                    modifier = Modifier.width(68.dp),
                )
                Slider(
                    value = draftVolume,
                    onValueChange = { draftVolume = it },
                    onValueChangeFinished = { onVolumeChange(draftVolume) },
                    valueRange = 0f..1.5f,
                    colors = excevoSliderColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .testTag("clip_volume_slider"),
                )
            }
        }
    }
}

@Composable
private fun FiltersDrawer(
    clip: TimelineClip?,
    onApplyFilter: (String, Float) -> Unit,
) {
    if (clip == null) {
        EmptyClipHint("Select a clip to apply color filters")
        return
    }
    val activeFilterId = clip.colorGrade.filterPresetId ?: "original"
    var intensity by remember(clip.id, clip.colorGrade.filterIntensity) {
        mutableFloatStateOf(clip.colorGrade.filterIntensity.coerceIn(0f, 1f))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorFilterCatalog.filters.forEach { filter ->
                val selected = filter.id == activeFilterId
                Surface(
                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                    ),
                    modifier = Modifier
                        .width(84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyFilter(filter.id, intensity) }
                        .testTag("filter_card_${filter.id}"),
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(filter.accentHex), ExcevoColors.StudioPanel)
                                    )
                                ),
                        )
                        Text(
                            text = filter.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (activeFilterId != "original") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Strength ${(intensity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.AccentLavender,
                )
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    onValueChangeFinished = { onApplyFilter(activeFilterId, intensity) },
                    valueRange = 0f..1f,
                    colors = excevoSliderColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("filter_intensity_slider"),
                )
            }
        }
    }
}

@Composable
private fun AdjustDrawer(
    clip: TimelineClip?,
    onPreviewGrade: (ColorGradeSpec) -> Unit,
    onCommitGrade: (ColorGradeSpec, String) -> Unit,
    onResetSingle: (ColorAdjustParam) -> Unit,
    onResetAll: () -> Unit,
) {
    if (clip == null) {
        EmptyClipHint("Select a clip to adjust color parameters")
        return
    }
    var selectedParam by remember { mutableStateOf(ColorAdjustParam.BRIGHTNESS) }
    var draftGrade by remember(clip.id, clip.colorGrade) { mutableStateOf(clip.colorGrade) }
    val g = draftGrade
    val currentValue = readAdjustValue(g, selectedParam)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorAdjustParam.entries.forEach { param ->
                val valFloat = readAdjustValue(g, param)
                ParamSelectChip(
                    label = param.label,
                    selected = param == selectedParam,
                    isActive = kotlin.math.abs(valFloat) > 0.005f,
                    testTag = "adjust_param_${param.name}",
                    onClick = { selectedParam = param },
                )
            }
            TextButton(
                onClick = onResetAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("reset_all_adjustments_button"),
            ) {
                Text("Reset All", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.PlayheadAmber)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${selectedParam.label}: ${formatSignedPercent(currentValue)}",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.AccentLavender,
                modifier = Modifier.width(104.dp),
            )
            Slider(
                value = currentValue,
                onValueChange = { v ->
                    val updated = writeAdjustValue(draftGrade, selectedParam, v)
                    draftGrade = updated
                    onPreviewGrade(updated)
                },
                onValueChangeFinished = {
                    onCommitGrade(draftGrade, "Adjust ${selectedParam.label}")
                },
                valueRange = selectedParam.range,
                colors = excevoSliderColors(),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("adjust_slider_${selectedParam.name}"),
            )
            TextButton(
                onClick = { onResetSingle(selectedParam) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.testTag("reset_adjust_${selectedParam.name}"),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reset ${selectedParam.label}", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StudioPresetsDrawer(
    hasSelectedClip: Boolean,
    presets: List<PresetDefinition>,
    onApplyPreset: (PresetDefinition, Float) -> Unit,
    onSaveCustomPreset: (String, PresetCategory) -> Unit,
) {
    if (!hasSelectedClip) {
        EmptyClipHint("Select a clip to apply or save presets")
        return
    }
    var selectedPreset by remember(presets) { mutableStateOf(presets.firstOrNull()) }
    var intensity by remember { mutableFloatStateOf(1.0f) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = ExcevoColors.AccentPurpleContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ExcevoColors.AccentPurple),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showSaveDialog = true }
                    .testTag("save_custom_preset_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Save Preset", tint = ExcevoColors.AccentLavender, modifier = Modifier.size(14.dp))
                    Text("Save Clip", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.AccentLavender)
                }
            }

            presets.take(15).forEach { preset ->
                val selected = selectedPreset?.id == preset.id
                Surface(
                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedPreset = preset }
                        .testTag("studio_preset_card_${preset.id}"),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                            maxLines = 1,
                        )
                        Text(
                            text = preset.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.TextMuted,
                        )
                    }
                }
            }
        }

        selectedPreset?.let { preset ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${(intensity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.AccentLavender,
                    modifier = Modifier.width(36.dp),
                )
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 0.1f..1.0f,
                    colors = excevoSliderColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .testTag("studio_preset_intensity_slider"),
                )
                Button(
                    onClick = { onApplyPreset(preset, intensity) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("studio_preset_apply_button"),
                ) {
                    Text("Apply", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showSaveDialog) {
        var title by remember { mutableStateOf("My Studio Look") }
        var category by remember { mutableStateOf(PresetCategory.CC) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = ExcevoColors.StudioPanel,
            title = { Text("Save Custom Preset", color = ExcevoColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ExcevoColors.AccentPurple,
                            unfocusedBorderColor = ExcevoColors.HairlineBorder,
                            focusedTextColor = ExcevoColors.TextPrimary,
                            unfocusedTextColor = ExcevoColors.TextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("custom_preset_title_input"),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(PresetCategory.CC, PresetCategory.EFFECTS, PresetCategory.MOTION).forEach { cat ->
                            ParamSelectChip(
                                label = cat.label,
                                selected = category == cat,
                                isActive = false,
                                testTag = "custom_preset_cat_${cat.name}",
                                onClick = { category = cat },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomPreset(title, category)
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
                    modifier = Modifier.testTag("confirm_save_custom_preset_button"),
                ) {
                    Text("Save Free Preset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = ExcevoColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun StudioDrawerButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    highlighted: Boolean = false,
    danger: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
) {
    val containerColor = if (highlighted) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface
    val contentColor = when {
        !enabled -> ExcevoColors.TextMuted.copy(alpha = 0.45f)
        danger -> ExcevoColors.DangerRed
        highlighted -> ExcevoColors.AccentLavender
        else -> ExcevoColors.TextPrimary
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (highlighted && enabled) ExcevoColors.AccentPurple.copy(alpha = 0.65f) else ExcevoColors.HairlineBorder,
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, maxLines = 1)
        }
    }
}

@Composable
private fun ParamSelectChip(
    label: String,
    selected: Boolean,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(ExcevoColors.PlayheadAmber),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun EmptyClipHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = ExcevoColors.TextMuted,
        )
    }
}

@Composable
private fun excevoSliderColors() = SliderDefaults.colors(
    thumbColor = ExcevoColors.AccentLavender,
    activeTrackColor = ExcevoColors.AccentPurple,
    inactiveTrackColor = ExcevoColors.HairlineBorder,
)

private fun readAdjustValue(g: ColorGradeSpec, param: ColorAdjustParam): Float = when (param) {
    ColorAdjustParam.BRIGHTNESS -> g.brightness
    ColorAdjustParam.CONTRAST -> g.contrast
    ColorAdjustParam.SATURATION -> g.saturation
    ColorAdjustParam.EXPOSURE -> g.exposure
    ColorAdjustParam.TEMPERATURE -> g.temperature
    ColorAdjustParam.TINT -> g.tint
    ColorAdjustParam.HIGHLIGHTS -> g.highlights
    ColorAdjustParam.SHADOWS -> g.shadows
    ColorAdjustParam.SHARPNESS -> g.sharpness
}

private fun writeAdjustValue(g: ColorGradeSpec, param: ColorAdjustParam, v: Float): ColorGradeSpec = when (param) {
    ColorAdjustParam.BRIGHTNESS -> g.copy(brightness = v)
    ColorAdjustParam.CONTRAST -> g.copy(contrast = v)
    ColorAdjustParam.SATURATION -> g.copy(saturation = v)
    ColorAdjustParam.EXPOSURE -> g.copy(exposure = v)
    ColorAdjustParam.TEMPERATURE -> g.copy(temperature = v)
    ColorAdjustParam.TINT -> g.copy(tint = v)
    ColorAdjustParam.HIGHLIGHTS -> g.copy(highlights = v)
    ColorAdjustParam.SHADOWS -> g.copy(shadows = v)
    ColorAdjustParam.SHARPNESS -> g.copy(sharpness = v)
}

private fun formatSignedPercent(value: Float): String {
    val pct = (value * 100).roundToInt()
    return if (pct > 0) "+$pct%" else "$pct%"
}
