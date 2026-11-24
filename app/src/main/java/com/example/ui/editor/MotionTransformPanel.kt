package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.engine.TimelineEvaluator
import com.example.core.model.AnimatableProperty
import com.example.core.model.CanvasScaleMode
import com.example.core.model.EasingType
import com.example.core.model.TimelineClip
import com.example.core.model.TransformSpec
import com.example.ui.theme.ExcevoColors
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MotionSubTab(val label: String, val keyProperty: AnimatableProperty) {
    TOUCHPAD("Move", AnimatableProperty.POSITION_X),
    ROTATE("Rotate", AnimatableProperty.ROTATION_DEG),
    SCALE("Size", AnimatableProperty.SCALE_X),
    SKEW("Skew", AnimatableProperty.SKEW_X),
    Z_DEPTH("Z Depth", AnimatableProperty.POSITION_Z),
    ANCHOR("Anchor", AnimatableProperty.ANCHOR_X),
    CROP("Crop", AnimatableProperty.OPACITY),
    SPEED("Speed", AnimatableProperty.VOLUME),
}

@Composable
fun MotionTransformPanel(
    clip: TimelineClip?,
    playheadUs: Long,
    scaleMode: CanvasScaleMode,
    onSetScaleMode: (CanvasScaleMode) -> Unit,
    onRotate90: () -> Unit,
    onResetTransform: () -> Unit,
    onPreviewTransform: (TransformSpec) -> Unit,
    onCommitTransform: (TransformSpec, String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleKeyframe: (AnimatableProperty, Float) -> Unit,
    onJumpKeyframe: (AnimatableProperty, Boolean) -> Unit,
    onSetKeyframeEasing: (AnimatableProperty, String, EasingType) -> Unit,
) {
    if (clip == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select a clip to edit Move & Transform, X/Y/Z & Keyframes",
                style = MaterialTheme.typography.labelMedium,
                color = ExcevoColors.TextMuted,
            )
        }
        return
    }

    var activeSubTab by remember { mutableStateOf(MotionSubTab.TOUCHPAD) }
    var uniformScale by remember { mutableStateOf(true) }
    var showEasingDrawer by remember { mutableStateOf(false) }

    var draftTransform by remember(clip.id, clip.transform) { mutableStateOf(clip.transform) }
    val evaluated = remember(clip, playheadUs, draftTransform) {
        if (draftTransform != clip.transform) {
            draftTransform
        } else {
            TimelineEvaluator.evaluateTransformAt(clip, playheadUs)
        }
    }

    val activeProp = activeSubTab.keyProperty
    val localOffsetUs = (playheadUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
    val activeKeyframe = TimelineEvaluator.findKeyframeNear(clip, activeProp, localOffsetUs)
    val propValueAtPlayhead = TimelineEvaluator.normalizedValueFor(evaluated, clip.volume, activeProp)
    val propKeyframes = clip.keyframes[activeProp].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Header: X / Y / Z live values + compact Diamond Keyframe toggle + Easing toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("xyz_telemetry_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XyzPill("X", evaluated.positionX.roundToInt().toString())
                XyzPill("Y", evaluated.positionY.roundToInt().toString())
                XyzPill("Z", evaluated.positionZ.roundToInt().toString(), highlight = abs(evaluated.positionZ) > 0.5f)
                Text(
                    text = activeProp.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.TextMuted,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = { onJumpKeyframe(activeProp, false) },
                    enabled = propKeyframes.isNotEmpty(),
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("keyframe_prev_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronLeft,
                        contentDescription = "Previous Keyframe",
                        tint = if (propKeyframes.isNotEmpty()) ExcevoColors.TextSecondary else ExcevoColors.TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Compact Diamond Keyframe Button
                Surface(
                    color = if (activeKeyframe != null) ExcevoColors.PlayheadAmberContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (activeKeyframe != null) ExcevoColors.PlayheadAmber else ExcevoColors.HairlineBorder,
                    ),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleKeyframe(activeProp, propValueAtPlayhead) }
                        .testTag("keyframe_diamond_toggle"),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension / 2f
                        val diamond = Path().apply {
                            moveTo(cx, cy - r)
                            lineTo(cx + r, cy)
                            lineTo(cx, cy + r)
                            lineTo(cx - r, cy)
                            close()
                        }
                        if (activeKeyframe != null) {
                            drawPath(diamond, color = ExcevoColors.PlayheadAmber)
                        } else {
                            drawPath(diamond, color = ExcevoColors.TextPrimary, style = Stroke(width = 1.6.dp.toPx()))
                        }
                    }
                }

                IconButton(
                    onClick = { onJumpKeyframe(activeProp, true) },
                    enabled = propKeyframes.isNotEmpty(),
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("keyframe_next_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Next Keyframe",
                        tint = if (propKeyframes.isNotEmpty()) ExcevoColors.TextSecondary else ExcevoColors.TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                IconButton(
                    onClick = { showEasingDrawer = !showEasingDrawer },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("easing_drawer_toggle"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                        contentDescription = "Easing Curve",
                        tint = if (showEasingDrawer) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Mode Tabs + Direct Transform Actions (Always Visible — No Hidden "More" Menu)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MotionSubTab.entries.forEach { tab ->
                val selected = tab == activeSubTab
                Surface(
                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { activeSubTab = tab }
                        .testTag("motion_subtab_${tab.name}"),
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }

            Surface(
                color = ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRotate90)
                    .testTag("transform_rotate_90_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Outlined.Rotate90DegreesCw, contentDescription = "Rotate 90", tint = ExcevoColors.AccentLavender, modifier = Modifier.size(14.dp))
                    Text("+90°", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                }
            }

            Surface(
                color = if (draftTransform.flipH) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (draftTransform.flipH) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val next = draftTransform.copy(flipH = !draftTransform.flipH)
                        draftTransform = next
                        onCommitTransform(next, "Flip Horizontal")
                    }
                    .testTag("transform_flip_h"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Outlined.Flip, contentDescription = "Flip H", tint = ExcevoColors.TextPrimary, modifier = Modifier.size(13.dp))
                    Text("Flip H", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                }
            }

            Surface(
                color = if (draftTransform.flipV) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (draftTransform.flipV) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val next = draftTransform.copy(flipV = !draftTransform.flipV)
                        draftTransform = next
                        onCommitTransform(next, "Flip Vertical")
                    }
                    .testTag("transform_flip_v"),
            ) {
                Text("Flip V", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
            }

            CanvasScaleMode.entries.forEach { mode ->
                val sel = mode == scaleMode
                Surface(
                    color = if (sel) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (sel) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSetScaleMode(mode) }
                        .testTag("transform_mode_${mode.name}"),
                ) {
                    Text(mode.label, style = MaterialTheme.typography.labelSmall, color = if (sel) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
            }

            TextButton(
                onClick = onResetTransform,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.testTag("transform_reset_button"),
            ) {
                Text("Reset", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.PlayheadAmber)
            }
        }

        // Expandable Compact Easing Curve Drawer
        if (showEasingDrawer) {
            val targetKf = activeKeyframe ?: propKeyframes.lastOrNull()
            val selectedEasing = targetKf?.easing ?: EasingType.EASE_IN_OUT
            Surface(
                color = ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("easing_curve_drawer"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EasingBezierPreview(
                        easing = selectedEasing,
                        modifier = Modifier.size(34.dp),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EasingType.entries.forEach { easing ->
                            val isSel = easing == selectedEasing
                            Surface(
                                color = if (isSel) ExcevoColors.AccentPurpleContainer else ExcevoColors.StudioPanel,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isSel) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = targetKf != null) {
                                        if (targetKf != null) {
                                            onSetKeyframeEasing(activeProp, targetKf.id, easing)
                                        }
                                    }
                                    .testTag("easing_option_${easing.name}"),
                            ) {
                                Text(
                                    text = easing.name.replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (targetKf == null) ExcevoColors.TextMuted else ExcevoColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode Control Area
        when (activeSubTab) {
            MotionSubTab.TOUCHPAD -> {
                var xInput by remember(clip.id, evaluated.positionX) {
                    mutableStateOf(evaluated.positionX.roundToInt().toString())
                }
                var yInput by remember(clip.id, evaluated.positionY) {
                    mutableStateOf(evaluated.positionY.roundToInt().toString())
                }
                var zInput by remember(clip.id, evaluated.positionZ) {
                    mutableStateOf(evaluated.positionZ.roundToInt().toString())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 2D Movement Touchpad
                    Surface(
                        color = ExcevoColors.VoidCharcoal,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .width(106.dp)
                            .height(66.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .pointerInput(clip.id) {
                                detectDragGestures(
                                    onDrag = { change, drag ->
                                        change.consume()
                                        val next = draftTransform.copy(
                                            positionX = (draftTransform.positionX + drag.x * 1.4f).coerceIn(-600f, 600f),
                                            positionY = (draftTransform.positionY + drag.y * 1.4f).coerceIn(-600f, 600f),
                                        )
                                        draftTransform = next
                                        xInput = next.positionX.roundToInt().toString()
                                        yInput = next.positionY.roundToInt().toString()
                                        onPreviewTransform(next)
                                    },
                                    onDragEnd = {
                                        onCommitTransform(draftTransform, "Touchpad Move X/Y")
                                    },
                                )
                            }
                            .testTag("motion_touchpad"),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            drawLine(ExcevoColors.HairlineBorder, Offset(cx, 0f), Offset(cx, size.height), 1f)
                            drawLine(ExcevoColors.HairlineBorder, Offset(0f, cy), Offset(size.width, cy), 1f)
                            val dotX = (cx + (evaluated.positionX / 600f) * (cx - 8f)).coerceIn(8f, size.width - 8f)
                            val dotY = (cy + (evaluated.positionY / 600f) * (cy - 8f)).coerceIn(8f, size.height - 8f)
                            drawCircle(ExcevoColors.AccentPurple, radius = 6.dp.toPx(), center = Offset(dotX, dotY))
                            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(dotX, dotY))
                        }
                    }

                    // Synchronized X / Y / Z numeric fields
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompactNumericField(
                                label = "X",
                                value = xInput,
                                testTag = "motion_pos_x_input",
                                modifier = Modifier.weight(1f),
                                onValueChange = { raw ->
                                    xInput = raw
                                    raw.toFloatOrNull()?.coerceIn(-600f, 600f)?.let { parsed ->
                                        val next = draftTransform.copy(positionX = parsed)
                                        draftTransform = next
                                        onCommitTransform(next, "Set Position X")
                                    }
                                },
                            )
                            CompactNumericField(
                                label = "Y",
                                value = yInput,
                                testTag = "motion_pos_y_input",
                                modifier = Modifier.weight(1f),
                                onValueChange = { raw ->
                                    yInput = raw
                                    raw.toFloatOrNull()?.coerceIn(-600f, 600f)?.let { parsed ->
                                        val next = draftTransform.copy(positionY = parsed)
                                        draftTransform = next
                                        onCommitTransform(next, "Set Position Y")
                                    }
                                },
                            )
                            CompactNumericField(
                                label = "Z",
                                value = zInput,
                                testTag = "motion_z_move_input",
                                modifier = Modifier.weight(1f),
                                onValueChange = { raw ->
                                    zInput = raw
                                    raw.toFloatOrNull()?.coerceIn(-300f, 400f)?.let { parsed ->
                                        val next = draftTransform.copy(positionZ = parsed)
                                        draftTransform = next
                                        onCommitTransform(next, "Set Position Z")
                                    }
                                },
                            )
                        }
                        Text(
                            text = "Z adjusts 2.5D perspective scale & overlay stack order",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.TextMuted,
                        )
                    }
                }
            }

            MotionSubTab.Z_DEPTH -> {
                var zText by remember(clip.id, evaluated.positionZ) {
                    mutableStateOf(evaluated.positionZ.roundToInt().toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Z-Depth (2.5D Scale & Layer Order): ${evaluated.positionZ.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.AccentLavender,
                        )
                        Slider(
                            value = evaluated.positionZ.coerceIn(-300f, 400f),
                            onValueChange = { z ->
                                val next = draftTransform.copy(positionZ = z)
                                draftTransform = next
                                zText = z.roundToInt().toString()
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = {
                                onCommitTransform(draftTransform, "Adjust Z-Depth")
                            },
                            valueRange = -300f..400f,
                            colors = motionSliderColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .testTag("motion_z_slider"),
                        )
                    }
                    CompactNumericField(
                        label = "Z",
                        value = zText,
                        testTag = "motion_z_input",
                        modifier = Modifier.width(74.dp),
                        onValueChange = { raw ->
                            zText = raw
                            val parsed = raw.toFloatOrNull()?.coerceIn(-300f, 400f)
                            if (parsed != null) {
                                val next = draftTransform.copy(positionZ = parsed)
                                draftTransform = next
                                onCommitTransform(next, "Set Z-Depth")
                            }
                        },
                    )
                }
            }

            MotionSubTab.SCALE -> {
                var sxInput by remember(clip.id, evaluated.scaleX) {
                    mutableStateOf(String.format("%.2f", evaluated.scaleX))
                }
                var syInput by remember(clip.id, evaluated.scaleY) {
                    mutableStateOf(String.format("%.2f", evaluated.scaleY))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "X ${String.format("%.2fx", evaluated.scaleX)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.AccentLavender,
                            modifier = Modifier.width(58.dp),
                        )
                        Slider(
                            value = evaluated.scaleX.coerceIn(0.25f, 3.0f),
                            onValueChange = { sx ->
                                val next = if (uniformScale) {
                                    draftTransform.copy(scaleX = sx, scaleY = sx)
                                } else {
                                    draftTransform.copy(scaleX = sx)
                                }
                                draftTransform = next
                                sxInput = String.format("%.2f", sx)
                                if (uniformScale) syInput = sxInput
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = { onCommitTransform(draftTransform, "Scale X") },
                            valueRange = 0.25f..3.0f,
                            colors = motionSliderColors(),
                            modifier = Modifier.weight(1f).height(24.dp).testTag("motion_scale_x_slider"),
                        )
                        CompactNumericField(
                            label = "W",
                            value = sxInput,
                            testTag = "motion_scale_x_input",
                            modifier = Modifier.width(64.dp),
                            onValueChange = { raw ->
                                sxInput = raw
                                raw.toFloatOrNull()?.coerceIn(0.1f, 4f)?.let { parsed ->
                                    val next = if (uniformScale) {
                                        syInput = raw
                                        draftTransform.copy(scaleX = parsed, scaleY = parsed)
                                    } else {
                                        draftTransform.copy(scaleX = parsed)
                                    }
                                    draftTransform = next
                                    onCommitTransform(next, "Set Scale X")
                                }
                            },
                        )
                        IconButton(
                            onClick = { uniformScale = !uniformScale },
                            modifier = Modifier.size(28.dp).testTag("motion_uniform_scale_toggle"),
                        ) {
                            Icon(
                                imageVector = if (uniformScale) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                contentDescription = "Uniform Scale",
                                tint = if (uniformScale) ExcevoColors.AccentLavender else ExcevoColors.TextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (!uniformScale) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Y ${String.format("%.2fx", evaluated.scaleY)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.TextSecondary,
                                modifier = Modifier.width(58.dp),
                            )
                            Slider(
                                value = evaluated.scaleY.coerceIn(0.25f, 3.0f),
                                onValueChange = { sy ->
                                    val next = draftTransform.copy(scaleY = sy)
                                    draftTransform = next
                                    syInput = String.format("%.2f", sy)
                                    onPreviewTransform(next)
                                },
                                onValueChangeFinished = { onCommitTransform(draftTransform, "Scale Y") },
                                valueRange = 0.25f..3.0f,
                                colors = motionSliderColors(),
                                modifier = Modifier.weight(1f).height(24.dp).testTag("motion_scale_y_slider"),
                            )
                            CompactNumericField(
                                label = "H",
                                value = syInput,
                                testTag = "motion_scale_y_input",
                                modifier = Modifier.width(64.dp),
                                onValueChange = { raw ->
                                    syInput = raw
                                    raw.toFloatOrNull()?.coerceIn(0.1f, 4f)?.let { parsed ->
                                        val next = draftTransform.copy(scaleY = parsed)
                                        draftTransform = next
                                        onCommitTransform(next, "Set Scale Y")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            MotionSubTab.ROTATE -> {
                var rotInput by remember(clip.id, evaluated.rotationDeg) {
                    mutableStateOf(evaluated.rotationDeg.roundToInt().toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Angle ${evaluated.rotationDeg.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.AccentLavender,
                        modifier = Modifier.width(74.dp),
                    )
                    Slider(
                        value = evaluated.rotationDeg.coerceIn(-180f, 180f),
                        onValueChange = { deg ->
                            val next = draftTransform.copy(rotationDeg = deg)
                            draftTransform = next
                            rotInput = deg.roundToInt().toString()
                            onPreviewTransform(next)
                        },
                        onValueChangeFinished = { onCommitTransform(draftTransform, "Rotate clip") },
                        valueRange = -180f..180f,
                        colors = motionSliderColors(),
                        modifier = Modifier.weight(1f).height(28.dp).testTag("motion_rotate_slider"),
                    )
                    CompactNumericField(
                        label = "Deg",
                        value = rotInput,
                        testTag = "motion_rotate_input",
                        modifier = Modifier.width(72.dp),
                        onValueChange = { raw ->
                            rotInput = raw
                            raw.toFloatOrNull()?.coerceIn(-360f, 360f)?.let { parsed ->
                                val next = draftTransform.copy(rotationDeg = parsed)
                                draftTransform = next
                                onCommitTransform(next, "Set Rotation")
                            }
                        },
                    )
                }
            }

            MotionSubTab.SKEW -> {
                var skewXInput by remember(clip.id, evaluated.skewXDeg) {
                    mutableStateOf(evaluated.skewXDeg.roundToInt().toString())
                }
                var skewYInput by remember(clip.id, evaluated.skewYDeg) {
                    mutableStateOf(evaluated.skewYDeg.roundToInt().toString())
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Skew X ${evaluated.skewXDeg.roundToInt()}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.AccentLavender,
                            modifier = Modifier.width(72.dp),
                        )
                        Slider(
                            value = evaluated.skewXDeg.coerceIn(-45f, 45f),
                            onValueChange = { sx ->
                                val next = draftTransform.copy(skewXDeg = sx)
                                draftTransform = next
                                skewXInput = sx.roundToInt().toString()
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = { onCommitTransform(draftTransform, "Skew X") },
                            valueRange = -45f..45f,
                            colors = motionSliderColors(),
                            modifier = Modifier.weight(1f).height(24.dp).testTag("motion_skew_x_slider"),
                        )
                        CompactNumericField(
                            label = "X°",
                            value = skewXInput,
                            testTag = "motion_skew_x_input",
                            modifier = Modifier.width(64.dp),
                            onValueChange = { raw ->
                                skewXInput = raw
                                raw.toFloatOrNull()?.coerceIn(-45f, 45f)?.let { parsed ->
                                    val next = draftTransform.copy(skewXDeg = parsed)
                                    draftTransform = next
                                    onCommitTransform(next, "Set Skew X")
                                }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Skew Y ${evaluated.skewYDeg.roundToInt()}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.TextSecondary,
                            modifier = Modifier.width(72.dp),
                        )
                        Slider(
                            value = evaluated.skewYDeg.coerceIn(-45f, 45f),
                            onValueChange = { sy ->
                                val next = draftTransform.copy(skewYDeg = sy)
                                draftTransform = next
                                skewYInput = sy.roundToInt().toString()
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = { onCommitTransform(draftTransform, "Skew Y") },
                            valueRange = -45f..45f,
                            colors = motionSliderColors(),
                            modifier = Modifier.weight(1f).height(24.dp).testTag("motion_skew_y_slider"),
                        )
                        CompactNumericField(
                            label = "Y°",
                            value = skewYInput,
                            testTag = "motion_skew_y_input",
                            modifier = Modifier.width(64.dp),
                            onValueChange = { raw ->
                                skewYInput = raw
                                raw.toFloatOrNull()?.coerceIn(-45f, 45f)?.let { parsed ->
                                    val next = draftTransform.copy(skewYDeg = parsed)
                                    draftTransform = next
                                    onCommitTransform(next, "Set Skew Y")
                                }
                            },
                        )
                    }
                }
            }

            MotionSubTab.ANCHOR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Anchor X ${(evaluated.anchorX * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.AccentLavender)
                        Slider(
                            value = evaluated.anchorX.coerceIn(0f, 1f),
                            onValueChange = { ax ->
                                val next = draftTransform.copy(anchorX = ax)
                                draftTransform = next
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = { onCommitTransform(draftTransform, "Anchor X") },
                            colors = motionSliderColors(),
                            modifier = Modifier.height(24.dp).testTag("motion_anchor_x_slider"),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Anchor Y ${(evaluated.anchorY * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.AccentLavender)
                        Slider(
                            value = evaluated.anchorY.coerceIn(0f, 1f),
                            onValueChange = { ay ->
                                val next = draftTransform.copy(anchorY = ay)
                                draftTransform = next
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = { onCommitTransform(draftTransform, "Anchor Y") },
                            colors = motionSliderColors(),
                            modifier = Modifier.height(24.dp).testTag("motion_anchor_y_slider"),
                        )
                    }
                }
            }

            MotionSubTab.CROP -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crop H ${(draftTransform.cropLeft * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.AccentLavender)
                        Slider(
                            value = draftTransform.cropLeft.coerceIn(0f, 0.4f),
                            onValueChange = { c ->
                                val next = draftTransform.copy(cropLeft = c, cropRight = c)
                                draftTransform = next
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = { onCommitTransform(draftTransform, "Crop Horizontal") },
                            valueRange = 0f..0.4f,
                            colors = motionSliderColors(),
                            modifier = Modifier.height(24.dp).testTag("motion_crop_h_slider"),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crop V ${(draftTransform.cropTop * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.AccentLavender)
                        Slider(
                            value = draftTransform.cropTop.coerceIn(0f, 0.4f),
                            onValueChange = { c ->
                                val next = draftTransform.copy(cropTop = c, cropBottom = c)
                                draftTransform = next
                                onPreviewTransform(next)
                            },
                            onValueChangeFinished = { onCommitTransform(draftTransform, "Crop Vertical") },
                            valueRange = 0f..0.4f,
                            colors = motionSliderColors(),
                            modifier = Modifier.height(24.dp).testTag("motion_crop_v_slider"),
                        )
                    }
                }
            }

            MotionSubTab.SPEED -> {
                var draftSpeed by remember(clip.id, clip.speedMultiplier) {
                    mutableFloatStateOf(clip.speedMultiplier.coerceIn(0.25f, 4f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = String.format("%.2fx", draftSpeed),
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.PlayheadAmber,
                        modifier = Modifier.width(46.dp),
                    )
                    Slider(
                        value = draftSpeed,
                        onValueChange = { draftSpeed = it },
                        onValueChangeFinished = { onSetSpeed(draftSpeed) },
                        valueRange = 0.25f..4.0f,
                        colors = motionSliderColors(),
                        modifier = Modifier.weight(1f).height(26.dp).testTag("motion_speed_slider"),
                    )
                    listOf(0.5f, 1f, 2f).forEach { sp ->
                        Surface(
                            color = if (abs(draftSpeed - sp) < 0.05f) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    draftSpeed = sp
                                    onSetSpeed(sp)
                                },
                        ) {
                            Text("${sp}x", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNumericField(
    label: String,
    value: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ExcevoColors.AccentPurple,
            unfocusedBorderColor = ExcevoColors.HairlineBorder,
            focusedTextColor = ExcevoColors.TextPrimary,
            unfocusedTextColor = ExcevoColors.TextPrimary,
        ),
        modifier = modifier.testTag(testTag),
    )
}

@Composable
private fun XyzPill(label: String, value: String, highlight: Boolean = false) {
    Surface(
        color = ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (highlight) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ExcevoColors.AccentLavender)
            Text(value, style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
        }
    }
}

@Composable
private fun EasingBezierPreview(easing: EasingType, modifier: Modifier = Modifier) {
    Surface(
        color = ExcevoColors.VoidCharcoal,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = modifier,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h)
                when (easing) {
                    EasingType.LINEAR -> lineTo(w, 0f)
                    EasingType.EASE_IN -> cubicTo(w * 0.55f, h, w * 0.85f, h * 0.7f, w, 0f)
                    EasingType.EASE_OUT -> cubicTo(w * 0.15f, h * 0.3f, w * 0.45f, 0f, w, 0f)
                    EasingType.EASE_IN_OUT -> cubicTo(w * 0.45f, h, w * 0.55f, 0f, w, 0f)
                    EasingType.HOLD -> {
                        lineTo(w, h)
                        lineTo(w, 0f)
                    }
                }
            }
            drawPath(path, color = ExcevoColors.AccentLavender, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun motionSliderColors() = SliderDefaults.colors(
    thumbColor = ExcevoColors.AccentLavender,
    activeTrackColor = ExcevoColors.AccentPurple,
    inactiveTrackColor = ExcevoColors.HairlineBorder,
)
