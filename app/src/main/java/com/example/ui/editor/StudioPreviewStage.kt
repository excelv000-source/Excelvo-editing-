package com.example.ui.editor

import android.os.Build
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.core.engine.EffectGradeMath
import com.example.core.engine.TimelineEvaluator
import com.example.core.engine.TimelineMath
import com.example.core.model.BlendModeType
import com.example.core.model.CanvasScaleMode
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TransformSpec
import com.example.core.ports.GpuCapabilities
import com.example.ui.theme.ExcevoColors
import java.io.File

@Composable
fun StudioPreviewStage(
    project: TimelineProject,
    activeClip: TimelineClip?,
    activeOverlayClips: List<TimelineClip> = emptyList(),
    activeAsset: MediaAsset?,
    playheadUs: Long,
    hasLiveTransformOverride: Boolean = false,
    liveOverrideClipId: String? = null,
    showSafeGuides: Boolean,
    isCleanPreview: Boolean,
    isBuffering: Boolean,
    isUsingGpuEffects: Boolean,
    gpuCapabilities: GpuCapabilities,
    playbackError: String?,
    hasPlaybackController: Boolean,
    onAttachTextureView: (TextureView?) -> Unit,
    onToggleScaleMode: () -> Unit,
    onPreviewTransform: (TransformSpec) -> Unit,
    onCommitTransform: (TransformSpec) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val targetAspect = project.effectiveAspectFloat().coerceIn(0.25f, 4f)
        val maxW = maxWidth
        val maxH = maxHeight
        val boxAspect = if (maxH.value > 0f) maxW.value / maxH.value else 1f
        val frameModifier = if (boxAspect > targetAspect) {
            Modifier
                .height(maxH)
                .aspectRatio(targetAspect)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(targetAspect)
        }
        val hudPadding = 6.dp

        val bgSpec = project.effectiveBackground()
        val bgBrush = remember(bgSpec) {
            Brush.linearGradient(
                colors = listOf(Color(bgSpec.colorArgb), Color(bgSpec.secondaryColorArgb))
            )
        }

        val evaluatedTransform = remember(activeClip, playheadUs, hasLiveTransformOverride, liveOverrideClipId) {
            activeClip?.let { clip ->
                if (hasLiveTransformOverride && clip.id == liveOverrideClipId) {
                    clip.transform
                } else {
                    TimelineEvaluator.evaluateTransformAt(clip, playheadUs)
                }
            }
        }
        val uniforms = remember(activeClip?.colorGrade, activeClip?.effectStack) {
            activeClip?.let { EffectGradeMath.computeUniforms(it.colorGrade, it.effectStack) }
        }
        val colorFilter = remember(uniforms) {
            uniforms?.takeIf { !it.isIdentity() }?.let { u ->
                ColorFilter.colorMatrix(ColorMatrix(EffectGradeMath.buildColorMatrix20(u)))
            }
        }
        val blurModifier = if (uniforms != null && uniforms.blur > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur((uniforms.blur * 14f).dp)
        } else {
            Modifier
        }

        var dragTransform by remember(activeClip?.id, activeClip?.transform) {
            mutableStateOf(activeClip?.transform ?: TransformSpec())
        }

        Box(
            modifier = frameModifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgBrush)
                .graphicsLayer {
                    // Enables saveLayer blend modes to composite against the canvas background & lower layers
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .border(1.dp, ExcevoColors.HairlineBorder, RoundedCornerShape(12.dp))
                .pointerInput(activeClip?.id, isCleanPreview) {
                    if (activeClip != null && !isCleanPreview) {
                        detectDragGestures(
                            onDragStart = {
                                dragTransform = activeClip.transform
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragTransform = dragTransform.copy(
                                    positionX = (dragTransform.positionX + dragAmount.x).coerceIn(-600f, 600f),
                                    positionY = (dragTransform.positionY + dragAmount.y).coerceIn(-600f, 600f),
                                )
                                onPreviewTransform(dragTransform)
                            },
                            onDragEnd = {
                                onCommitTransform(dragTransform)
                            },
                        )
                    }
                }
                .testTag("studio_stage_viewport"),
            contentAlignment = Alignment.Center,
        ) {
            val contentScale = if (project.scaleMode == CanvasScaleMode.FILL) {
                ContentScale.Crop
            } else {
                ContentScale.Fit
            }

            val showHardwareSurface = activeClip?.mediaType == MediaType.VIDEO &&
                hasPlaybackController &&
                gpuCapabilities.useHardwareGpuPipeline &&
                (activeAsset?.isAccessible != false)

            if (activeClip != null && evaluatedTransform != null) {
                RenderClipLayer(
                    clip = activeClip,
                    asset = activeAsset,
                    transform = evaluatedTransform,
                    colorFilter = colorFilter,
                    blurModifier = blurModifier,
                    contentScale = contentScale,
                    projectAspectLabel = project.aspectRatio.shortLabel,
                    projectScaleLabel = project.scaleMode.label,
                    showHardwareSurface = showHardwareSurface,
                    onAttachTextureView = onAttachTextureView,
                )

                // Overlay & Text track clips layered atop primary video clip ordered by 2.5D Z-depth
                val orderedOverlays = remember(activeOverlayClips, playheadUs, hasLiveTransformOverride, liveOverrideClipId) {
                    activeOverlayClips
                        .map { overlayClip ->
                            val ovTransform = if (hasLiveTransformOverride && overlayClip.id == liveOverrideClipId) {
                                overlayClip.transform
                            } else {
                                TimelineEvaluator.evaluateTransformAt(overlayClip, playheadUs)
                            }
                            overlayClip to ovTransform
                        }
                        .sortedBy { (_, t) -> t.positionZ }
                }
                orderedOverlays.forEach { (overlayClip, ovTransform) ->
                    val ovUniforms = EffectGradeMath.computeUniforms(overlayClip.colorGrade, overlayClip.effectStack)
                    val ovFilter = ovUniforms.takeIf { !it.isIdentity() }?.let {
                        ColorFilter.colorMatrix(ColorMatrix(EffectGradeMath.buildColorMatrix20(it)))
                    }
                    val ovBlur = if (ovUniforms.blur > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur((ovUniforms.blur * 14f).dp)
                    } else {
                        Modifier
                    }
                    RenderClipLayer(
                        clip = overlayClip,
                        asset = project.assets[overlayClip.assetId],
                        transform = ovTransform,
                        colorFilter = ovFilter,
                        blurModifier = ovBlur,
                        contentScale = contentScale,
                        projectAspectLabel = project.aspectRatio.shortLabel,
                        projectScaleLabel = project.scaleMode.label,
                        showHardwareSurface = false,
                        onAttachTextureView = {},
                    )
                }

                // Glow / Bloom halo overlay (shader handles hardware video; Canvas halo handles images & fallback)
                if (uniforms != null && uniforms.glow > 0.01f && (!showHardwareSurface || !isUsingGpuEffects)) {
                    val glowAlpha = (uniforms.glow * 0.42f).coerceIn(0f, 0.55f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE9D5FF).copy(alpha = glowAlpha),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.maxDimension * 0.62f,
                            )
                        )
                    }
                }

                // Real-time vignette overlay matching shader math for images / fallback
                if (uniforms != null && uniforms.vignette > 0.01f && (!showHardwareSurface || !isUsingGpuEffects)) {
                    val vigAlpha = (uniforms.vignette * 0.78f).coerceIn(0f, 0.85f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.35f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = vigAlpha),
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.maxDimension * 0.65f,
                            )
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = project.aspectRatio.shortLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        color = ExcevoColors.AccentLavender,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (project.primaryVideoTrack().clips.isEmpty()) {
                            "Tap Import below to add video, photos, or audio"
                        } else {
                            "End of sequence"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExcevoColors.TextSecondary,
                    )
                }
            }

            if (showSafeGuides && !isCleanPreview) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(w / 3f, 0f), Offset(w / 3f, h), 1f)
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), 1f)
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, h / 3f), Offset(w, h / 3f), 1f)
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), 1f)
                    val insetX = w * 0.05f
                    val insetY = h * 0.05f
                    drawRect(
                        color = ExcevoColors.AccentPurple.copy(alpha = 0.30f),
                        topLeft = Offset(insetX, insetY),
                        size = Size(w - insetX * 2f, h - insetY * 2f),
                        style = Stroke(width = 1f),
                    )
                }
            }

            if (isBuffering) {
                CircularProgressIndicator(
                    color = ExcevoColors.AccentPurple,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        if (!isCleanPreview) {
            // Top-left GPU / Pipeline Pill
            Surface(
                color = ExcevoColors.VoidCharcoal.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, ExcevoColors.HairlineBorder.copy(alpha = 0.75f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(hudPadding)
                    .testTag("gpu_pipeline_badge"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = if (isUsingGpuEffects) ExcevoColors.SuccessEmerald else ExcevoColors.AccentLavender,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = if (isUsingGpuEffects) gpuCapabilities.statusLabel else "Safe Render",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.TextPrimary,
                        maxLines = 1,
                    )
                }
            }

            // Bottom-left active clip readout (incl. active blend / Z-depth if modified)
            if (activeClip != null && activeAsset != null) {
                val blendSuffix = if (activeClip.blendMode != BlendModeType.NORMAL) " · ${activeClip.blendMode.label}" else ""
                val zSuffix = if (evaluatedTransform != null && kotlin.math.abs(evaluatedTransform.positionZ) > 0.5f) {
                    " · Z:${evaluatedTransform.positionZ.toInt()}"
                } else ""
                Surface(
                    color = ExcevoColors.VoidCharcoal.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, ExcevoColors.HairlineBorder.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(hudPadding),
                ) {
                    Text(
                        text = "${activeClip.label} · ${activeAsset.width}x${activeAsset.height}$blendSuffix$zSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            // Bottom-right interactive Fit / Fill toggle pill
            Surface(
                onClick = onToggleScaleMode,
                color = ExcevoColors.VoidCharcoal.copy(alpha = 0.68f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, ExcevoColors.AccentPurple.copy(alpha = 0.55f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(hudPadding)
                    .testTag("stage_scale_mode_chip"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AspectRatio,
                        contentDescription = "Toggle Fit or Fill",
                        tint = ExcevoColors.AccentLavender,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = project.scaleMode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.AccentLavender,
                        maxLines = 1,
                    )
                }
            }
        }

        if (!playbackError.isNullOrBlank()) {
            Surface(
                color = ExcevoColors.DangerRed.copy(alpha = 0.90f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp)
                    .clickable(onClick = onDismissError)
                    .testTag("playback_error_banner"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        text = playbackError,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderClipLayer(
    clip: TimelineClip,
    asset: MediaAsset?,
    transform: TransformSpec,
    colorFilter: ColorFilter?,
    blurModifier: Modifier,
    contentScale: ContentScale,
    projectAspectLabel: String,
    projectScaleLabel: String,
    showHardwareSurface: Boolean,
    onAttachTextureView: (TextureView?) -> Unit,
) {
    val hasCrop = transform.cropLeft + transform.cropRight + transform.cropTop + transform.cropBottom > 0.002f
    val cropModifier = if (hasCrop) {
        val shape = GenericShape { size, _ ->
            val l = (size.width * transform.cropLeft.coerceIn(0f, 0.45f))
            val t = (size.height * transform.cropTop.coerceIn(0f, 0.45f))
            val r = (size.width * (1f - transform.cropRight.coerceIn(0f, 0.45f))).coerceAtLeast(l + 2f)
            val b = (size.height * (1f - transform.cropBottom.coerceIn(0f, 0.45f))).coerceAtLeast(t + 2f)
            addRect(Rect(l, t, r, b))
        }
        Modifier.clip(shape)
    } else {
        Modifier
    }

    val zFactor = (1f + transform.positionZ / 400f).coerceIn(0.35f, 2.5f)
    val hasSkew = kotlin.math.abs(transform.skewXDeg) > 0.05f || kotlin.math.abs(transform.skewYDeg) > 0.05f
    val skewModifier = if (hasSkew) {
        val kx = kotlin.math.tan(Math.toRadians(transform.skewXDeg.coerceIn(-60f, 60f).toDouble())).toFloat()
        val ky = kotlin.math.tan(Math.toRadians(transform.skewYDeg.coerceIn(-60f, 60f).toDouble())).toFloat()
        val ax = transform.anchorX.coerceIn(0f, 1f)
        val ay = transform.anchorY.coerceIn(0f, 1f)
        Modifier.drawWithContent {
            val px = size.width * ax
            val py = size.height * ay
            val skewMatrix = android.graphics.Matrix().apply {
                setSkew(kx, ky, px, py)
            }
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.nativeCanvas.concat(skewMatrix)
                drawContent()
                canvas.restore()
            }
        }
    } else {
        Modifier
    }
    val transformModifier = Modifier.graphicsLayer {
        transformOrigin = TransformOrigin(
            pivotFractionX = transform.anchorX.coerceIn(0f, 1f),
            pivotFractionY = transform.anchorY.coerceIn(0f, 1f),
        )
        translationX = transform.positionX
        translationY = transform.positionY
        scaleX = transform.scaleX * zFactor * (if (transform.flipH) -1f else 1f)
        scaleY = transform.scaleY * zFactor * (if (transform.flipV) -1f else 1f)
        rotationZ = transform.rotationDeg
        alpha = transform.opacity.coerceIn(0f, 1f)
    }

    val blendModifier = if (clip.blendMode != BlendModeType.NORMAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Modifier.drawWithContent {
            val paint = Paint().apply { blendMode = clip.blendMode.toComposeBlendMode() }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                drawContent()
                canvas.restore()
            }
        }
    } else {
        Modifier
    }

    val displayFile = (asset?.cachedLocalPath ?: clip.thumbnailPath)
        ?.let { File(it) }
        ?.takeIf { it.exists() }
    val thumbFile = displayFile ?: clip.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }

    if (asset?.isAccessible == false && thumbFile == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Missing media",
                    tint = ExcevoColors.DangerRed,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Media Unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    color = ExcevoColors.TextPrimary,
                )
                Text(
                    text = "${clip.label} was moved or deleted",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else if (thumbFile != null) {
        AsyncImage(
            model = thumbFile,
            contentDescription = clip.label,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .then(blendModifier)
                .then(transformModifier)
                .then(skewModifier)
                .then(cropModifier)
                .then(blurModifier),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(blendModifier)
                .then(transformModifier)
                .then(skewModifier)
                .then(cropModifier)
                .then(blurModifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clip.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = ExcevoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$projectAspectLabel · $projectScaleLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.AccentLavender,
                )
            }
        }
    }

    if (showHardwareSurface) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    onAttachTextureView(this)
                }
            },
            update = { tv ->
                onAttachTextureView(tv)
            },
            modifier = Modifier
                .fillMaxSize()
                .then(blendModifier),
        )
    }
}

private fun BlendModeType.toComposeBlendMode(): BlendMode = when (this) {
    BlendModeType.NORMAL -> BlendMode.SrcOver
    BlendModeType.MULTIPLY -> BlendMode.Multiply
    BlendModeType.SCREEN -> BlendMode.Screen
    BlendModeType.OVERLAY -> BlendMode.Overlay
    BlendModeType.DARKEN -> BlendMode.Darken
    BlendModeType.LIGHTEN -> BlendMode.Lighten
    BlendModeType.ADDITIVE -> BlendMode.Plus
}
