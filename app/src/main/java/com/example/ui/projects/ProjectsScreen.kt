package com.example.ui.projects

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.engine.TimelineMath
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ProjectSummary
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.DeleteProjectDialog
import com.example.ui.components.RenameProjectDialog
import com.example.ui.components.SpecBadge
import com.example.ui.components.StudioBackdropCanvas
import com.example.ui.theme.ExcevoColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    viewModel: ProjectHubViewModel,
    bottomContentPadding: Dp = 20.dp,
    onAtTopChanged: (Boolean) -> Unit = {},
    onModalVisibilityChanged: (Boolean) -> Unit = {},
    onOpenProject: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val isAtTop by remember(uiState.viewMode, gridState, listState) {
        derivedStateOf {
            if (uiState.viewMode == ProjectViewMode.GRID) {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            } else {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
    }
    LaunchedEffect(isAtTop) { onAtTopChanged(isAtTop) }
    LaunchedEffect(showCreateDialog, renameTarget, deleteTarget) {
        onModalVisibilityChanged(showCreateDialog || renameTarget != null || deleteTarget != null)
    }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header + New Project + Grid/List + Sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.nav_projects),
                        style = MaterialTheme.typography.headlineMedium,
                        color = ExcevoColors.TextPrimary,
                    )
                    Text(
                        text = "${uiState.filteredProjects.size} of ${uiState.allProjects.size} sequences",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.TextSecondary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = viewModel::toggleViewMode,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("projects_view_mode_toggle"),
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == ProjectViewMode.GRID) {
                                Icons.AutoMirrored.Outlined.ViewList
                            } else {
                                Icons.Outlined.GridView
                            },
                            contentDescription = "Toggle Grid or List View",
                            tint = ExcevoColors.TextPrimary,
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { sortMenuOpen = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("projects_sort_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = "Sort projects",
                                tint = ExcevoColors.TextPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                            containerColor = ExcevoColors.ElevatedSurface,
                        ) {
                            ProjectSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            color = if (option == uiState.sortOption) {
                                                ExcevoColors.AccentLavender
                                            } else {
                                                ExcevoColors.TextPrimary
                                            },
                                        )
                                    },
                                    onClick = {
                                        sortMenuOpen = false
                                        viewModel.setSortOption(option)
                                    },
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ExcevoColors.AccentPurple,
                            contentColor = ExcevoColors.TextPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("new_project_button"),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.new_project), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search projects...", color = ExcevoColors.TextMuted) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = ExcevoColors.TextSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ExcevoColors.AccentPurple,
                    unfocusedBorderColor = ExcevoColors.HairlineBorder,
                    focusedContainerColor = ExcevoColors.StudioPanel,
                    unfocusedContainerColor = ExcevoColors.StudioPanel,
                    focusedTextColor = ExcevoColors.TextPrimary,
                    unfocusedTextColor = ExcevoColors.TextPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("projects_search_input"),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Aspect filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AspectFilterChip(
                        label = "All",
                        selected = uiState.aspectFilter == null,
                        onClick = { viewModel.setAspectFilter(null) },
                    )
                }
                items(AspectRatioPreset.entries) { ratio ->
                    AspectFilterChip(
                        label = ratio.shortLabel,
                        selected = uiState.aspectFilter == ratio,
                        onClick = {
                            viewModel.setAspectFilter(if (uiState.aspectFilter == ratio) null else ratio)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ExcevoColors.AccentPurple)
                }
            } else if (uiState.filteredProjects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        color = ExcevoColors.StudioPanel,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MovieCreation,
                                contentDescription = null,
                                tint = ExcevoColors.AccentLavender,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = if (uiState.allProjects.isEmpty()) {
                                    stringResource(R.string.empty_hub_title)
                                } else {
                                    "No matching projects"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = ExcevoColors.TextPrimary,
                            )
                            Text(
                                text = if (uiState.allProjects.isEmpty()) {
                                    stringResource(R.string.empty_hub_body)
                                } else {
                                    "Adjust your search query or aspect ratio filter."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = ExcevoColors.TextSecondary,
                            )
                            if (uiState.allProjects.isEmpty()) {
                                Button(
                                    onClick = { showCreateDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
                                    modifier = Modifier.testTag("empty_create_project_button"),
                                ) {
                                    Text(stringResource(R.string.new_project))
                                }
                            }
                        }
                    }
                }
            } else if (uiState.viewMode == ProjectViewMode.GRID) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = bottomContentPadding),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("projects_grid"),
                ) {
                    items(uiState.filteredProjects, key = { it.id }) { summary ->
                        ProjectGridCard(
                            summary = summary,
                            onContinue = { onOpenProject(summary.id) },
                            onRename = { renameTarget = summary },
                            onDuplicate = { viewModel.duplicateProject(summary.id) },
                            onDelete = { deleteTarget = summary },
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = bottomContentPadding),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("projects_list"),
                ) {
                    items(uiState.filteredProjects, key = { it.id }) { summary ->
                        ProjectListCard(
                            summary = summary,
                            onContinue = { onOpenProject(summary.id) },
                            onRename = { renameTarget = summary },
                            onDuplicate = { viewModel.duplicateProject(summary.id) },
                            onDelete = { deleteTarget = summary },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (bottomContentPadding - 12.dp).coerceAtLeast(12.dp)),
        )
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

    renameTarget?.let { target ->
        RenameProjectDialog(
            currentName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameProject(target.id, newName)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        DeleteProjectDialog(
            target = target,
            onDismiss = { deleteTarget = null },
            onConfirmDelete = {
                viewModel.deleteProject(target.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun AspectFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("filter_chip_$label"),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ProjectGridCard(
    summary: ProjectSummary,
    onContinue: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onContinue)
            .testTag("project_card_${summary.id}"),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
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
                    StudioBackdropCanvas(modifier = Modifier.fillMaxSize())
                    Text(
                        text = summary.aspectRatio.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ExcevoColors.AccentLavender,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = ExcevoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ProjectOverflowMenu(
                    projectId = summary.id,
                    projectName = summary.name,
                    expanded = menuExpanded,
                    onToggle = { menuExpanded = it },
                    onContinue = onContinue,
                    onRename = onRename,
                    onDuplicate = onDuplicate,
                    onDelete = onDelete,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpecBadge(summary.aspectRatio.shortLabel, highlight = true)
                SpecBadge(TimelineMath.formatDurationCompact(summary.durationUs))
            }
        }
    }
}

@Composable
private fun ProjectListCard(
    summary: ProjectSummary,
    onContinue: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onContinue)
            .testTag("project_card_${summary.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 64.dp)
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
                    StudioBackdropCanvas(modifier = Modifier.fillMaxSize())
                    Text(
                        text = summary.aspectRatio.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ExcevoColors.AccentLavender,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
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
                    SpecBadge("${summary.frameRate.fps} FPS")
                    SpecBadge(TimelineMath.formatDurationCompact(summary.durationUs))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${summary.clipCount} clips · ${dateFormatter.format(Date(summary.updatedAtMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextMuted,
                )
            }
            ProjectOverflowMenu(
                projectId = summary.id,
                projectName = summary.name,
                expanded = menuExpanded,
                onToggle = { menuExpanded = it },
                onContinue = onContinue,
                onRename = onRename,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun ProjectOverflowMenu(
    projectId: String,
    projectName: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        IconButton(
            onClick = { onToggle(true) },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("project_menu_$projectId"),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "Options for $projectName",
                tint = ExcevoColors.TextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onToggle(false) },
            containerColor = ExcevoColors.ElevatedSurface,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.continue_editing), color = ExcevoColors.AccentLavender) },
                leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = ExcevoColors.AccentLavender) },
                onClick = {
                    onToggle(false)
                    onContinue()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename), color = ExcevoColors.TextPrimary) },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null, tint = ExcevoColors.TextSecondary) },
                onClick = {
                    onToggle(false)
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.duplicate), color = ExcevoColors.TextPrimary) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, tint = ExcevoColors.TextSecondary) },
                onClick = {
                    onToggle(false)
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = ExcevoColors.DangerRed) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = ExcevoColors.DangerRed) },
                onClick = {
                    onToggle(false)
                    onDelete()
                },
            )
        }
    }
}
