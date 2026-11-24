package com.example.ui.editor

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.model.AnimatableProperty
import com.example.core.model.BlendModeType
import com.example.core.model.TimelineClip
import com.example.core.model.TransformSpec
import com.example.ui.theme.ExcevoColors
import kotlin.math.roundToInt

@Composable
fun BlendingPanel(
    clip: TimelineClip?,
    onPreviewTransform: (TransformSpec) -> Unit,
    onCommitBlend: (BlendModeType, Float) -> Unit,
    onToggleOpacityKeyframe: (AnimatableProperty, Float) -> Unit,
) {
    if (clip == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select a clip to edit Opacity & Blend Modes",
                style = MaterialTheme.typography.labelMedium,
                color = ExcevoColors.TextMuted,
            )
        }
        return
    }

    var draftOpacity by remember(clip.id, clip.transform.opacity) {
        mutableFloatStateOf(clip.transform.opacity.coerceIn(0f, 1f))
    }
    var opacityInput by remember(clip.id, clip.transform.opacity) {
        mutableStateOf((clip.transform.opacity * 100f).roundToInt().toString())
    }
    val supportsAdvancedBlend = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Opacity",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.AccentLavender,
            )
            Slider(
                value = draftOpacity,
                onValueChange = { v ->
                    draftOpacity = v
                    opacityInput = (v * 100f).roundToInt().toString()
                    onPreviewTransform(clip.transform.copy(opacity = v))
                },
                onValueChangeFinished = {
                    onCommitBlend(clip.blendMode, draftOpacity)
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = ExcevoColors.AccentLavender,
                    activeTrackColor = ExcevoColors.AccentPurple,
                    inactiveTrackColor = ExcevoColors.HairlineBorder,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .testTag("blend_opacity_slider"),
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
                        onCommitBlend(clip.blendMode, norm)
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
                    .testTag("blend_opacity_input"),
            )
            Surface(
                color = ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onToggleOpacityKeyframe(AnimatableProperty.OPACITY, draftOpacity) }
                    .testTag("blend_opacity_keyframe"),
            ) {
                Text(
                    text = "◇ KF",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.PlayheadAmber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        val availableModes = remember(supportsAdvancedBlend) {
            if (supportsAdvancedBlend) BlendModeType.entries else BlendModeType.entries.filter { !it.requiresApi29 }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            availableModes.forEach { mode ->
                val selected = mode == clip.blendMode
                Surface(
                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onCommitBlend(mode, draftOpacity)
                        }
                        .testTag("blend_mode_${mode.name}"),
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
            if (!supportsAdvancedBlend) {
                Text(
                    text = "Android 10+ required for compositing blend modes",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
