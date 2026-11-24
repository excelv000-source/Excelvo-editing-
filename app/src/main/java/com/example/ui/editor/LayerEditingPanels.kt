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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.engine.ColorFilterCatalog
import com.example.core.engine.TimelineEvaluator
import com.example.core.engine.TimelineMath
import com.example.core.model.AnimatableProperty
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.TimelineClip
import com.example.core.model.TransformSpec
import com.example.ui.theme.ExcevoColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LayerEditingMenu(
    clip: TimelineClip,
    isLayerHidden: Boolean = false,
    onToggleVisibility: () -> Unit = {},
    onOpenPanel: (LayerMenuPanel) -> Unit,
    onDuplicate: () -> Unit,
    onToggleGroup: () -> Unit,
    onSplit: () -> Unit = {},
    onResetClip: () -> Unit = {},
    onDelete: () -> Unit,
    onCloseMenu: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = ExcevoColors.SheetObsidian,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("layer_editing_menu"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Centered glowing purple sheet pill handle (Screenshot 2)
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ExcevoColors.AccentPurple.copy(alpha = 0.7f),
                            ExcevoColors.AccentLavender,
                            ExcevoColors.AccentPurple.copy(alpha = 0.7f),
                        )
                    )
                ),
        )

        // Sheet Header: Eye toggle + Layer / Group name + Close X
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("layer_menu_visibility_toggle"),
                ) {
                    Icon(
                        imageVector = if (isLayerHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (isLayerHidden) "Show Layer" else "Hide Layer",
                        tint = if (isLayerHidden) ExcevoColors.TextMuted else ExcevoColors.AccentLavender,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = if (clip.groupId != null) "Group · ${clip.label}" else clip.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ExcevoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onCloseMenu,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("layer_menu_close_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close Layer Menu",
                    tint = ExcevoColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 4 x 3 Dark Rounded Action Tile Grid (Screenshot 2 visual hierarchy, 100% real actions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LayerMenuGridTile(
                icon = Icons.Outlined.OpenWith,
                label = "Move &\nTransform",
                highlighted = true,
                testTag = "layer_menu_MOVE_TRANSFORM",
                modifier = Modifier.weight(1f),
                onClick = { onOpenPanel(LayerMenuPanel.MOVE_TRANSFORM) },
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.FormatColorFill,
                label = "Color &\nFill",
                testTag = "layer_menu_COLOR_FILL",
                modifier = Modifier.weight(1f),
                onClick = { onOpenPanel(LayerMenuPanel.COLOR_FILL) },
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.AutoAwesome,
                label = "Effects",
                testTag = "layer_menu_EFFECTS",
                modifier = Modifier.weight(1f),
                onClick = { onOpenPanel(LayerMenuPanel.EFFECTS) },
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.Layers,
                label = "Blending",
                testTag = "layer_menu_BLENDING",
                modifier = Modifier.weight(1f),
                onClick = { onOpenPanel(LayerMenuPanel.BLENDING) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LayerMenuGridTile(
                icon = Icons.Outlined.Opacity,
                label = "Opacity",
                testTag = "layer_menu_OPACITY",
                modifier = Modifier.weight(1f),
                onClick = { onOpenPanel(LayerMenuPanel.OPACITY) },
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.Diamond,
                label = "Keyframes",
                testTag = "layer_menu_KEYFRAMES",
                modifier = Modifier.weight(1f),
                onClick = { onOpenPanel(LayerMenuPanel.KEYFRAMES) },
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.StarOutline,
                label = "Presets",
                testTag = "layer_menu_PRESETS",
                modifier = Modifier.weight(1f),
                onClick = { onOpenPanel(LayerMenuPanel.PRESETS) },
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.ContentCopy,
                label = "Duplicate",
                testTag = "layer_menu_DUPLICATE",
                modifier = Modifier.weight(1f),
                onClick = onDuplicate,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LayerMenuGridTile(
                icon = Icons.Outlined.GroupWork,
                label = if (clip.groupId != null) "Ungroup" else "Group",
                highlighted = clip.groupId != null,
                testTag = "layer_menu_GROUP",
                modifier = Modifier.weight(1f),
                onClick = onToggleGroup,
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.ContentCut,
                label = "Split",
                testTag = "layer_menu_SPLIT",
                modifier = Modifier.weight(1f),
                onClick = onSplit,
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.Refresh,
                label = "Reset",
                testTag = "layer_menu_RESET",
                modifier = Modifier.weight(1f),
                onClick = onResetClip,
            )
            LayerMenuGridTile(
                icon = Icons.Outlined.DeleteOutline,
                label = "Delete",
                danger = true,
                testTag = "layer_menu_DELETE",
                modifier = Modifier.weight(1f),
                onClick = onDelete,
            )
        }
    }
}

@Composable
fun FocusedLayerPanelHeader(
    panel: LayerMenuPanel,
    clip: TimelineClip,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExcevoColors.ElevatedSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("focused_layer_panel_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            color = ExcevoColors.AccentPurpleContainer,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ExcevoColors.AccentPurple),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .testTag("layer_panel_back_button"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to Layer Menu",
                    tint = ExcevoColors.AccentLavender,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ExcevoColors.AccentLavender,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = panel.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = ExcevoColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = clip.label,
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val kfCount = clip.keyframes.values.sumOf { it.size }
        Surface(
            color = ExcevoColors.StudioPanel,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        ) {
            Text(
                text = "$kfCount KF",
                style = MaterialTheme.typography.labelSmall,
                color = if (kfCount > 0) ExcevoColors.PlayheadAmber else ExcevoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun ColorFillPanel(
    clip: TimelineClip?,
    onPreviewGrade: (ColorGradeSpec) -> Unit,
    onCommitGrade: (ColorGradeSpec, String) -> Unit,
    onResetSingleAdjust: (ColorAdjustParam) -> Unit,
    onResetAllAdjust: () -> Unit,
    onApplyFilter: (String, Float) -> Unit,
) {
    if (clip == null) return
    var subMode by remember { mutableStateOf(0) } // 0 = Color Grading, 1 = Filters / Fill

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Color Grading" to 0, "Filters & Fill" to 1).forEach { (label, idx) ->
                val selected = subMode == idx
                Surface(
                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { subMode = idx }
                        .testTag("color_fill_tab_$idx"),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        if (subMode == 0) {
            var selectedParam by remember { mutableStateOf(ColorAdjustParam.BRIGHTNESS) }
            var draftGrade by remember(clip.id, clip.colorGrade) { mutableStateOf(clip.colorGrade) }
            val currentValue = when (selectedParam) {
                ColorAdjustParam.BRIGHTNESS -> draftGrade.brightness
                ColorAdjustParam.CONTRAST -> draftGrade.contrast
                ColorAdjustParam.SATURATION -> draftGrade.saturation
                ColorAdjustParam.EXPOSURE -> draftGrade.exposure
                ColorAdjustParam.TEMPERATURE -> draftGrade.temperature
                ColorAdjustParam.TINT -> draftGrade.tint
                ColorAdjustParam.HIGHLIGHTS -> draftGrade.highlights
                ColorAdjustParam.SHADOWS -> draftGrade.shadows
                ColorAdjustParam.SHARPNESS -> draftGrade.sharpness
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorAdjustParam.entries.forEach { param ->
                    val v = when (param) {
                        ColorAdjustParam.BRIGHTNESS -> draftGrade.brightness
                        ColorAdjustParam.CONTRAST -> draftGrade.contrast
                        ColorAdjustParam.SATURATION -> draftGrade.saturation
                        ColorAdjustParam.EXPOSURE -> draftGrade.exposure
                        ColorAdjustParam.TEMPERATURE -> draftGrade.temperature
                        ColorAdjustParam.TINT -> draftGrade.tint
                        ColorAdjustParam.HIGHLIGHTS -> draftGrade.highlights
                        ColorAdjustParam.SHADOWS -> draftGrade.shadows
                        ColorAdjustParam.SHARPNESS -> draftGrade.sharpness
                    }
                    val sel = param == selectedParam
                    Surface(
                        color = if (sel) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (sel) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedParam = param }
                            .testTag("adjust_param_${param.name}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (abs(v) > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(ExcevoColors.PlayheadAmber),
                                )
                            }
                            Text(
                                text = param.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onResetAllAdjust,
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
                val pct = (currentValue * 100).roundToInt()
                Text(
                    text = "${selectedParam.label}: ${if (pct > 0) "+$pct%" else "$pct%"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.AccentLavender,
                    modifier = Modifier.width(104.dp),
                )
                Slider(
                    value = currentValue,
                    onValueChange = { v ->
                        val updated = when (selectedParam) {
                            ColorAdjustParam.BRIGHTNESS -> draftGrade.copy(brightness = v)
                            ColorAdjustParam.CONTRAST -> draftGrade.copy(contrast = v)
                            ColorAdjustParam.SATURATION -> draftGrade.copy(saturation = v)
                            ColorAdjustParam.EXPOSURE -> draftGrade.copy(exposure = v)
                            ColorAdjustParam.TEMPERATURE -> draftGrade.copy(temperature = v)
                            ColorAdjustParam.TINT -> draftGrade.copy(tint = v)
                            ColorAdjustParam.HIGHLIGHTS -> draftGrade.copy(highlights = v)
                            ColorAdjustParam.SHADOWS -> draftGrade.copy(shadows = v)
                            ColorAdjustParam.SHARPNESS -> draftGrade.copy(sharpness = v)
                        }
                        draftGrade = updated
                        onPreviewGrade(updated)
                    },
                    onValueChangeFinished = {
                        onCommitGrade(draftGrade, "Adjust ${selectedParam.label}")
                    },
                    valueRange = selectedParam.range,
                    colors = SliderDefaults.colors(
                        thumbColor = ExcevoColors.AccentLavender,
                        activeTrackColor = ExcevoColors.AccentPurple,
                        inactiveTrackColor = ExcevoColors.HairlineBorder,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("adjust_slider_${selectedParam.name}"),
                )
                TextButton(
                    onClick = { onResetSingleAdjust(selectedParam) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("reset_adjust_${selectedParam.name}"),
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Reset ${selectedParam.label}",
                        tint = ExcevoColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            val activeFilterId = clip.colorGrade.filterPresetId ?: "original"
            var intensity by remember(clip.id, clip.colorGrade.filterIntensity) {
                mutableFloatStateOf(clip.colorGrade.filterIntensity.coerceIn(0f, 1f))
            }
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
                        colors = SliderDefaults.colors(
                            thumbColor = ExcevoColors.AccentLavender,
                            activeTrackColor = ExcevoColors.AccentPurple,
                            inactiveTrackColor = ExcevoColors.HairlineBorder,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .testTag("filter_intensity_slider"),
                    )
                }
            }
        }
    }
}

@Composable
fun OpacityPanel(
    clip: TimelineClip?,
    playheadUs: Long,
    onPreviewTransform: (TransformSpec) -> Unit,
    onCommitOpacity: (Float) -> Unit,
    onToggleOpacityKeyframe: (AnimatableProperty, Float) -> Unit,
    onJumpOpacityKeyframe: (AnimatableProperty, Boolean) -> Unit,
) {
    if (clip == null) return
    val evaluatedOpacity = remember(clip, playheadUs) {
        TimelineEvaluator.evaluateTransformAt(clip, playheadUs).opacity.coerceIn(0f, 1f)
    }
    var draftOpacity by remember(clip.id, clip.transform.opacity) {
        mutableFloatStateOf(clip.transform.opacity.coerceIn(0f, 1f))
    }
    var opacityInput by remember(clip.id, clip.transform.opacity) {
        mutableStateOf((clip.transform.opacity * 100f).roundToInt().toString())
    }
    val localOffsetUs = (playheadUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
    val hasKfHere = TimelineEvaluator.findKeyframeNear(clip, AnimatableProperty.OPACITY, localOffsetUs) != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .testTag("opacity_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Layer Opacity",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.AccentLavender,
            )
            Slider(
                value = if (draftOpacity != clip.transform.opacity) draftOpacity else evaluatedOpacity,
                onValueChange = { v ->
                    draftOpacity = v
                    opacityInput = (v * 100f).roundToInt().toString()
                    onPreviewTransform(clip.transform.copy(opacity = v))
                },
                onValueChangeFinished = {
                    onCommitOpacity(draftOpacity)
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = ExcevoColors.AccentLavender,
                    activeTrackColor = ExcevoColors.AccentPurple,
                    inactiveTrackColor = ExcevoColors.HairlineBorder,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("opacity_panel_slider"),
            )
            OutlinedTextField(
                value = opacityInput,
                onValueChange = { raw ->
                    val filtered = raw.filter(Char::isDigit).take(3)
                    opacityInput = filtered
                    val pct = filtered.toIntOrNull()?.coerceIn(0, 100)
                    if (pct != null) {
                        val norm = pct / 100f
                        draftOpacity = norm
                        onCommitOpacity(norm)
                    }
                },
                suffix = { Text("%", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ExcevoColors.AccentPurple,
                    unfocusedBorderColor = ExcevoColors.HairlineBorder,
                    focusedTextColor = ExcevoColors.TextPrimary,
                    unfocusedTextColor = ExcevoColors.TextPrimary,
                ),
                modifier = Modifier
                    .width(80.dp)
                    .testTag("opacity_panel_input"),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(0, 25, 50, 75, 100).forEach { pct ->
                val norm = pct / 100f
                val selected = abs(draftOpacity - norm) < 0.02f
                Surface(
                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            draftOpacity = norm
                            opacityInput = pct.toString()
                            onCommitOpacity(norm)
                        }
                        .testTag("opacity_preset_$pct"),
                ) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { onJumpOpacityKeyframe(AnimatableProperty.OPACITY, false) },
                enabled = clip.keyframes[AnimatableProperty.OPACITY].orEmpty().isNotEmpty(),
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Prev Opacity Keyframe", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
            Surface(
                color = if (hasKfHere) ExcevoColors.PlayheadAmberContainer else ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (hasKfHere) ExcevoColors.PlayheadAmber else ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onToggleOpacityKeyframe(AnimatableProperty.OPACITY, draftOpacity) }
                    .testTag("opacity_keyframe_toggle"),
            ) {
                Text(
                    text = if (hasKfHere) "◆ Keyframed" else "◇ Keyframe",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.PlayheadAmber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            IconButton(
                onClick = { onJumpOpacityKeyframe(AnimatableProperty.OPACITY, true) },
                enabled = clip.keyframes[AnimatableProperty.OPACITY].orEmpty().isNotEmpty(),
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next Opacity Keyframe", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun KeyframesInspectorPanel(
    clip: TimelineClip?,
    playheadUs: Long,
    onToggleKeyframe: (AnimatableProperty, Float) -> Unit,
    onRemoveKeyframe: (AnimatableProperty, String) -> Unit,
    onJumpKeyframe: (AnimatableProperty, Boolean) -> Unit,
    onSeekToUs: (Long) -> Unit,
    onSetEasing: (AnimatableProperty, String, EasingType) -> Unit,
) {
    if (clip == null) return
    var selectedProp by remember { mutableStateOf(AnimatableProperty.POSITION_X) }
    val evaluatedTransform = remember(clip, playheadUs) {
        TimelineEvaluator.evaluateTransformAt(clip, playheadUs)
    }
    val localOffsetUs = (playheadUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
    val propKeyframes = clip.keyframes[selectedProp].orEmpty().sortedBy { it.timeOffsetUs }
    val activeKf = TimelineEvaluator.findKeyframeNear(clip, selectedProp, localOffsetUs)
    val valueAtPlayhead = TimelineEvaluator.normalizedValueFor(evaluatedTransform, clip.volume, selectedProp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keyframes_inspector_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Property Selector with Active Keyframe Counts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatableProperty.entries.forEach { prop ->
                val count = clip.keyframes[prop].orEmpty().size
                val selected = prop == selectedProp
                Surface(
                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedProp = prop }
                        .testTag("kf_prop_${prop.name}"),
                ) {
                    Text(
                        text = if (count > 0) "${prop.label} ($count)" else prop.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            selected -> ExcevoColors.AccentLavender
                            count > 0 -> ExcevoColors.PlayheadAmber
                            else -> ExcevoColors.TextPrimary
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // Playhead controls for selected property
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { onJumpKeyframe(selectedProp, false) },
                    enabled = propKeyframes.isNotEmpty(),
                    modifier = Modifier.size(28.dp).testTag("kf_panel_prev"),
                ) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                Surface(
                    color = if (activeKf != null) ExcevoColors.PlayheadAmberContainer else ExcevoColors.AccentPurpleContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (activeKf != null) ExcevoColors.PlayheadAmber else ExcevoColors.AccentPurple),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleKeyframe(selectedProp, valueAtPlayhead) }
                        .testTag("kf_panel_toggle_diamond"),
                ) {
                    Text(
                        text = if (activeKf != null) "◆ Remove at Playhead" else "◇ Add Keyframe",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeKf != null) ExcevoColors.PlayheadAmber else ExcevoColors.AccentLavender,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
                IconButton(
                    onClick = { onJumpKeyframe(selectedProp, true) },
                    enabled = propKeyframes.isNotEmpty(),
                    modifier = Modifier.size(28.dp).testTag("kf_panel_next"),
                ) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Text(
                text = "Val: ${String.format("%.2f", valueAtPlayhead)}",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.TextSecondary,
            )
        }

        // Keyframes timeline chips + easing selector
        if (propKeyframes.isEmpty()) {
            Text(
                text = "No keyframes for ${selectedProp.label}. Scrub playhead and tap Add Keyframe.",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.TextMuted,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                propKeyframes.forEach { kf ->
                    val isNear = abs(kf.timeOffsetUs - localOffsetUs) <= TimelineEvaluator.KEYFRAME_SNAP_TOLERANCE_US
                    Surface(
                        color = if (isNear) ExcevoColors.PlayheadAmberContainer else ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isNear) ExcevoColors.PlayheadAmber else ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSeekToUs(clip.timelineStartUs + kf.timeOffsetUs) }
                            .testTag("kf_item_${kf.id}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "${TimelineMath.formatDurationCompact(kf.timeOffsetUs)} (${String.format("%.1f", kf.value)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNear) ExcevoColors.PlayheadAmber else ExcevoColors.TextPrimary,
                            )
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Delete keyframe",
                                tint = ExcevoColors.DangerRed,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { onRemoveKeyframe(selectedProp, kf.id) }
                                    .testTag("kf_delete_${kf.id}"),
                            )
                        }
                    }
                }
            }

            val easingTarget = activeKf ?: propKeyframes.lastOrNull()
            if (easingTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Easing:", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextSecondary)
                    EasingType.entries.forEach { easing ->
                        val sel = easing == easingTarget.easing
                        Surface(
                            color = if (sel) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (sel) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSetEasing(selectedProp, easingTarget.id, easing) }
                                .testTag("kf_panel_easing_${easing.name}"),
                        ) {
                            Text(
                                text = easing.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerMenuGridTile(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    danger: Boolean = false,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val iconColor = when {
        danger -> ExcevoColors.DangerRed
        highlighted -> ExcevoColors.AccentLavender
        else -> ExcevoColors.AccentLavender.copy(alpha = 0.9f)
    }
    Surface(
        color = if (highlighted) ExcevoColors.AccentPurpleContainer.copy(alpha = 0.72f) else ExcevoColors.TileCardSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                danger -> ExcevoColors.DangerRed.copy(alpha = 0.4f)
                highlighted -> ExcevoColors.AccentPurple.copy(alpha = 0.85f)
                else -> ExcevoColors.TileCardBorder
            },
        ),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label.replace('\n', ' '),
                tint = iconColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    lineHeight = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontSize = androidx.compose.ui.unit.TextUnit(9.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
                color = if (danger) ExcevoColors.DangerRed else ExcevoColors.TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
