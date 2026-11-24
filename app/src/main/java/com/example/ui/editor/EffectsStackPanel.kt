package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.TimelineClip
import com.example.core.model.VisualEffectType
import com.example.ui.theme.ExcevoColors
import kotlin.math.roundToInt

@Composable
fun EffectsStackPanel(
    clip: TimelineClip?,
    onPreviewGrade: (ColorGradeSpec) -> Unit,
    onCommitGrade: (ColorGradeSpec, String) -> Unit,
    onResetSingle: (VisualEffectParam) -> Unit,
    onResetAll: () -> Unit,
    onAddEffectToStack: (VisualEffectType) -> Unit,
    onToggleEffectEnabled: (String) -> Unit,
    onMoveEffectInStack: (String, Int) -> Unit,
    onRemoveEffectFromStack: (String) -> Unit,
    onPreviewEffectStack: (List<ClipEffectInstance>) -> Unit,
    onCommitEffectStack: (List<ClipEffectInstance>, String) -> Unit,
) {
    if (clip == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select or import a clip to apply modular GPU effects",
                style = MaterialTheme.typography.labelMedium,
                color = ExcevoColors.TextMuted,
            )
        }
        return
    }

    var selectedParam by remember { mutableStateOf(VisualEffectParam.BLUR) }
    var draftGrade by remember(clip.id, clip.colorGrade) { mutableStateOf(clip.colorGrade) }
    var draftStack by remember(clip.id, clip.effectStack) { mutableStateOf(clip.effectStack) }

    val currentValue = when (selectedParam) {
        VisualEffectParam.BLUR -> draftGrade.blurRadius
        VisualEffectParam.GLOW -> draftGrade.glow
        VisualEffectParam.VIGNETTE -> draftGrade.vignette
        VisualEffectParam.GRAYSCALE -> draftGrade.grayscale
        VisualEffectParam.SEPIA -> draftGrade.sepia
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row 1: Quick Grade FX parameter chips + Reset FX
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisualEffectParam.entries.forEach { param ->
                val v = when (param) {
                    VisualEffectParam.BLUR -> draftGrade.blurRadius
                    VisualEffectParam.GLOW -> draftGrade.glow
                    VisualEffectParam.VIGNETTE -> draftGrade.vignette
                    VisualEffectParam.GRAYSCALE -> draftGrade.grayscale
                    VisualEffectParam.SEPIA -> draftGrade.sepia
                }
                val sel = param == selectedParam
                Surface(
                    color = if (sel) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (sel) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedParam = param }
                        .testTag("effect_param_${param.name}"),
                ) {
                    Text(
                        text = if (v > 0.005f) "${param.label} •" else param.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }

            TextButton(
                onClick = onResetAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("reset_all_effects_button"),
            ) {
                Text("Reset FX", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.PlayheadAmber)
            }
        }

        // Row 2: Selected quick-effect slider (always exposes effect_slider_BLUR by default)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${selectedParam.label}: ${(currentValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.AccentLavender,
                modifier = Modifier.width(88.dp),
            )
            Slider(
                value = currentValue,
                onValueChange = { v ->
                    val updated = when (selectedParam) {
                        VisualEffectParam.BLUR -> draftGrade.copy(blurRadius = v)
                        VisualEffectParam.GLOW -> draftGrade.copy(glow = v)
                        VisualEffectParam.VIGNETTE -> draftGrade.copy(vignette = v)
                        VisualEffectParam.GRAYSCALE -> draftGrade.copy(grayscale = v)
                        VisualEffectParam.SEPIA -> draftGrade.copy(sepia = v)
                    }
                    draftGrade = updated
                    onPreviewGrade(updated)
                },
                onValueChangeFinished = {
                    onCommitGrade(draftGrade, "Effect ${selectedParam.label}")
                },
                valueRange = selectedParam.range,
                colors = fxSliderColors(),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .testTag("effect_slider_${selectedParam.name}"),
            )
            TextButton(
                onClick = { onResetSingle(selectedParam) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.testTag("reset_effect_${selectedParam.name}"),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reset ${selectedParam.label}", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        // Row 3: Add modular stackable effects + active stack list
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Stack:",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.TextSecondary,
            )
            VisualEffectType.entries.forEach { fxType ->
                Surface(
                    color = ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddEffectToStack(fxType) }
                        .testTag("add_effect_${fxType.name}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = ExcevoColors.AccentLavender, modifier = Modifier.size(12.dp))
                        Text(fxType.label, style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                    }
                }
            }
        }

        if (draftStack.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draftStack.forEachIndexed { idx, fx ->
                    Surface(
                        color = if (fx.enabled) ExcevoColors.AccentPurpleContainer.copy(alpha = 0.55f) else ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (fx.enabled) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .width(196.dp)
                            .testTag("effect_stack_item_${fx.id}"),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${fx.type.label} ${(fx.intensity * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ExcevoColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = fx.enabled,
                                    onCheckedChange = { onToggleEffectEnabled(fx.id) },
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 22.dp)
                                        .testTag("toggle_effect_${fx.id}"),
                                )
                                IconButton(
                                    onClick = { onMoveEffectInStack(fx.id, -1) },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(22.dp).testTag("move_effect_up_${fx.id}"),
                                ) {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move Up", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(13.dp))
                                }
                                IconButton(
                                    onClick = { onMoveEffectInStack(fx.id, 1) },
                                    enabled = idx < draftStack.lastIndex,
                                    modifier = Modifier.size(22.dp).testTag("move_effect_down_${fx.id}"),
                                ) {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move Down", tint = ExcevoColors.TextSecondary, modifier = Modifier.size(13.dp))
                                }
                                IconButton(
                                    onClick = { onRemoveEffectFromStack(fx.id) },
                                    modifier = Modifier.size(22.dp).testTag("remove_effect_${fx.id}"),
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove Effect", tint = ExcevoColors.DangerRed, modifier = Modifier.size(13.dp))
                                }
                            }
                            Slider(
                                value = fx.intensity.coerceIn(0f, 1f),
                                onValueChange = { v ->
                                    val updated = draftStack.map { if (it.id == fx.id) it.copy(intensity = v) else it }
                                    draftStack = updated
                                    onPreviewEffectStack(updated)
                                },
                                onValueChangeFinished = {
                                    onCommitEffectStack(draftStack, "Adjust ${fx.type.label}")
                                },
                                colors = fxSliderColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp)
                                    .testTag("stack_effect_slider_${fx.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun fxSliderColors() = SliderDefaults.colors(
    thumbColor = ExcevoColors.AccentLavender,
    activeTrackColor = ExcevoColors.AccentPurple,
    inactiveTrackColor = ExcevoColors.HairlineBorder,
)
