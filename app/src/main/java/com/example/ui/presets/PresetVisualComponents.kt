package com.example.ui.presets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.ui.components.SpecBadge
import com.example.ui.theme.ExcevoColors
import kotlin.math.cos
import kotlin.math.sin

private data class CategoryVisualSpec(
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
)

private fun categoryVisual(tab: PresetTab): CategoryVisualSpec = when (tab) {
    PresetTab.SHAKES -> CategoryVisualSpec(Icons.Outlined.Vibration, Color(0xFFC084FC), Color(0xFF281B45))
    PresetTab.TRANSITIONS -> CategoryVisualSpec(Icons.Outlined.Compare, Color(0xFF60A5FA), Color(0xFF172744))
    PresetTab.EFFECTS -> CategoryVisualSpec(Icons.Outlined.AutoAwesome, Color(0xFFE879F9), Color(0xFF331B41))
    PresetTab.ONE_FRAME -> CategoryVisualSpec(Icons.Outlined.Bolt, Color(0xFFFBBF24), Color(0xFF362811))
    PresetTab.CC -> CategoryVisualSpec(Icons.Outlined.ColorLens, Color(0xFF34D399), Color(0xFF133329))
    PresetTab.TEXT -> CategoryVisualSpec(Icons.Outlined.TextFields, Color(0xFFFB7185), Color(0xFF391926))
    PresetTab.MOTION -> CategoryVisualSpec(Icons.Outlined.Animation, Color(0xFF818CF8), Color(0xFF1D2246))
    PresetTab.SAVED -> CategoryVisualSpec(Icons.Outlined.BookmarkBorder, Color(0xFFA78BFA), Color(0xFF231D42))
    PresetTab.COMMUNITY -> CategoryVisualSpec(Icons.Outlined.Public, Color(0xFF38BDF8), Color(0xFF132D3F))
    PresetTab.ALL -> CategoryVisualSpec(Icons.Outlined.CloudQueue, ExcevoColors.AccentLavender, ExcevoColors.AccentPurpleContainer)
}

@Composable
fun PresetCategoryCardsGrid(
    activeTab: PresetTab,
    counts: Map<PresetTab, Int>,
    onSelectTab: (PresetTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = PresetTab.categoryCards.chunked(3)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rows.forEach { rowTabs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowTabs.forEach { tab ->
                    val visual = categoryVisual(tab)
                    val selected = activeTab == tab
                    val count = counts[tab] ?: 0
                    Surface(
                        color = if (selected) {
                            visual.tint.copy(alpha = 0.22f)
                        } else {
                            visual.container.copy(alpha = 0.78f)
                        },
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) visual.tint else visual.tint.copy(alpha = 0.28f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onSelectTab(tab) }
                            .testTag("preset_tab_${tab.name}"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = tab.label,
                                tint = visual.tint,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = visual.tint.copy(alpha = 0.92f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalPresetPreviewCard(
    preset: PresetDefinition,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = modifier
            .width(164.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("preset_card_${preset.id}"),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                PresetRichThumbnail(
                    preset = preset,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.56f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = preset.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .minimumInteractiveComponentSize()
                        .testTag("save_preset_${preset.id}"),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.52f),
                        shape = CircleShape,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Remove saved preset" else "Save preset",
                                tint = if (isSaved) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = preset.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = ExcevoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    CreatorMiniAvatar(name = preset.creatorName)
                    Text(
                        text = preset.creatorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                SpecBadge("FREE", highlight = !preset.isBuiltIn)
            }
        }
    }
}

@Composable
fun CreatorMiniAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "E"
    Surface(
        color = ExcevoColors.AccentPurpleContainer,
        shape = CircleShape,
        border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.55f)),
        modifier = modifier.size(18.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.AccentLavender,
            )
        }
    }
}

/**
 * Custom procedural preview rendering real visual representations for every preset type:
 * - Shakes: damped sinusoidal motion waveform + ghosted camera frame trails
 * - Transitions: diagonal/split wipe panes + directional velocity trail
 * - Effects: radial optical prism/halation rings + RGB chromatic shift reticle
 * - 1 Frames: high-contrast single-frame strobe bars + beat peak flash marker
 * - CCs: real graded tonal horizon + RGB histogram readout derived from ColorGradeSpec
 * - Text: animated kinetic baseline & crisp specimen lines
 */
@Composable
fun PresetRichThumbnail(
    preset: PresetDefinition,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = modifier,
    ) {
        when (preset.category) {
            PresetCategory.SHAKES -> {
                val freqScale = (preset.shakeFrequencyHz.coerceIn(2f, 22f) / 14f)
                val ampScale = (preset.shakeAmplitude.coerceIn(4f, 50f) / 42f).coerceIn(0.28f, 0.92f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF151226), Color(0xFF28194A), Color(0xFF12121D))
                        )
                    )
                    // Ghosted displacement frames
                    val frameW = size.width * 0.44f
                    val frameH = size.height * 0.46f
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawRoundRect(
                        color = ExcevoColors.AccentLavender.copy(alpha = 0.22f),
                        topLeft = Offset(cx - frameW / 2f - 8f * ampScale, cy - frameH / 2f - 4f),
                        size = Size(frameW, frameH),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(2f),
                    )
                    drawRoundRect(
                        color = ExcevoColors.AccentPurple.copy(alpha = 0.55f),
                        topLeft = Offset(cx - frameW / 2f + 6f * ampScale, cy - frameH / 2f + 3f),
                        size = Size(frameW, frameH),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(2.5f),
                    )
                    // Damped sinusoidal curve
                    val path = Path()
                    val steps = 36
                    val maxAmp = size.height * 0.34f * ampScale
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = size.width * 0.08f + size.width * 0.84f * t
                        val y = cy + sin(t * 14f * freqScale) * maxAmp * (1f - t * 0.72f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = ExcevoColors.AccentLavender,
                        style = Stroke(width = 3.2f, cap = StrokeCap.Round),
                    )
                }
            }
            PresetCategory.TRANSITIONS -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF111C33), Color(0xFF253F73), Color(0xFF3E296B))
                        )
                    )
                    // Left & right sliding frames + diagonal cut plane
                    val splitPath = Path().apply {
                        moveTo(size.width * 0.56f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width * 0.38f, size.height)
                        close()
                    }
                    drawPath(splitPath, color = Color(0xFF1B1633).copy(alpha = 0.82f))
                    drawLine(
                        color = Color(0xFF60A5FA),
                        start = Offset(size.width * 0.56f, 0f),
                        end = Offset(size.width * 0.38f, size.height),
                        strokeWidth = 3.5f,
                    )
                    // Motion trails
                    for (row in 1..3) {
                        val y = size.height * (0.24f * row)
                        drawLine(
                            color = Color(0xFF93C5FD).copy(alpha = 0.65f),
                            start = Offset(size.width * 0.16f, y),
                            end = Offset(size.width * 0.44f, y),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            PresetCategory.EFFECTS -> {
                val intensity = preset.effectIntensity.coerceIn(0.25f, 1f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF5B21B6).copy(alpha = 0.72f * intensity + 0.2f),
                                Color(0xFF1B152B),
                                Color(0xFF0E0E17),
                            ),
                            center = Offset(size.width * 0.55f, size.height * 0.48f),
                            radius = size.maxDimension * 0.65f,
                        )
                    )
                    val cx = size.width * 0.52f
                    val cy = size.height * 0.50f
                    val r = size.minDimension * 0.32f
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.55f),
                        radius = r,
                        center = Offset(cx - 5f, cy),
                        style = Stroke(width = 2.5f),
                    )
                    drawCircle(
                        color = Color(0xFFE879F9).copy(alpha = 0.65f),
                        radius = r,
                        center = Offset(cx + 5f, cy),
                        style = Stroke(width = 2.5f),
                    )
                    drawCircle(
                        color = ExcevoColors.TextPrimary.copy(alpha = 0.85f),
                        radius = r * 0.52f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f),
                    )
                }
            }
            PresetCategory.ONE_FRAME -> {
                val intensity = preset.effectIntensity.coerceIn(0.3f, 1f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF16131E), Color(0xFF2B1F12), Color(0xFF12101A))
                        )
                    )
                    val barCount = 11
                    val spacing = size.width / (barCount + 1)
                    for (i in 1..barCount) {
                        val isPeak = i == (barCount + 1) / 2
                        val hFactor = if (isPeak) 0.76f else 0.22f + ((i % 3) * 0.1f)
                        val barH = size.height * hFactor
                        val x = spacing * i
                        drawLine(
                            color = if (isPeak) Color(0xFFFBBF24) else ExcevoColors.AccentPurple.copy(alpha = 0.52f),
                            start = Offset(x, (size.height - barH) / 2f),
                            end = Offset(x, (size.height + barH) / 2f),
                            strokeWidth = if (isPeak) 6f * intensity + 2f else 3f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            PresetCategory.CC, PresetCategory.FILTERS -> {
                val g = preset.colorGrade
                val warmShift = g.temperature * 0.28f
                val shadowColor = Color(
                    red = (0.14f + warmShift * 0.4f + g.exposure * 0.15f).coerceIn(0.04f, 0.9f),
                    green = (0.14f - g.tint * 0.18f + g.shadows * 0.1f).coerceIn(0.04f, 0.9f),
                    blue = (0.28f - warmShift * 0.5f + g.tint * 0.22f).coerceIn(0.05f, 0.95f),
                )
                val midColor = Color(
                    red = (0.36f + warmShift + g.contrast * 0.18f).coerceIn(0.08f, 0.96f),
                    green = (0.28f + g.saturation * 0.14f - g.tint * 0.12f).coerceIn(0.08f, 0.96f),
                    blue = (0.58f - warmShift * 0.45f + g.tint * 0.2f).coerceIn(0.08f, 0.96f),
                )
                val highlightColor = Color(
                    red = (0.76f + warmShift * 0.5f + g.highlights * 0.2f).coerceIn(0.15f, 0.99f),
                    green = (0.58f + g.saturation * 0.16f).coerceIn(0.15f, 0.99f),
                    blue = (0.88f - warmShift * 0.4f).coerceIn(0.15f, 0.99f),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(shadowColor, midColor, highlightColor),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                    )
                    // Tonal orb + histogram bars
                    drawCircle(
                        color = highlightColor.copy(alpha = 0.72f),
                        radius = size.minDimension * 0.24f,
                        center = Offset(size.width * 0.72f, size.height * 0.34f),
                    )
                    val bars = listOf(0.45f, 0.72f, 0.58f, 0.85f, 0.64f)
                    bars.forEachIndexed { idx, frac ->
                        val bx = size.width * (0.14f + idx * 0.12f)
                        val bh = size.height * 0.28f * frac
                        drawLine(
                            color = Color.White.copy(alpha = 0.72f),
                            start = Offset(bx, size.height * 0.86f),
                            end = Offset(bx, size.height * 0.86f - bh),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            PresetCategory.TEXT_ANIMATIONS -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF211329), Color(0xFF151424))
                        )
                    )
                    val centerY = size.height * 0.48f
                    // Kinetic Aa bars + underline baseline
                    drawRoundRect(
                        color = Color(0xFFFB7185).copy(alpha = 0.85f),
                        topLeft = Offset(size.width * 0.20f, centerY - 14f),
                        size = Size(size.width * 0.48f, 8f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                    drawRoundRect(
                        color = ExcevoColors.TextPrimary.copy(alpha = 0.75f),
                        topLeft = Offset(size.width * 0.20f, centerY + 2f),
                        size = Size(size.width * 0.60f, 6f),
                        cornerRadius = CornerRadius(3f, 3f),
                    )
                    val arrowX = size.width * 0.76f
                    drawLine(
                        color = ExcevoColors.AccentLavender,
                        start = Offset(arrowX, centerY + 14f),
                        end = Offset(arrowX, centerY - 14f),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round,
                    )
                    for (angle in listOf(-45.0, 45.0)) {
                        val rad = Math.toRadians(angle)
                        drawLine(
                            color = ExcevoColors.AccentLavender,
                            start = Offset(arrowX, centerY - 14f),
                            end = Offset(
                                arrowX + (sin(rad) * 7f).toFloat(),
                                centerY - 14f + (cos(rad) * 7f).toFloat(),
                            ),
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            PresetCategory.MOTION -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF131630), Color(0xFF231C4A))
                        )
                    )
                    val path = Path().apply {
                        moveTo(size.width * 0.14f, size.height * 0.78f)
                        cubicTo(
                            size.width * 0.36f,
                            size.height * 0.78f,
                            size.width * 0.58f,
                            size.height * 0.22f,
                            size.width * 0.86f,
                            size.height * 0.22f,
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF818CF8),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
                    )
                    val d1 = Offset(size.width * 0.14f, size.height * 0.78f)
                    val d2 = Offset(size.width * 0.86f, size.height * 0.22f)
                    listOf(d1, d2).forEach { c ->
                        val r = 6f
                        val diamond = Path().apply {
                            moveTo(c.x, c.y - r)
                            lineTo(c.x + r, c.y)
                            lineTo(c.x, c.y + r)
                            lineTo(c.x - r, c.y)
                            close()
                        }
                        drawPath(diamond, color = ExcevoColors.PlayheadAmber)
                    }
                }
            }
            else -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = ExcevoColors.ElevatedSurface)
                    drawCircle(
                        color = ExcevoColors.AccentPurple.copy(alpha = 0.55f),
                        radius = size.minDimension * 0.30f,
                    )
                }
            }
        }
    }
}
