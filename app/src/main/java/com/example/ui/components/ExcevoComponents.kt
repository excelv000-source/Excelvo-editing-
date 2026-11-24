package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.ProjectSummary
import com.example.ui.theme.ExcevoColors

@Composable
fun ExcevoMarkCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val spine = Path().apply {
            moveTo(w * 0.14f, h * 0.14f)
            lineTo(w * 0.32f, h * 0.14f)
            lineTo(w * 0.32f, h * 0.86f)
            lineTo(w * 0.14f, h * 0.86f)
            close()
        }
        val topArm = Path().apply {
            moveTo(w * 0.14f, h * 0.14f)
            lineTo(w * 0.78f, h * 0.14f)
            lineTo(w * 0.65f, h * 0.29f)
            lineTo(w * 0.14f, h * 0.29f)
            close()
        }
        val bottomArm = Path().apply {
            moveTo(w * 0.14f, h * 0.71f)
            lineTo(w * 0.65f, h * 0.71f)
            lineTo(w * 0.78f, h * 0.86f)
            lineTo(w * 0.14f, h * 0.86f)
            close()
        }
        val playBlade = Path().apply {
            moveTo(w * 0.40f, h * 0.34f)
            lineTo(w * 0.90f, h * 0.50f)
            lineTo(w * 0.40f, h * 0.66f)
            lineTo(w * 0.48f, h * 0.50f)
            close()
        }
        drawPath(spine, color = ExcevoColors.TextPrimary)
        drawPath(topArm, color = ExcevoColors.TextPrimary)
        drawPath(bottomArm, color = ExcevoColors.TextSecondary)
        drawPath(playBlade, color = ExcevoColors.AccentPurple)
    }
}

@Composable
fun StudioBackdropCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    ExcevoColors.VoidCharcoal,
                    ExcevoColors.StudioPanel,
                    ExcevoColors.AccentPurpleContainer.copy(alpha = 0.65f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        )
        val step = 28.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = ExcevoColors.HairlineBorder.copy(alpha = 0.35f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = ExcevoColors.HairlineBorder.copy(alpha = 0.25f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
    }
}

/**
 * Draws an accurate scaled rectangular wireframe glyph matching the exact aspect ratio
 * (16:9, 9:16, 1:1, 4:5, 4:3, 3:4, 21:9) with inner highlight fill and framing corners.
 */
@Composable
fun AspectRatioGlyph(
    aspectRatio: AspectRatioPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val frameStrokeColor = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary
    val frameFillColor = if (selected) {
        ExcevoColors.AccentPurple.copy(alpha = 0.30f)
    } else {
        ExcevoColors.StudioPanel.copy(alpha = 0.85f)
    }
    Canvas(modifier = modifier.size(30.dp)) {
        val ratio = aspectRatio.aspectFloat.coerceIn(0.35f, 2.8f)
        val maxBox = size.minDimension * 0.86f
        val rectW: Float
        val rectH: Float
        if (ratio >= 1f) {
            rectW = maxBox
            rectH = (maxBox / ratio).coerceAtLeast(size.minDimension * 0.28f)
        } else {
            rectH = maxBox
            rectW = (maxBox * ratio).coerceAtLeast(size.minDimension * 0.28f)
        }
        val left = (size.width - rectW) / 2f
        val top = (size.height - rectH) / 2f
        val corner = 3.dp.toPx()

        drawRoundRect(
            color = frameFillColor,
            topLeft = Offset(left, top),
            size = Size(rectW, rectH),
            cornerRadius = CornerRadius(corner, corner),
        )
        drawRoundRect(
            color = frameStrokeColor,
            topLeft = Offset(left, top),
            size = Size(rectW, rectH),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = if (selected) 2.2f else 1.5f),
        )
        if (selected) {
            val dotR = 2.dp.toPx()
            drawCircle(
                color = ExcevoColors.AccentLavender,
                radius = dotR,
                center = Offset(left + rectW / 2f, top + rectH / 2f),
            )
        }
    }
}

@Composable
fun SpecBadge(
    label: String,
    highlight: Boolean = false,
) {
    Surface(
        color = if (highlight) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlight) ExcevoColors.AccentPurple.copy(alpha = 0.55f) else ExcevoColors.HairlineBorder,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

data class CreateProjectRequest(
    val name: String,
    val aspectRatio: AspectRatioPreset,
    val frameRate: FrameRatePreset,
    val targetResolution: ExportResolutionPreset,
    val scaleMode: com.example.core.model.CanvasScaleMode = com.example.core.model.CanvasScaleMode.FIT,
    val backgroundId: String = com.example.core.model.CanvasBackgroundSpec.PURE_BLACK.id,
    val customWidthPx: Int? = null,
    val customHeightPx: Int? = null,
)

private enum class CreateDialogTab(val label: String) {
    PROJECT("PROJECT"),
    ELEMENTS("ELEMENTS"),
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (CreateProjectRequest) -> Unit,
    onImportVideoElement: ((CreateProjectRequest) -> Unit)? = null,
    onImportImagesElement: ((CreateProjectRequest) -> Unit)? = null,
    onImportAudioElement: ((CreateProjectRequest) -> Unit)? = null,
) {
    var activeTab by remember { mutableStateOf(CreateDialogTab.PROJECT) }
    var name by remember { mutableStateOf("") }
    var selectedAspect by remember { mutableStateOf(AspectRatioPreset.RATIO_9_16) }
    var selectedFps by remember { mutableStateOf(FrameRatePreset.FPS_30) }
    var selectedResolution by remember { mutableStateOf(ExportResolutionPreset.FHD_1080P) }
    var selectedScaleMode by remember { mutableStateOf(com.example.core.model.CanvasScaleMode.FIT) }
    var selectedBackground by remember { mutableStateOf(com.example.core.model.CanvasBackgroundSpec.PURE_BLACK) }
    var isCustomizeOpen by remember { mutableStateOf(false) }
    var customWidthText by remember { mutableStateOf("1080") }
    var customHeightText by remember { mutableStateOf("1920") }

    val customW = if (isCustomizeOpen) customWidthText.toIntOrNull()?.coerceIn(240, 4096) else null
    val customH = if (isCustomizeOpen) customHeightText.toIntOrNull()?.coerceIn(240, 4096) else null
    val (outWidth, outHeight) = if (customW != null && customH != null) {
        customW to customH
    } else {
        selectedResolution.dimensionsFor(selectedAspect)
    }

    fun buildRequest(): CreateProjectRequest = CreateProjectRequest(
        name = name,
        aspectRatio = selectedAspect,
        frameRate = selectedFps,
        targetResolution = selectedResolution,
        scaleMode = selectedScaleMode,
        backgroundId = selectedBackground.id,
        customWidthPx = customW,
        customHeightPx = customH,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = ExcevoColors.StudioPanel,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Header + PROJECT / ELEMENTS tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.create_project_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = ExcevoColors.TextPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CreateDialogTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            Surface(
                                color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { activeTab = tab }
                                    .testTag("create_tab_${tab.name}"),
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = ExcevoColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (activeTab == CreateDialogTab.ELEMENTS) {
                    Text(
                        text = "Start with media elements using your active ${outWidth}×${outHeight} (${selectedBackground.label}) canvas:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExcevoColors.TextSecondary,
                    )
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val req = buildRequest()
                                onImportVideoElement?.invoke(req) ?: onCreate(req)
                            }
                            .testTag("elements_import_video_button"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Import Video Clip", style = MaterialTheme.typography.titleSmall, color = ExcevoColors.TextPrimary)
                            Text("Pick video from device gallery & open timeline", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextSecondary)
                        }
                    }
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val req = buildRequest()
                                onImportImagesElement?.invoke(req) ?: onCreate(req)
                            }
                            .testTag("elements_import_images_button"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Import Photo Sequence", style = MaterialTheme.typography.titleSmall, color = ExcevoColors.TextPrimary)
                            Text("Combine up to 12 photos with color grading", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextSecondary)
                        }
                    }
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val req = buildRequest()
                                onImportAudioElement?.invoke(req) ?: onCreate(req)
                            }
                            .testTag("elements_import_audio_button"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Import Audio Track", style = MaterialTheme.typography.titleSmall, color = ExcevoColors.TextPrimary)
                            Text("Add MP3, WAV, AAC, or M4A music onto A1 waveform track", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextSecondary)
                        }
                    }
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCreate(buildRequest()) }
                            .testTag("elements_blank_sequence_button"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Create Empty Canvas Sequence", style = MaterialTheme.typography.titleSmall, color = ExcevoColors.TextPrimary)
                            Text("Open Studio directly with ${selectedAspect.shortLabel} @ ${selectedFps.fps}fps", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextSecondary)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.project_name_label)) },
                        placeholder = { Text(stringResource(R.string.project_name_placeholder)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ExcevoColors.AccentPurple,
                            unfocusedBorderColor = ExcevoColors.HairlineBorder,
                            focusedLabelColor = ExcevoColors.AccentLavender,
                            unfocusedLabelColor = ExcevoColors.TextSecondary,
                            focusedTextColor = ExcevoColors.TextPrimary,
                            unfocusedTextColor = ExcevoColors.TextPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_name_input"),
                    )

                    // Compact Live Project Preview & Background Swatches
                    Surface(
                        color = ExcevoColors.VoidCharcoal,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_live_preview_card"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    color = androidx.compose.ui.graphics.Color(selectedBackground.colorArgb),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.7f)),
                                    modifier = Modifier.size(width = 28.dp, height = 22.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AspectRatioGlyph(
                                            aspectRatio = selectedAspect,
                                            selected = true,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "${outWidth}×${outHeight} · ${selectedFps.fps}fps · ${selectedScaleMode.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExcevoColors.AccentLavender,
                                    )
                                    Text(
                                        text = "BG: ${selectedBackground.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExcevoColors.TextMuted,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                com.example.core.model.CanvasBackgroundSpec.entries.forEach { bg ->
                                    val isBgSelected = bg == selectedBackground
                                    Surface(
                                        color = androidx.compose.ui.graphics.Color(bg.colorArgb),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        border = BorderStroke(
                                            if (isBgSelected) 2.dp else 1.dp,
                                            if (isBgSelected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                                        ),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .clickable { selectedBackground = bg }
                                            .testTag("bg_option_${bg.id}"),
                                    ) {}
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AspectRatioPreset.entries.forEach { preset ->
                            val selected = preset == selectedAspect
                            Surface(
                                color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedAspect = preset
                                        if (!isCustomizeOpen) {
                                            val (dw, dh) = selectedResolution.dimensionsFor(preset)
                                            customWidthText = dw.toString()
                                            customHeightText = dh.toString()
                                        }
                                    }
                                    .testTag("aspect_option_${preset.id}"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    AspectRatioGlyph(
                                        aspectRatio = preset,
                                        selected = selected,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = preset.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.HighQuality,
                                contentDescription = null,
                                tint = ExcevoColors.AccentLavender,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.resolution_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = ExcevoColors.TextPrimary,
                            )
                        }
                        Surface(
                            color = if (isCustomizeOpen) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isCustomizeOpen) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isCustomizeOpen = !isCustomizeOpen }
                                .testTag("customize_resolution_toggle"),
                        ) {
                            Text(
                                text = if (isCustomizeOpen) "Customize: On" else "Customize",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCustomizeOpen) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if (isCustomizeOpen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = customWidthText,
                                onValueChange = { customWidthText = it.filter(Char::isDigit).take(4) },
                                label = { Text("W px") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_width_input"),
                            )
                            OutlinedTextField(
                                value = customHeightText,
                                onValueChange = { customHeightText = it.filter(Char::isDigit).take(4) },
                                label = { Text("H px") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_height_input"),
                            )
                            com.example.core.model.CanvasScaleMode.entries.forEach { mode ->
                                val modeSel = mode == selectedScaleMode
                                Surface(
                                    color = if (modeSel) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (modeSel) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedScaleMode = mode }
                                        .testTag("create_scale_mode_${mode.name}"),
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (modeSel) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExportResolutionPreset.entries.forEach { res ->
                                val selected = res == selectedResolution
                                Surface(
                                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedResolution = res
                                            val (dw, dh) = res.dimensionsFor(selectedAspect)
                                            customWidthText = dw.toString()
                                            customHeightText = dh.toString()
                                        }
                                        .testTag("resolution_option_${res.id}"),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = res.shortLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FrameRatePreset.entries.forEach { preset ->
                            val selected = preset == selectedFps
                            Surface(
                                color = if (selected) ExcevoColors.PlayheadAmberContainer else ExcevoColors.ElevatedSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ExcevoColors.PlayheadAmber else ExcevoColors.HairlineBorder,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedFps = preset }
                                    .testTag("fps_option_${preset.fps}"),
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) ExcevoColors.PlayheadAmber else ExcevoColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel), color = ExcevoColors.TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onCreate(buildRequest()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExcevoColors.AccentPurple,
                                contentColor = ExcevoColors.TextPrimary,
                            ),
                            modifier = Modifier.testTag("confirm_create_project_button"),
                        ) {
                            Text(stringResource(R.string.create_and_open))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameProjectDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = { Text("Rename Sequence", color = ExcevoColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ExcevoColors.AccentPurple,
                    unfocusedBorderColor = ExcevoColors.HairlineBorder,
                    focusedTextColor = ExcevoColors.TextPrimary,
                    unfocusedTextColor = ExcevoColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExcevoColors.AccentPurple,
                    contentColor = ExcevoColors.TextPrimary,
                ),
            ) {
                Text(stringResource(R.string.confirm))
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
fun DeleteProjectDialog(
    target: ProjectSummary,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Text(
                text = "Delete Sequence?",
                style = MaterialTheme.typography.titleLarge,
                color = ExcevoColors.TextPrimary,
            )
        },
        text = {
            Text(
                text = "This removes \"${target.name}\" and its cached project bundle from local storage. Source media in your device gallery is not touched.",
                style = MaterialTheme.typography.bodyMedium,
                color = ExcevoColors.TextSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.DangerRed),
                modifier = Modifier.testTag("confirm_delete_project_button"),
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcevoColors.TextSecondary)
            }
        },
    )
}
