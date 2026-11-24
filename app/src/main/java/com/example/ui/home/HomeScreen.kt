package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.engine.TimelineMath
import com.example.core.model.ProjectSummary
import com.example.core.model.SequenceDiagnosticReport
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.ExcevoMarkCanvas
import com.example.ui.components.SpecBadge
import com.example.ui.components.StudioBackdropCanvas
import com.example.ui.projects.ProjectHubViewModel
import com.example.ui.theme.ExcevoColors
import java.io.File

@Composable
fun HomeScreen(
    viewModel: ProjectHubViewModel,
    bottomContentPadding: Dp = 20.dp,
    onAtTopChanged: (Boolean) -> Unit = {},
    onModalVisibilityChanged: (Boolean) -> Unit = {},
    onOpenProject: (String) -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToPresets: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAiLabSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(isAtTop) { onAtTopChanged(isAtTop) }
    LaunchedEffect(showCreateDialog, showAiLabSheet) {
        onModalVisibilityChanged(showCreateDialog || showAiLabSheet)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Welcome Hero Banner
            item {
                WelcomeHeroCard(
                    projectCount = uiState.allProjects.size,
                    onNewComposition = { showCreateDialog = true },
                )
            }

            // AI Lab Card
            item {
                AiLabBannerCard(
                    hasProjects = uiState.allProjects.isNotEmpty(),
                    onOpenAiLab = {
                        val first = uiState.recentProjects.firstOrNull()
                        if (first != null) {
                            viewModel.analyzeProjectSequence(first.id)
                        }
                        showAiLabSheet = true
                    },
                )
            }

            // Recent Projects Carousel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recent_projects_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = ExcevoColors.TextPrimary,
                        )
                        TextButton(
                            onClick = onNavigateToProjects,
                            modifier = Modifier.testTag("home_see_all_projects"),
                        ) {
                            Text("View All", color = ExcevoColors.AccentLavender)
                        }
                    }

                    if (uiState.recentProjects.isEmpty()) {
                        Surface(
                            color = ExcevoColors.StudioPanel,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No sequences yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ExcevoColors.TextPrimary,
                                    )
                                    Text(
                                        text = "Start a new composition to import video or images.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExcevoColors.TextSecondary,
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                OutlinedButton(
                                    onClick = { showCreateDialog = true },
                                    border = BorderStroke(1.dp, ExcevoColors.AccentPurple),
                                ) {
                                    Text("Create", color = ExcevoColors.AccentLavender)
                                }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(uiState.recentProjects, key = { it.id }) { summary ->
                                RecentProjectCard(
                                    summary = summary,
                                    onClick = { onOpenProject(summary.id) },
                                )
                            }
                        }
                    }
                }
            }

            // AI Tools Section (Honest functional & roadmap-transparent tools)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.ai_tools_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = ExcevoColors.TextPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AiToolTile(
                            icon = Icons.Outlined.Analytics,
                            title = "Pacing Analyzer",
                            badge = "ACTIVE",
                            badgeHighlight = true,
                            testTag = "ai_tool_pacing",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                uiState.recentProjects.firstOrNull()?.let { viewModel.analyzeProjectSequence(it.id) }
                                showAiLabSheet = true
                            },
                        )
                        AiToolTile(
                            icon = Icons.Outlined.Palette,
                            title = "CC & Look Presets",
                            badge = "FREE",
                            badgeHighlight = true,
                            testTag = "ai_tool_presets",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToPresets,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AiToolTile(
                            icon = Icons.Outlined.ContentCut,
                            title = "AI Smart Cut",
                            badge = "PHASE 7",
                            badgeHighlight = false,
                            testTag = "ai_tool_smart_cut",
                            modifier = Modifier.weight(1f),
                            onClick = { showAiLabSheet = true },
                        )
                        AiToolTile(
                            icon = Icons.Outlined.Subtitles,
                            title = "AI Captions",
                            badge = "PHASE 7",
                            badgeHighlight = false,
                            testTag = "ai_tool_captions",
                            modifier = Modifier.weight(1f),
                            onClick = { showAiLabSheet = true },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { req ->
                showCreateDialog = false
                viewModel.createProject(
                    name = req.name,
                    aspectRatio = req.aspectRatio,
                    frameRate = req.frameRate,
                    targetResolution = req.targetResolution,
                    scaleMode = req.scaleMode,
                    backgroundId = req.backgroundId,
                    customWidthPx = req.customWidthPx,
                    customHeightPx = req.customHeightPx,
                    onCreated = onOpenProject,
                )
            },
        )
    }

    if (showAiLabSheet) {
        AiLabDiagnosticDialog(
            projects = uiState.allProjects,
            report = uiState.diagnosticReport,
            onAnalyzeProject = viewModel::analyzeProjectSequence,
            onOpenProject = { id ->
                showAiLabSheet = false
                viewModel.dismissDiagnosticReport()
                onOpenProject(id)
            },
            onDismiss = {
                showAiLabSheet = false
                viewModel.dismissDiagnosticReport()
            },
        )
    }
}

@Composable
private fun WelcomeHeroCard(
    projectCount: Int,
    onNewComposition: () -> Unit,
) {
    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            StudioBackdropCanvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExcevoMarkCanvas(modifier = Modifier.size(32.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.brand_wordmark),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = ExcevoColors.TextPrimary,
                            )
                            Text(
                                text = "${stringResource(R.string.brand_subtitle)} · ${stringResource(R.string.brand_tagline)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.AccentLavender,
                            )
                        }
                    }
                    SpecBadge("$projectCount SEQ", highlight = true)
                }

                Text(
                    text = stringResource(R.string.home_hero_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = ExcevoColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.home_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )

                Button(
                    onClick = onNewComposition,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExcevoColors.AccentPurple,
                        contentColor = ExcevoColors.TextPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("home_new_composition_button"),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.new_composition),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiLabBannerCard(
    hasProjects: Boolean,
    onOpenAiLab: () -> Unit,
) {
    Surface(
        color = ExcevoColors.AccentPurpleContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenAiLab)
            .testTag("home_ai_lab_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = ExcevoColors.AccentPurple.copy(alpha = 0.22f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = ExcevoColors.AccentLavender,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_lab_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = ExcevoColors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (hasProjects) {
                        "Run Sequence Pacing Analyzer on your projects · Gemini AI arrives Phase 7"
                    } else {
                        stringResource(R.string.ai_lab_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun RecentProjectCard(
    summary: ProjectSummary,
    onClick: () -> Unit,
) {
    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier
            .width(172.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("home_recent_project_${summary.id}"),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ExcevoColors.ElevatedSurface)
                    .border(1.dp, ExcevoColors.HairlineBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val thumbFile = summary.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
                if (thumbFile != null) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = summary.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.MovieCreation,
                        contentDescription = null,
                        tint = ExcevoColors.AccentLavender,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleMedium,
                color = ExcevoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpecBadge(summary.aspectRatio.shortLabel, highlight = true)
                SpecBadge(TimelineMath.formatDurationCompact(summary.durationUs))
            }
        }
    }
}

@Composable
private fun AiToolTile(
    icon: ImageVector,
    title: String,
    badge: String,
    badgeHighlight: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (badgeHighlight) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                )
                SpecBadge(badge, highlight = badgeHighlight)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ExcevoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AiLabDiagnosticDialog(
    projects: List<ProjectSummary>,
    report: SequenceDiagnosticReport?,
    onAnalyzeProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Text("EXCEVO AI Lab", color = ExcevoColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Active Tool: Deterministic Sequence Pacing & Framing Analyzer",
                    style = MaterialTheme.typography.labelLarge,
                    color = ExcevoColors.AccentLavender,
                )
                if (projects.isEmpty()) {
                    Text(
                        text = "Create a sequence and import clips first to inspect pacing, orientation mismatches, and color-grade coverage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExcevoColors.TextSecondary,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(projects, key = { it.id }) { proj ->
                            OutlinedButton(
                                onClick = { onAnalyzeProject(proj.id) },
                                border = BorderStroke(
                                    1.dp,
                                    if (report?.projectId == proj.id) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                                ),
                            ) {
                                Text(proj.name, color = ExcevoColors.TextPrimary, maxLines = 1)
                            }
                        }
                    }
                    if (report != null) {
                        Surface(
                            color = ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "${report.projectName} · ${report.clipCount} clips · ${TimelineMath.formatDurationCompact(report.totalDurationUs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ExcevoColors.PlayheadAmber,
                                )
                                Text(
                                    text = "Rhythm: ${report.pacingLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ExcevoColors.TextPrimary,
                                )
                                report.recommendations.forEach { rec ->
                                    Text(
                                        text = "• $rec",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExcevoColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "Phase 7 Cloud & On-Device AI: Firebase AI Logic (gemini-3.8-flash Look/CC Generator, Smart Rough Cut, Auto Captions) + Beat Onset Detection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextMuted,
                )
            }
        },
        confirmButton = {
            if (report != null) {
                Button(
                    onClick = { onOpenProject(report.projectId) },
                    colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
                ) {
                    Text("Open in Studio")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ExcevoColors.TextSecondary)
            }
        },
    )
}
