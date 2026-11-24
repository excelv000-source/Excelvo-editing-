package com.example.ui.presets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.engine.PresetSchemaValidator
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.ProjectSummary
import com.example.ui.components.SpecBadge
import com.example.ui.theme.ExcevoColors

@Composable
fun PresetDetailPreviewDialog(
    preset: PresetDefinition,
    isSaved: Boolean,
    projects: List<ProjectSummary>,
    onToggleSave: () -> Unit,
    onFreeDownload: () -> Unit,
    onApplyToProject: (String, Float) -> Unit,
    onOpenProject: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var intensity by remember(preset.id) { mutableFloatStateOf(1.0f) }
    var showJsonExport by remember(preset.id) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = ExcevoColors.StudioPanel,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .testTag("preset_detail_modal"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Header: Title + Category badge + Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = ExcevoColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            SpecBadge(preset.category.label, highlight = true)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CreatorMiniAvatar(name = preset.creatorName)
                            Text(
                                text = "${preset.creatorName} · 100% Free",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.AccentLavender,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = ExcevoColors.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Visual Preview
                PresetRichThumbnail(
                    preset = preset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                )

                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ExcevoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Spec + Engine Version & Compatible Aspect Ratios
                Surface(
                    color = ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Spec: ${formatPresetSpecSummary(preset)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.PlayheadAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Engine v${preset.minEngineVersion}+ · Ratios: ${preset.compatibleAspectRatios.joinToString("·") { it.shortLabel }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Inline Preset Intensity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Intensity ${(intensity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.AccentLavender,
                    )
                    androidx.compose.material3.Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 0.1f..1.0f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = ExcevoColors.AccentLavender,
                            activeTrackColor = ExcevoColors.AccentPurple,
                            inactiveTrackColor = ExcevoColors.HairlineBorder,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_intensity_slider"),
                    )
                }

                // Save + Free Download + Open Latest actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onToggleSave,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSaved) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                        ),
                        modifier = Modifier
                            .weight(0.95f)
                            .height(36.dp)
                            .testTag("detail_save_button"),
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = ExcevoColors.AccentLavender,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSaved) "Saved" else "Save",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.AccentLavender,
                            maxLines = 1,
                        )
                    }

                    if (!preset.isBuiltIn) {
                        Button(
                            onClick = onFreeDownload,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExcevoColors.SuccessEmerald,
                                contentColor = ExcevoColors.VoidCharcoal,
                            ),
                            modifier = Modifier
                                .weight(1.45f)
                                .height(36.dp)
                                .testTag("detail_free_download_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Free Download", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    } else if (projects.isNotEmpty()) {
                        Button(
                            onClick = { onOpenProject(projects.first().id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(36.dp),
                        ) {
                            Text("Open Latest", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = { showJsonExport = !showJsonExport },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("detail_export_json_button"),
                    ) {
                        Text(
                            text = if (showJsonExport) "Hide" else "JSON",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }

                if (showJsonExport) {
                    val exportedJson = remember(preset.id) { PresetSchemaValidator.toExcevoJson(preset) }
                    Surface(
                        color = ExcevoColors.VoidCharcoal,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier.fillMaxWidth().testTag("preset_export_json_box"),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("excevo-preset/v1 JSON", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.AccentLavender)
                                TextButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(exportedJson)) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                ) {
                                    Text("Copy", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.SuccessEmerald)
                                }
                            }
                            Text(
                                text = exportedJson,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.TextSecondary,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Apply to sequence selector
                Text(
                    text = "Apply directly to a sequence:",
                    style = MaterialTheme.typography.labelLarge,
                    color = ExcevoColors.TextPrimary,
                )

                if (projects.isEmpty()) {
                    Text(
                        text = "Create a project with video or image clips first to apply this preset.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExcevoColors.TextMuted,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        projects.forEach { proj ->
                            Surface(
                                color = ExcevoColors.ElevatedSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onApplyToProject(proj.id, intensity) }
                                    .testTag("apply_preset_to_${proj.id}"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = ExcevoColors.AccentLavender,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = proj.name,
                                            color = ExcevoColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = "Apply (${proj.clipCount})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExcevoColors.AccentLavender,
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

private fun formatPresetSpecSummary(preset: PresetDefinition): String = when (preset.category) {
    PresetCategory.SHAKES -> "Amplitude ${preset.shakeAmplitude}px · Frequency ${preset.shakeFrequencyHz} Hz"
    PresetCategory.TRANSITIONS -> "Style ${preset.transitionStyle ?: "Cross"} · Duration ${preset.transitionDurationMs} ms"
    PresetCategory.ONE_FRAME -> "1F Impulse ${preset.oneFrameStyle ?: "Flash"} · Intensity ${(preset.effectIntensity * 100).toInt()}%"
    PresetCategory.TEXT_ANIMATIONS -> "Typography Motion ${preset.textAnimationStyle ?: "Kinetic"}"
    PresetCategory.MOTION -> "Keyframe Channels: ${preset.motionKeyframes.keys.joinToString(", ") { it.label }.ifBlank { "Transform" }}"
    PresetCategory.EFFECTS -> "FX ${preset.effectName ?: "Custom"} · Intensity ${(preset.effectIntensity * 100).toInt()}%"
    else -> {
        val g = preset.colorGrade
        "Exp ${g.exposure} · Con ${g.contrast} · Sat ${g.saturation} · Temp ${g.temperature} · Tint ${g.tint}"
    }
}
