package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.core.engine.TimelineMath
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import com.example.ui.theme.ExcevoColors
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MultiTrackTimelineArea(
    project: TimelineProject,
    playheadUs: Long,
    selectedClipId: String?,
    pxPerSecond: Float,
    showImportFab: Boolean = true,
    onSeekUs: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onZoomChange: (Float) -> Unit,
    onAddTrack: (TrackType) -> Unit = {},
    onRemoveTrack: (String) -> Unit = {},
    onToggleTrackVisibility: (String) -> Unit = {},
    onImportMedia: () -> Unit = {},
    onTrimClipDrag: (clipId: String, newSourceStartUs: Long, newSourceEndUs: Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val totalDurationUs = max(project.totalDurationUs(), 10_000_000L)
    val density = LocalDensity.current
    val pxPerSecInPx = with(density) { pxPerSecond.dp.toPx() }
    val contentWidthDp = with(density) {
        (TimelineMath.usToPx(totalDurationUs + 4_000_000L, pxPerSecInPx) / density.density).dp.coerceAtLeast(360.dp)
    }
    val scrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    var showAddTrackMenu by remember { mutableStateOf(false) }

    // Scroll -> Playhead scrub (drag timeline under fixed center red needle)
    LaunchedEffect(scrollState, pxPerSecInPx, project.id) {
        snapshotFlow { scrollState.value to scrollState.isScrollInProgress }
            .collect { (scrollPx, scrolling) ->
                if (scrolling) {
                    val targetUs = TimelineMath.pxToUs(scrollPx.toFloat(), pxPerSecInPx)
                        .coerceIn(0L, project.totalDurationUs().coerceAtLeast(0L))
                    onSeekUs(targetUs)
                }
            }
    }

    // Playhead -> Scroll align (keep current timecode locked directly under centered red needle)
    LaunchedEffect(playheadUs, pxPerSecInPx, scrollState.maxValue) {
        if (!scrollState.isScrollInProgress) {
            val desiredPx = TimelineMath.usToPx(playheadUs, pxPerSecInPx).roundToInt()
                .coerceIn(0, scrollState.maxValue.coerceAtLeast(0))
            if (abs(scrollState.value - desiredPx) > 1) {
                scrollState.scrollTo(desiredPx)
            }
        }
    }

    val displayTracks = remember(project.tracks) { buildDisplayTrackOrder(project.tracks) }
    val audioTrackCount = remember(project.tracks) { project.tracks.count { it.type == TrackType.AUDIO } }

    Surface(
        color = ExcevoColors.TimelineBg,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Timeline Header Bar with + Track & Zoom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ExcevoColors.StudioPanel)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TIMELINE · ${project.tracks.size}T · ${project.clipCount()}C",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.TextSecondary,
                    )
                    Box {
                        Surface(
                            color = ExcevoColors.AccentPurpleContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showAddTrackMenu = true }
                                .testTag("add_track_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Add Track",
                                    tint = ExcevoColors.AccentLavender,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text("Track", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.AccentLavender)
                            }
                        }
                        DropdownMenu(
                            expanded = showAddTrackMenu,
                            onDismissRequest = { showAddTrackMenu = false },
                            containerColor = ExcevoColors.ElevatedSurface,
                        ) {
                            listOf(
                                TrackType.OVERLAY to "Add Overlay Track (V)",
                                TrackType.TEXT to "Add Text Track (T)",
                                TrackType.ADJUSTMENT to "Add Adjustment Track (E)",
                                TrackType.AUDIO to "Add Audio Track (A)",
                            ).forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = ExcevoColors.TextPrimary) },
                                    onClick = {
                                        showAddTrackMenu = false
                                        onAddTrack(type)
                                    },
                                    modifier = Modifier.testTag("add_track_${type.name}"),
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = { onZoomChange(pxPerSecond - 24f) },
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("timeline_zoom_out"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomOut,
                            contentDescription = "Zoom out timeline",
                            tint = ExcevoColors.TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = "${(pxPerSecond / 56f * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.AccentLavender,
                    )
                    IconButton(
                        onClick = { onZoomChange(pxPerSecond + 24f) },
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("timeline_zoom_in"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomIn,
                            contentDescription = "Zoom in timeline",
                            tint = ExcevoColors.TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // Full-height timeline viewport with fixed screen-center RED Playhead line
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val halfViewportDp = maxWidth / 2f
                val viewportHeightPx = constraints.maxHeight.toFloat()

                LaunchedEffect(selectedClipId, viewportHeightPx, displayTracks) {
                    if (selectedClipId != null && viewportHeightPx > 0f) {
                        val gapPx = with(density) { 3.dp.toPx() }
                        var yCursorPx = with(density) { 25.dp.toPx() }
                        for ((track, _) in displayTracks) {
                            val laneHeightPx = with(density) { trackLaneHeightDp(track).toPx() }
                            if (track.clips.any { it.id == selectedClipId }) {
                                val trackBottomPx = yCursorPx + laneHeightPx
                                val curScroll = verticalScrollState.value.toFloat()
                                if (trackBottomPx > curScroll + viewportHeightPx) {
                                    verticalScrollState.scrollTo((trackBottomPx - viewportHeightPx).roundToInt().coerceAtLeast(0))
                                } else if (yCursorPx < curScroll) {
                                    verticalScrollState.scrollTo(yCursorPx.roundToInt().coerceAtLeast(0))
                                }
                                break
                            }
                            yCursorPx += laneHeightPx + gapPx
                        }
                    }
                }

                // Scrollable ruler + ALL tracks passing beneath fixed center playhead
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
                ) {
                    Spacer(modifier = Modifier.width(halfViewportDp))

                    Column(
                        modifier = Modifier
                            .width(contentWidthDp)
                            .fillMaxHeight()
                            .verticalScroll(verticalScrollState),
                    ) {
                        TimelineRuler(
                            totalDurationUs = totalDurationUs,
                            pxPerSecondDp = pxPerSecond,
                            onSeekUs = onSeekUs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .testTag("timeline_ruler"),
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        displayTracks.forEach { (track, shortTag) ->
                            val laneHeight = trackLaneHeightDp(track)
                            val canRemove = track.type != TrackType.VIDEO_PRIMARY &&
                                !(track.type == TrackType.AUDIO && audioTrackCount <= 1) &&
                                track.id != TimelineProject.OVERLAY_TRACK_ID &&
                                track.id != TimelineProject.TEXT_TRACK_ID
                            TrackLaneRow(
                                track = track,
                                trackLabel = shortTag,
                                assets = project.assets,
                                selectedClipId = selectedClipId,
                                pxPerSecondDp = pxPerSecond,
                                canRemove = canRemove,
                                onSelectClip = onSelectClip,
                                onRemoveTrack = { onRemoveTrack(track.id) },
                                onTrimClipDrag = onTrimClipDrag,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(laneHeight)
                                    .testTag("timeline_track_${track.id}"),
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(halfViewportDp))
                }

                // Left-edge Track Controls locked vertically to verticalScrollState.value
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight()
                        .clipToBounds()
                        .zIndex(20f),
                ) {
                    Column(
                        modifier = Modifier
                            .wrapContentHeight(unbounded = true, align = Alignment.Top)
                            .offset { IntOffset(0, -verticalScrollState.value) }
                            .padding(top = 25.dp, start = 6.dp),
                    ) {
                        displayTracks.forEach { (track, shortTag) ->
                            val laneHeight = trackLaneHeightDp(track)
                            Box(
                                modifier = Modifier.height(laneHeight),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Surface(
                                    color = ExcevoColors.ElevatedSurface.copy(alpha = 0.90f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onToggleTrackVisibility(track.id) }
                                                .padding(horizontal = 3.dp, vertical = 3.dp)
                                                .testTag("track_visibility_${track.id}"),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = if (track.isHidden || track.isMuted) {
                                                    Icons.Outlined.VisibilityOff
                                                } else {
                                                    Icons.Outlined.Visibility
                                                },
                                                contentDescription = if (track.isHidden) "Show ${track.name}" else "Hide ${track.name}",
                                                tint = if (track.isHidden || track.isMuted) {
                                                    ExcevoColors.TextMuted
                                                } else {
                                                    ExcevoColors.TextPrimary
                                                },
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Text(
                                            text = shortTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (track.type == TrackType.VIDEO_PRIMARY) {
                                                ExcevoColors.AccentLavender
                                            } else {
                                                ExcevoColors.TextSecondary
                                            },
                                        )
                                        val canRemove = track.type != TrackType.VIDEO_PRIMARY &&
                                            !(track.type == TrackType.AUDIO && audioTrackCount <= 1) &&
                                            track.id != TimelineProject.OVERLAY_TRACK_ID &&
                                            track.id != TimelineProject.TEXT_TRACK_ID
                                        if (canRemove) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Remove ${track.name}",
                                                tint = ExcevoColors.TextMuted,
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clickable { onRemoveTrack(track.id) }
                                                    .testTag("remove_track_${track.id}"),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }

                // MANDATORY: Continuous full-height fixed center RED Playhead needle across ruler + ALL tracks
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight()
                        .width(12.dp)
                        .zIndex(50f)
                        .testTag("timeline_center_playhead"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cap = Path().apply {
                            moveTo(cx - 5.dp.toPx(), 0f)
                            lineTo(cx + 5.dp.toPx(), 0f)
                            lineTo(cx + 5.dp.toPx(), 6.dp.toPx())
                            lineTo(cx, 11.dp.toPx())
                            lineTo(cx - 5.dp.toPx(), 6.dp.toPx())
                            close()
                        }
                        drawPath(cap, color = ExcevoColors.DangerRed)
                        drawLine(
                            color = ExcevoColors.DangerRed,
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }

                // Bottom-Right Floating + Import Media FAB (Screenshot 1)
                if (showImportFab) {
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            width = 1.8.dp,
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(ExcevoColors.AccentPurple, ExcevoColors.SuccessEmerald)
                            ),
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 8.dp)
                            .size(44.dp)
                            .zIndex(60f)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onImportMedia)
                            .testTag("timeline_fab_import"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Add Layer or Media",
                                tint = ExcevoColors.SuccessEmerald,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun trackLaneHeightDp(track: TimelineTrack): Dp = when (track.type) {
    TrackType.VIDEO_PRIMARY -> 42.dp
    TrackType.AUDIO -> 34.dp
    else -> if (track.clips.isEmpty()) 24.dp else 34.dp
}

private fun buildDisplayTrackOrder(tracks: List<TimelineTrack>): List<Pair<TimelineTrack, String>> {
    val upperVisual = tracks.filter { tr ->
        val isUpperType = tr.type == TrackType.OVERLAY || tr.type == TrackType.TEXT || tr.type == TrackType.ADJUSTMENT
        val isDefaultEmpty = tr.clips.isEmpty() &&
            (tr.id == TimelineProject.OVERLAY_TRACK_ID || tr.id == TimelineProject.TEXT_TRACK_ID)
        isUpperType && !isDefaultEmpty
    }
    val primary = tracks.filter { it.type == TrackType.VIDEO_PRIMARY }
    val audio = tracks.filter { it.type == TrackType.AUDIO }

    val result = mutableListOf<Pair<TimelineTrack, String>>()
    var overlayCounter = 2
    var textCounter = 1
    var adjustCounter = 1
    upperVisual.reversed().forEach { tr ->
        val label = when (tr.type) {
            TrackType.OVERLAY -> "V${overlayCounter++}"
            TrackType.TEXT -> "T${textCounter++}"
            TrackType.ADJUSTMENT -> "E${adjustCounter++}"
            else -> tr.name.take(3)
        }
        result.add(tr to label)
    }
    primary.forEachIndexed { idx, tr ->
        result.add(tr to if (idx == 0) "V1" else "V1.${idx + 1}")
    }
    audio.forEachIndexed { idx, tr ->
        result.add(tr to "A${idx + 1}")
    }
    return result
}

@Composable
private fun TimelineRuler(
    totalDurationUs: Long,
    pxPerSecondDp: Float,
    onSeekUs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pxPerSecInPx = with(density) { pxPerSecondDp.dp.toPx() }

    Box(
        modifier = modifier
            .background(ExcevoColors.VoidCharcoal.copy(alpha = 0.72f))
            .pointerInput(pxPerSecInPx) {
                detectTapGestures { offset ->
                    val targetUs = TimelineMath.pxToUs(offset.x.coerceAtLeast(0f), pxPerSecInPx)
                    onSeekUs(targetUs)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalSeconds = ((totalDurationUs / 1_000_000L) + 4).toInt()
            for (sec in 0..totalSeconds) {
                val x = sec * pxPerSecInPx
                if (x > size.width) break
                drawLine(
                    color = ExcevoColors.TextSecondary.copy(alpha = 0.72f),
                    start = Offset(x, size.height * 0.34f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.8f,
                )
                for (sub in 1..4) {
                    val subX = x + pxPerSecInPx * (sub / 5f)
                    if (subX <= size.width) {
                        drawLine(
                            color = ExcevoColors.HairlineBorder.copy(alpha = 0.85f),
                            start = Offset(subX, size.height * 0.64f),
                            end = Offset(subX, size.height),
                            strokeWidth = 1f,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackLaneRow(
    track: TimelineTrack,
    trackLabel: String,
    assets: Map<String, MediaAsset>,
    selectedClipId: String?,
    pxPerSecondDp: Float,
    canRemove: Boolean,
    onSelectClip: (String) -> Unit,
    onRemoveTrack: () -> Unit,
    onTrimClipDrag: (clipId: String, newSourceStartUs: Long, newSourceEndUs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pxPerSecInPx = with(density) { pxPerSecondDp.dp.toPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ExcevoColors.TrackSurface)
            .border(1.dp, ExcevoColors.SubtleDivider, RoundedCornerShape(8.dp)),
    ) {
        if (track.clips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = when (track.type) {
                        TrackType.AUDIO -> "$trackLabel · Audio track ready"
                        TrackType.VIDEO_PRIMARY -> "$trackLabel · Import clips below to build sequence"
                        else -> "$trackLabel · ${track.name}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.TextMuted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else {
            track.clips.forEach { clip ->
                val asset = assets[clip.assetId]
                val isAccessible = asset?.isAccessible != false
                val startPx = TimelineMath.usToPx(clip.timelineStartUs, pxPerSecInPx)
                val widthDp: Dp = with(density) {
                    (TimelineMath.usToPx(clip.timelineDurationUs, pxPerSecInPx) / density.density).dp
                }.coerceAtLeast(44.dp)
                val isSelected = clip.id == selectedClipId
                val baseTint = when {
                    clip.groupId != null && isSelected -> ExcevoColors.ClipSelectedCoral
                    track.type == TrackType.OVERLAY || track.type == TrackType.TEXT || track.type == TrackType.ADJUSTMENT -> ExcevoColors.ClipImageTint
                    clip.mediaType == MediaType.VIDEO -> ExcevoColors.ClipVideoTint
                    clip.mediaType == MediaType.IMAGE -> ExcevoColors.ClipImageTint
                    clip.mediaType == MediaType.AUDIO -> ExcevoColors.ClipAudioTint
                    else -> ExcevoColors.ClipVideoTint
                }
                val clipTint = if (track.isHidden || track.isMuted) baseTint.copy(alpha = 0.42f) else baseTint
                val hasFxOrGrade = !clip.colorGrade.isNeutral() || clip.effectStack.any { it.enabled } || clip.keyframes.isNotEmpty()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(startPx.roundToInt(), 0) }
                        .width(widthDp)
                        .fillMaxHeight()
                        .padding(vertical = 3.dp, horizontal = 1.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(clipTint)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = when {
                                !isAccessible -> ExcevoColors.DangerRed
                                isSelected -> ExcevoColors.AccentPurple
                                else -> ExcevoColors.HairlineBorder
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelectClip(clip.id) }
                        .testTag("timeline_clip_${clip.id}"),
                ) {
                    val filmstripFiles = remember(asset?.filmstripPaths, clip.sourceStartUs, clip.sourceEndUs) {
                        val paths = asset?.filmstripPaths.orEmpty()
                            .map { File(it) }
                            .filter { it.exists() }
                        if (paths.size <= 1 || clip.sourceTotalDurationUs <= 0L) {
                            paths
                        } else {
                            val startFrac = (clip.sourceStartUs.toFloat() / clip.sourceTotalDurationUs).coerceIn(0f, 1f)
                            val endFrac = (clip.sourceEndUs.toFloat() / clip.sourceTotalDurationUs).coerceIn(startFrac, 1f)
                            val startIdx = (startFrac * paths.size).toInt().coerceIn(0, paths.lastIndex)
                            val endIdx = ((endFrac * paths.size).roundToInt()).coerceIn(startIdx + 1, paths.size)
                            paths.subList(startIdx, endIdx)
                        }
                    }

                    if (clip.mediaType == MediaType.AUDIO) {
                        val samples = remember(asset?.waveformSamples, clip.sourceStartUs, clip.sourceEndUs) {
                            val raw = asset?.waveformSamples.orEmpty()
                            if (raw.isEmpty() || clip.sourceTotalDurationUs <= 0L) {
                                raw
                            } else {
                                val startFrac = (clip.sourceStartUs.toFloat() / clip.sourceTotalDurationUs).coerceIn(0f, 1f)
                                val endFrac = (clip.sourceEndUs.toFloat() / clip.sourceTotalDurationUs).coerceIn(startFrac, 1f)
                                val startIdx = (startFrac * raw.size).toInt().coerceIn(0, raw.lastIndex)
                                val endIdx = ((endFrac * raw.size).roundToInt()).coerceIn(startIdx + 1, raw.size)
                                raw.subList(startIdx, endIdx)
                            }
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("audio_waveform_${clip.id}"),
                        ) {
                            val centerY = size.height / 2f
                            if (samples.isNotEmpty()) {
                                val barCount = samples.size
                                val stepX = size.width / barCount.coerceAtLeast(1)
                                val barW = (stepX * 0.62f).coerceIn(1.5f, 6f)
                                val barColor = if (clip.isMuted) {
                                    ExcevoColors.TextMuted.copy(alpha = 0.45f)
                                } else {
                                    ExcevoColors.AccentLavender.copy(alpha = 0.65f)
                                }
                                samples.forEachIndexed { idx, amp ->
                                    val h = (amp.coerceIn(0.06f, 1f) * size.height * 0.88f).coerceAtLeast(3f)
                                    val x = idx * stepX + (stepX - barW) / 2f
                                    drawLine(
                                        color = barColor,
                                        start = Offset(x, centerY - h / 2f),
                                        end = Offset(x, centerY + h / 2f),
                                        strokeWidth = barW,
                                    )
                                }
                            }
                            // Fade In & Fade Out envelope overlay
                            val dur = clip.timelineDurationUs.coerceAtLeast(1L).toFloat()
                            val fadeInFrac = (clip.fadeInUs / dur).coerceIn(0f, 0.5f)
                            val fadeOutFrac = (clip.fadeOutUs / dur).coerceIn(0f, 0.5f)
                            if (fadeInFrac > 0.005f) {
                                val endX = size.width * fadeInFrac
                                val path = Path().apply {
                                    moveTo(0f, size.height)
                                    lineTo(0f, 0f)
                                    lineTo(endX, 0f)
                                    close()
                                }
                                drawPath(path, Color.Black.copy(alpha = 0.42f))
                                drawLine(ExcevoColors.PlayheadAmber, Offset(0f, size.height), Offset(endX, 2f), 2f)
                            }
                            if (fadeOutFrac > 0.005f) {
                                val startX = size.width * (1f - fadeOutFrac)
                                val path = Path().apply {
                                    moveTo(startX, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width, size.height)
                                    close()
                                }
                                drawPath(path, Color.Black.copy(alpha = 0.42f))
                                drawLine(ExcevoColors.PlayheadAmber, Offset(startX, 2f), Offset(size.width, size.height), 2f)
                            }
                        }
                    } else if (filmstripFiles.size > 1) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            filmstripFiles.forEach { file ->
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alpha = 0.50f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    } else {
                        val thumbFile = (clip.thumbnailPath ?: asset?.thumbnailPath)
                            ?.let { File(it) }
                            ?.takeIf { it.exists() }
                        if (thumbFile != null) {
                            AsyncImage(
                                model = thumbFile,
                                contentDescription = clip.label,
                                contentScale = ContentScale.Crop,
                                alpha = 0.45f,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    val compactLabelStyle = MaterialTheme.typography.labelSmall.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(9.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                        lineHeight = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isSelected) 16.dp else 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = when (clip.mediaType) {
                                MediaType.VIDEO -> Icons.Outlined.Videocam
                                MediaType.IMAGE -> Icons.Outlined.Image
                                MediaType.AUDIO -> Icons.Outlined.Audiotrack
                            },
                            contentDescription = null,
                            tint = when {
                                !isAccessible -> ExcevoColors.DangerRed
                                isSelected -> ExcevoColors.AccentLavender
                                else -> ExcevoColors.TextPrimary
                            },
                            modifier = Modifier.size(14.dp),
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = clip.label,
                                style = compactLabelStyle,
                                color = ExcevoColors.TextPrimary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                text = if (!isAccessible) "Missing" else TimelineMath.formatDurationCompact(clip.timelineDurationUs),
                                style = compactLabelStyle,
                                color = when {
                                    !isAccessible -> ExcevoColors.DangerRed
                                    isSelected -> ExcevoColors.AccentLavender
                                    else -> ExcevoColors.TextSecondary
                                },
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (clip.groupId != null) {
                                Text(
                                    text = "• GRP",
                                    style = compactLabelStyle,
                                    color = ExcevoColors.AccentLavender,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.testTag("clip_group_badge_${clip.id}"),
                                )
                            }
                            if (clip.isMuted) {
                                Text("• Muted", style = compactLabelStyle, color = ExcevoColors.TextMuted, maxLines = 1, softWrap = false)
                            }
                            if (hasFxOrGrade) {
                                Text("• FX", style = compactLabelStyle, color = ExcevoColors.PlayheadAmber, maxLines = 1, softWrap = false)
                            }
                        }
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = null,
                            tint = ExcevoColors.TextPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp),
                        )
                    }

                    // Keyframe diamond markers drawn across the clip timeline bar
                    val allKeyframes = clip.keyframes.values.flatten()
                    if (allKeyframes.isNotEmpty() && clip.timelineDurationUs > 0L) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("clip_keyframe_markers_${clip.id}"),
                        ) {
                            val dur = clip.timelineDurationUs.toFloat()
                            val markerY = size.height - 6.dp.toPx()
                            val r = 4.dp.toPx()
                            allKeyframes.distinctBy { it.timeOffsetUs / 10_000L }.forEach { kf ->
                                val frac = (kf.timeOffsetUs / dur).coerceIn(0f, 1f)
                                val x = (frac * size.width).coerceIn(r, (size.width - r).coerceAtLeast(r))
                                val diamond = Path().apply {
                                    moveTo(x, markerY - r)
                                    lineTo(x + r, markerY)
                                    lineTo(x, markerY + r)
                                    lineTo(x - r, markerY)
                                    close()
                                }
                                drawPath(diamond, color = ExcevoColors.PlayheadAmber)
                            }
                        }
                    }

                    if (isSelected) {
                        var pendingStartUs = clip.sourceStartUs
                        var pendingEndUs = clip.sourceEndUs

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(12.dp)
                                .fillMaxHeight()
                                .background(ExcevoColors.AccentPurple)
                                .pointerInput(clip.id, clip.sourceStartUs, clip.sourceEndUs, pxPerSecInPx) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            pendingStartUs = clip.sourceStartUs
                                            pendingEndUs = clip.sourceEndUs
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val deltaUs = (TimelineMath.pxToUs(dragAmount, pxPerSecInPx) * clip.speedMultiplier).toLong()
                                            pendingStartUs = (pendingStartUs + deltaUs).coerceIn(
                                                0L,
                                                (pendingEndUs - TimelineMath.MIN_CLIP_DURATION_US).coerceAtLeast(0L)
                                            )
                                        },
                                        onDragEnd = {
                                            if (pendingStartUs != clip.sourceStartUs) {
                                                onTrimClipDrag(clip.id, pendingStartUs, clip.sourceEndUs)
                                            }
                                        },
                                    )
                                }
                                .testTag("trim_handle_left_${clip.id}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(ExcevoColors.TextPrimary, RoundedCornerShape(1.dp)),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(12.dp)
                                .fillMaxHeight()
                                .background(ExcevoColors.AccentPurple)
                                .pointerInput(clip.id, clip.sourceStartUs, clip.sourceEndUs, pxPerSecInPx) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            pendingStartUs = clip.sourceStartUs
                                            pendingEndUs = clip.sourceEndUs
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val deltaUs = (TimelineMath.pxToUs(dragAmount, pxPerSecInPx) * clip.speedMultiplier).toLong()
                                            pendingEndUs = (pendingEndUs + deltaUs).coerceIn(
                                                clip.sourceStartUs + TimelineMath.MIN_CLIP_DURATION_US,
                                                clip.sourceTotalDurationUs.coerceAtLeast(clip.sourceStartUs + TimelineMath.MIN_CLIP_DURATION_US),
                                            )
                                        },
                                        onDragEnd = {
                                            if (pendingEndUs != clip.sourceEndUs) {
                                                onTrimClipDrag(clip.id, clip.sourceStartUs, pendingEndUs)
                                            }
                                        },
                                    )
                                }
                                .testTag("trim_handle_right_${clip.id}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(ExcevoColors.TextPrimary, RoundedCornerShape(1.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}
