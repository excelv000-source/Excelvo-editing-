package com.example.ui.presets

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.model.PresetDefinition
import com.example.ui.theme.ExcevoColors

@Composable
fun PresetsScreen(
    viewModel: PresetsViewModel,
    bottomContentPadding: Dp = 20.dp,
    onAtTopChanged: (Boolean) -> Unit = {},
    onModalVisibilityChanged: (Boolean) -> Unit = {},
    onOpenProject: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var detailPreset by remember { mutableStateOf<PresetDefinition?>(null) }
    var showValidatorDialog by remember { mutableStateOf(false) }

    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(isAtTop) { onAtTopChanged(isAtTop) }
    LaunchedEffect(detailPreset, showValidatorDialog) {
        onModalVisibilityChanged(detailPreset != null || showValidatorDialog)
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessages()
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
            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.presets_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = ExcevoColors.TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.presets_free_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.SuccessEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.activeTab != PresetTab.ALL) {
                        Surface(
                            color = ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectTabExplicit(PresetTab.ALL) }
                                .testTag("preset_tab_ALL"),
                        ) {
                            Text(
                                text = "All",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.AccentLavender,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ExcevoColors.AccentPurple),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showValidatorDialog = true }
                            .testTag("validate_preset_json_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = ExcevoColors.AccentLavender,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Validate JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.AccentLavender,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                color = ExcevoColors.StudioPanel,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = ExcevoColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (uiState.searchQuery.isEmpty()) {
                            Text(
                                text = "Search shakes, FX, 1F, CCs...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExcevoColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = ExcevoColors.TextPrimary),
                            cursorBrush = SolidColor(ExcevoColors.AccentLavender),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("presets_search_input"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("presets_list"),
            ) {
                item(key = "category_cards_grid") {
                    PresetCategoryCardsGrid(
                        activeTab = uiState.activeTab,
                        counts = uiState.categoryCounts,
                        onSelectTab = viewModel::selectTab,
                    )
                }

                items(uiState.sections, key = { "section_${it.tab.name}" }) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ExcevoColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = section.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ExcevoColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (uiState.activeTab == PresetTab.ALL) {
                                Surface(
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.selectTabExplicit(section.tab) },
                                ) {
                                    Text(
                                        text = "Focus (${section.presets.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExcevoColors.AccentLavender,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        if (section.isCommunitySection && section.presets.isEmpty()) {
                            CommunityPhase6InfoCard(
                                onTestValidator = { showValidatorDialog = true },
                            )
                        } else if (section.presets.isEmpty()) {
                            Surface(
                                color = ExcevoColors.StudioPanel,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = if (section.tab == PresetTab.SAVED) "No saved presets yet" else "No presets match",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ExcevoColors.TextPrimary,
                                    )
                                    Text(
                                        text = if (section.tab == PresetTab.SAVED) {
                                            "Tap the bookmark icon on any preset card to save it locally."
                                        } else {
                                            "Try clearing your search filter."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExcevoColors.TextSecondary,
                                    )
                                }
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("preset_section_${section.tab.name}"),
                            ) {
                                items(section.presets, key = { "${section.tab.name}_${it.id}" }) { preset ->
                                    HorizontalPresetPreviewCard(
                                        preset = preset,
                                        isSaved = preset.id in uiState.savedIds,
                                        onToggleSave = { viewModel.toggleSaved(preset.id) },
                                        onClick = { detailPreset = preset },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = (bottomContentPadding - 8.dp).coerceAtLeast(10.dp),
                    start = 16.dp,
                    end = 16.dp,
                ),
        ) { data ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ExcevoColors.StudioPanel.copy(alpha = 0.95f))
                    .border(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = ExcevoColors.AccentLavender,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = ExcevoColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    detailPreset?.let { preset ->
        PresetDetailPreviewDialog(
            preset = preset,
            isSaved = preset.id in uiState.savedIds,
            projects = uiState.projects,
            onToggleSave = { viewModel.toggleSaved(preset.id) },
            onFreeDownload = { viewModel.downloadCommunityPreset(preset) },
            onApplyToProject = { projectId, intensity ->
                viewModel.applyPresetToProject(preset, projectId, intensity)
                detailPreset = null
            },
            onOpenProject = { projectId ->
                detailPreset = null
                onOpenProject(projectId)
            },
            onDismiss = { detailPreset = null },
        )
    }

    if (showValidatorDialog) {
        ValidatePresetJsonDialog(
            onValidate = { raw ->
                viewModel.validateAndImportPresetJson(raw) {
                    showValidatorDialog = false
                }
            },
            onDismiss = { showValidatorDialog = false },
        )
    }
}

@Composable
private fun CommunityPhase6InfoCard(
    onTestValidator: () -> Unit,
) {
    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = ExcevoColors.AccentLavender)
                Text(
                    text = "100% Free Community · Phase 6 Backend",
                    style = MaterialTheme.typography.titleMedium,
                    color = ExcevoColors.TextPrimary,
                )
            }
            Text(
                text = "EXCEVO Community is strictly free (no payments or paid downloads). Live user uploads, Pending Review moderation, likes, and creator profiles activate in Phase 6 after Firebase Auth + Firestore provisioning — zero fake uploads or fake download counts are shown here.",
                style = MaterialTheme.typography.bodyMedium,
                color = ExcevoColors.TextSecondary,
            )
            OutlinedButton(
                onClick = onTestValidator,
                border = BorderStroke(1.dp, ExcevoColors.AccentPurple),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ExcevoColors.AccentLavender, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test & Import .excevo.json Locally", color = ExcevoColors.AccentLavender)
            }
        }
    }
}

@Composable
private fun ValidatePresetJsonDialog(
    onValidate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var jsonInput by remember {
        mutableStateOf(
            """
            {
              "presetFileFormat": "excevo-preset/v1",
              "title": "Custom Purple Punch",
              "category": "CC",
              "description": "High-contrast violet editorial grade",
              "creatorName": "Local Creator",
              "contrast": 0.25,
              "tint": 0.18,
              "vignette": 0.22
            }
            """.trimIndent()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = { Text("Validate Preset JSON", color = ExcevoColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enforces excevo-preset/v1 schema, safe ranges, and blocks executable scripts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    minLines = 6,
                    maxLines = 10,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExcevoColors.AccentPurple,
                        unfocusedBorderColor = ExcevoColors.HairlineBorder,
                        focusedTextColor = ExcevoColors.TextPrimary,
                        unfocusedTextColor = ExcevoColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("preset_json_validator_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onValidate(jsonInput) },
                colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
                modifier = Modifier.testTag("confirm_validate_preset_button"),
            ) {
                Text("Validate & Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = ExcevoColors.TextSecondary)
            }
        },
    )
}
