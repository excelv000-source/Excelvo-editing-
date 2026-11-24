package com.example.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.model.LibraryItemType
import com.example.core.model.ProxyQuality
import com.example.core.model.ThemeIntensity
import com.example.data.storage.StorageManager
import com.example.ui.components.SpecBadge
import com.example.ui.theme.ExcevoColors

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    bottomContentPadding: Dp = 20.dp,
    onAtTopChanged: (Boolean) -> Unit = {},
    onModalVisibilityChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRolesDialog by remember { mutableStateOf(false) }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var selectedImportType by remember { mutableStateOf(LibraryItemType.LUT) }
    val listState = rememberLazyListState()

    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(isAtTop) { onAtTopChanged(isAtTop) }
    LaunchedEffect(showRolesDialog, showLibraryDialog, showExtensionsDialog, showDevicesDialog, showHelpDialog) {
        onModalVisibilityChanged(showRolesDialog || showLibraryDialog || showExtensionsDialog || showDevicesDialog || showHelpDialog)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importLibraryAsset(uri.toString(), selectedImportType)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("profile_screen_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ExcevoColors.TextPrimary,
                )
            }

            // Account & Role Card
            item {
                Surface(
                    color = ExcevoColors.StudioPanel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                color = ExcevoColors.AccentPurpleContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.PersonOutline,
                                        contentDescription = null,
                                        tint = ExcevoColors.AccentLavender,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Guest Studio",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = ExcevoColors.TextPrimary,
                                    )
                                    SpecBadge("GUEST", highlight = true)
                                }
                                Text(
                                    text = "100% Free Ecosystem · Google Sign-In & Creator Profiles unlock in Phase 6",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ExcevoColors.TextSecondary,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showRolesDialog = true },
                            border = BorderStroke(1.dp, ExcevoColors.AccentPurple),
                            modifier = Modifier.fillMaxWidth().testTag("profile_roles_button"),
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = ExcevoColors.AccentLavender, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Roles & Community Membership", color = ExcevoColors.AccentLavender)
                        }
                    }
                }
            }

            // Real Usage Statistics
            item {
                Surface(
                    color = ExcevoColors.StudioPanel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Studio Usage Statistics", style = MaterialTheme.typography.titleMedium, color = ExcevoColors.TextPrimary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UsageStatTile("Projects", uiState.projectCount.toString(), Modifier.weight(1f))
                            UsageStatTile("Clips", uiState.totalClipCount.toString(), Modifier.weight(1f))
                            UsageStatTile("Saved", uiState.savedPresetsCount.toString(), Modifier.weight(1f))
                            UsageStatTile("Library", uiState.libraryItems.size.toString(), Modifier.weight(1f))
                        }
                        Text(
                            text = "AI Diagnostics Runs: ${uiState.preferences.aiUsageCount} · Unlimited Free Local Tools",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.TextSecondary,
                        )
                    }
                }
            }

            // Appearance & Theme Intensity
            item {
                Surface(
                    color = ExcevoColors.StudioPanel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ColorLens, contentDescription = null, tint = ExcevoColors.AccentLavender)
                            Text("Appearance & Themes", style = MaterialTheme.typography.titleMedium, color = ExcevoColors.TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeIntensity.entries.forEach { intensity ->
                                val selected = uiState.preferences.themeIntensity == intensity
                                Surface(
                                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setThemeIntensity(intensity) }
                                        .testTag("theme_option_${intensity.name}"),
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = intensity.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                        Text("Default Studio Workspace", style = MaterialTheme.typography.labelLarge, color = ExcevoColors.TextSecondary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.example.core.model.WorkspacePreset.entries.chunked(2).forEach { pair ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { wp ->
                                        val selected = uiState.preferences.workspacePreset == wp
                                        Surface(
                                            color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { viewModel.setWorkspacePreset(wp) }
                                                .testTag("profile_workspace_${wp.name}"),
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                Text(
                                                    text = wp.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                                )
                                                Text(
                                                    text = wp.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ExcevoColors.TextSecondary,
                                                    maxLines = 2,
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

            // Performance Settings
            item {
                Surface(
                    color = ExcevoColors.StudioPanel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Speed, contentDescription = null, tint = ExcevoColors.PlayheadAmber)
                            Text("Performance & Timeline Preferences", style = MaterialTheme.typography.titleMedium, color = ExcevoColors.TextPrimary)
                        }

                        PreferenceToggleRow(
                            title = "Hardware MediaCodec Acceleration",
                            subtitle = "Use device GPU/MediaCodec decoders when available",
                            checked = uiState.preferences.hardwareDecodeEnabled,
                            testTag = "pref_hw_decode_switch",
                            onCheckedChange = viewModel::setHardwareDecode,
                        )
                        PreferenceToggleRow(
                            title = "Magnetic Timeline Snap",
                            subtitle = "Snap playhead and trim edges to clip boundaries",
                            checked = uiState.preferences.timelineSnapEnabled,
                            testTag = "pref_timeline_snap_switch",
                            onCheckedChange = viewModel::setTimelineSnap,
                        )

                        Text("Proxy Scrub Resolution", style = MaterialTheme.typography.labelLarge, color = ExcevoColors.TextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProxyQuality.entries.forEach { quality ->
                                val selected = uiState.preferences.proxyQuality == quality
                                Surface(
                                    color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setProxyQuality(quality) }
                                        .testTag("proxy_option_${quality.name}"),
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = quality.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextPrimary,
                                        )
                                    }
                                }
                            }
                        }

                        Text("Scrub Cache Limit", style = MaterialTheme.typography.labelLarge, color = ExcevoColors.TextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(256, 512, 1024).forEach { mb ->
                                val selected = uiState.preferences.scrubCacheLimitMb == mb
                                Surface(
                                    color = if (selected) ExcevoColors.PlayheadAmberContainer else ExcevoColors.ElevatedSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (selected) ExcevoColors.PlayheadAmber else ExcevoColors.HairlineBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setScrubCacheLimitMb(mb) },
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$mb MB",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) ExcevoColors.PlayheadAmber else ExcevoColors.TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Storage Management
            item {
                val stats = uiState.storageStats
                Surface(
                    color = ExcevoColors.StudioPanel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Storage, contentDescription = null, tint = ExcevoColors.AccentLavender)
                                Text("Storage Management", style = MaterialTheme.typography.titleMedium, color = ExcevoColors.TextPrimary)
                            }
                            SpecBadge(StorageManager.formatBytes(stats.totalBytes), highlight = true)
                        }
                        StorageRow("Project JSON Documents", StorageManager.formatBytes(stats.projectFilesBytes))
                        StorageRow("Frame Thumbnails", StorageManager.formatBytes(stats.thumbnailBytes))
                        StorageRow("EXCEVO Library Assets", StorageManager.formatBytes(stats.libraryBytes))
                        StorageRow("Temporary Cache", StorageManager.formatBytes(stats.cacheBytes))
                        Button(
                            onClick = viewModel::clearCache,
                            enabled = !uiState.isClearingCache,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExcevoColors.ElevatedSurface,
                                contentColor = ExcevoColors.AccentLavender,
                            ),
                            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                            modifier = Modifier.fillMaxWidth().testTag("profile_clear_cache_button"),
                        ) {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.isClearingCache) "Clearing..." else "Clear Temporary Cache")
                        }
                    }
                }
            }

            // EXCEVO Library, Extensions, Connected Devices, Help
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileNavigationCard(
                        icon = Icons.Outlined.FolderZip,
                        title = stringResource(R.string.excevo_library_title),
                        subtitle = "${uiState.libraryItems.size} personal LUTs, overlays, fonts & presets",
                        testTag = "profile_library_card",
                        onClick = { showLibraryDialog = true },
                    )
                    ProfileNavigationCard(
                        icon = Icons.Outlined.Extension,
                        title = "Extensions & Engine Modules",
                        subtitle = "Active timeline, keyframe shake, and declarative grade modules",
                        testTag = "profile_extensions_card",
                        onClick = { showExtensionsDialog = true },
                    )
                    ProfileNavigationCard(
                        icon = Icons.Outlined.Devices,
                        title = "Connected Devices",
                        subtitle = "Current device active · Cloud sync joins in Phase 6",
                        testTag = "profile_devices_card",
                        onClick = { showDevicesDialog = true },
                    )
                    ProfileNavigationCard(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        title = "Help, Schema & Free Policy",
                        subtitle = "excevo-preset/v1 documentation & 100% free pledge",
                        testTag = "profile_help_card",
                        onClick = { showHelpDialog = true },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (bottomContentPadding - 12.dp).coerceAtLeast(12.dp)),
        )
    }

    if (showRolesDialog) {
        RolesPermissionsDialog(onDismiss = { showRolesDialog = false })
    }
    if (showLibraryDialog) {
        ExcevoLibraryDialog(
            items = uiState.libraryItems,
            selectedImportType = selectedImportType,
            isImporting = uiState.isImportingAsset,
            onSelectImportType = { selectedImportType = it },
            onImportClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
            onDeleteItem = viewModel::deleteLibraryItem,
            onDismiss = { showLibraryDialog = false },
        )
    }
    if (showExtensionsDialog) {
        ExtensionsDialog(onDismiss = { showExtensionsDialog = false })
    }
    if (showDevicesDialog) {
        ConnectedDevicesDialog(onDismiss = { showDevicesDialog = false })
    }
    if (showHelpDialog) {
        HelpSupportDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
private fun UsageStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = ExcevoColors.AccentLavender)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextSecondary)
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ExcevoColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ExcevoColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ExcevoColors.TextPrimary,
                checkedTrackColor = ExcevoColors.AccentPurple,
                uncheckedThumbColor = ExcevoColors.TextSecondary,
                uncheckedTrackColor = ExcevoColors.ElevatedSurface,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ExcevoColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = ExcevoColors.TextPrimary)
    }
}

@Composable
private fun ProfileNavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        color = ExcevoColors.StudioPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = ExcevoColors.AccentLavender)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ExcevoColors.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ExcevoColors.TextSecondary)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = ExcevoColors.TextMuted)
        }
    }
}
