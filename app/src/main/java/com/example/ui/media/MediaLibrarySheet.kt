package com.example.ui.media

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.engine.TimelineMath
import com.example.core.model.MediaType
import com.example.core.ports.MediaImportProgress
import com.example.data.media.DeviceFolderMediaItem
import com.example.data.media.GrantedFolderItem
import com.example.data.media.MediaLibraryRepository
import com.example.data.media.RecentMediaItem
import com.example.data.storage.StorageManager
import com.example.ui.theme.ExcevoColors
import kotlinx.coroutines.launch
import java.io.File

enum class MediaLibraryTab(val label: String) {
    RECENT("Recent"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    FOLDERS("Folders"),
}

data class MediaLibraryEntry(
    val uriString: String,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val durationUs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val thumbnailPath: String? = null,
)

@Composable
fun MediaLibrarySheet(
    visible: Boolean,
    initialTab: MediaLibraryTab = MediaLibraryTab.RECENT,
    mediaLibraryRepository: MediaLibraryRepository? = null,
    isImporting: Boolean = false,
    importProgress: MediaImportProgress? = null,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onImportUris: (List<String>) -> Unit,
) {
    if (!visible) return

    BackHandler(enabled = !isImporting) { onDismiss() }

    val scope = rememberCoroutineScope()
    var activeTab by remember(visible, initialTab) { mutableStateOf(initialTab) }
    val recentItems by (mediaLibraryRepository?.observeRecentMedia()
        ?: remember { kotlinx.coroutines.flow.flowOf(emptyList()) })
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val grantedFolders by (mediaLibraryRepository?.observeGrantedFolders()
        ?: remember { kotlinx.coroutines.flow.flowOf(emptyList()) })
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedFolder by remember { mutableStateOf<GrantedFolderItem?>(null) }
    var folderEntries by remember { mutableStateOf<List<DeviceFolderMediaItem>>(emptyList()) }
    var isScanningFolder by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val selectedUris = remember { mutableStateListOf<String>() }
    var previewEntry by remember { mutableStateOf<MediaLibraryEntry?>(null) }

    LaunchedEffect(grantedFolders) {
        if (selectedFolder == null && grantedFolders.isNotEmpty()) {
            selectedFolder = grantedFolders.first()
        }
    }

    LaunchedEffect(selectedFolder) {
        val folder = selectedFolder
        if (folder != null && mediaLibraryRepository != null) {
            isScanningFolder = true
            localError = null
            mediaLibraryRepository.listMediaInFolder(folder.treeUri)
                .onSuccess { folderEntries = it }
                .onFailure { localError = it.localizedMessage ?: "Unable to read folder contents." }
            isScanningFolder = false
        } else {
            folderEntries = emptyList()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris.map(Uri::toString))
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris.map(Uri::toString))
        }
    }

    val audioDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris.map(Uri::toString))
        }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris.map(Uri::toString))
        }
    }

    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null && mediaLibraryRepository != null) {
            scope.launch {
                mediaLibraryRepository.addGrantedFolder(treeUri.toString())
                    .onSuccess { folder ->
                        selectedFolder = folder
                        activeTab = MediaLibraryTab.FOLDERS
                    }
                    .onFailure { localError = it.localizedMessage ?: "Failed to link folder." }
            }
        }
    }

    val visibleEntries: List<MediaLibraryEntry> = remember(
        activeTab,
        recentItems,
        folderEntries,
    ) {
        when (activeTab) {
            MediaLibraryTab.RECENT -> recentItems.map { it.toLibraryEntry() }
            MediaLibraryTab.IMAGES -> recentItems.filter { it.mediaType == MediaType.IMAGE }.map { it.toLibraryEntry() }
            MediaLibraryTab.VIDEOS -> recentItems.filter { it.mediaType == MediaType.VIDEO }.map { it.toLibraryEntry() }
            MediaLibraryTab.AUDIO -> recentItems.filter { it.mediaType == MediaType.AUDIO }.map { it.toLibraryEntry() }
            MediaLibraryTab.FOLDERS -> folderEntries.map {
                MediaLibraryEntry(
                    uriString = it.uriString,
                    displayName = it.displayName,
                    mediaType = it.mediaType,
                    mimeType = it.mimeType,
                    fileSizeBytes = it.sizeBytes,
                )
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = ExcevoColors.StudioPanel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 640.dp)
                .testTag("media_library_sheet"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Media Library",
                            style = MaterialTheme.typography.titleLarge,
                            color = ExcevoColors.TextPrimary,
                        )
                        Text(
                            text = "Import images, videos & audio tracks into your timeline",
                            style = MaterialTheme.typography.labelSmall,
                            color = ExcevoColors.TextSecondary,
                        )
                    }
                    IconButton(
                        onClick = { if (!isImporting) onDismiss() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("media_library_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close Media Library",
                            tint = ExcevoColors.TextSecondary,
                        )
                    }
                }

                // Universal Android Picker Launch Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DevicePickerActionChip(
                        icon = Icons.Outlined.PhotoLibrary,
                        label = "Pick Photos",
                        accent = ExcevoColors.AccentLavender,
                        enabled = !isImporting,
                        testTag = "picker_photos_button",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    DevicePickerActionChip(
                        icon = Icons.Outlined.VideoLibrary,
                        label = "Pick Videos",
                        accent = ExcevoColors.AccentVioletGlow,
                        enabled = !isImporting,
                        testTag = "picker_videos_button",
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                    )
                    DevicePickerActionChip(
                        icon = Icons.Outlined.LibraryMusic,
                        label = "Pick Audio",
                        accent = ExcevoColors.PlayheadAmber,
                        enabled = !isImporting,
                        testTag = "picker_audio_button",
                        onClick = {
                            audioDocumentLauncher.launch(arrayOf("audio/*"))
                        },
                    )
                    DevicePickerActionChip(
                        icon = Icons.Outlined.PermMedia,
                        label = "All Files",
                        accent = ExcevoColors.AccentPurple,
                        enabled = !isImporting,
                        testTag = "picker_all_files_button",
                        onClick = {
                            allFilesLauncher.launch(arrayOf("image/*", "video/*", "audio/*"))
                        },
                    )
                    DevicePickerActionChip(
                        icon = Icons.Outlined.CreateNewFolder,
                        label = "Link Folder",
                        accent = ExcevoColors.SuccessEmerald,
                        enabled = !isImporting && mediaLibraryRepository != null,
                        testTag = "picker_link_folder_button",
                        onClick = { folderTreeLauncher.launch(null) },
                    )
                }

                // Category Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MediaLibraryTab.entries.forEach { tab ->
                        val selected = tab == activeTab
                        Surface(
                            color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { activeTab = tab }
                                .testTag("media_library_tab_${tab.name}"),
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                // Folder Selector Strip when FOLDERS tab active
                if (activeTab == MediaLibraryTab.FOLDERS && grantedFolders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        grantedFolders.forEach { folder ->
                            val isSelected = selectedFolder?.treeUri == folder.treeUri
                            Surface(
                                color = if (isSelected) ExcevoColors.AccentPurpleContainer else ExcevoColors.VoidCharcoal,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedFolder = folder },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = ExcevoColors.AccentLavender,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = folder.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExcevoColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Import Progress & Error Banners
                if (isImporting) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            color = ExcevoColors.AccentPurple,
                            trackColor = ExcevoColors.ElevatedSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        importProgress?.let { prog ->
                            Text(
                                text = if (prog.totalCount > 0) {
                                    "Importing ${prog.currentIndex} of ${prog.totalCount} · ${prog.currentLabel}"
                                } else {
                                    prog.currentLabel
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.AccentLavender,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                val activeError = localError ?: errorMessage
                if (!activeError.isNullOrBlank()) {
                    Surface(
                        color = ExcevoColors.DangerRed.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExcevoColors.DangerRed.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("media_library_error_banner"),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ExcevoColors.DangerRed, modifier = Modifier.size(18.dp))
                            Text(
                                text = activeError,
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcevoColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Main Media List
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (isScanningFolder) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ExcevoColors.AccentPurple)
                        }
                    } else if (visibleEntries.isEmpty()) {
                        EmptyMediaCategoryCard(
                            tab = activeTab,
                            hasFolders = grantedFolders.isNotEmpty(),
                            onPrimaryAction = {
                                when (activeTab) {
                                    MediaLibraryTab.IMAGES -> photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    MediaLibraryTab.VIDEOS -> videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                    MediaLibraryTab.AUDIO -> audioDocumentLauncher.launch(arrayOf("audio/*"))
                                    MediaLibraryTab.FOLDERS -> folderTreeLauncher.launch(null)
                                    MediaLibraryTab.RECENT -> allFilesLauncher.launch(arrayOf("image/*", "video/*", "audio/*"))
                                }
                            },
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("media_library_items_list"),
                        ) {
                            items(visibleEntries, key = { it.uriString }) { entry ->
                                val isSelected = entry.uriString in selectedUris
                                MediaEntryRowCard(
                                    entry = entry,
                                    isSelected = isSelected,
                                    onClick = {
                                        previewEntry = entry
                                        if (isSelected) {
                                            selectedUris.remove(entry.uriString)
                                        } else {
                                            selectedUris.add(entry.uriString)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Selected Media Preview & Import CTA
                previewEntry?.let { preview ->
                    Surface(
                        color = ExcevoColors.VoidCharcoal,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("media_library_preview_card"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MediaEntryThumbnail(entry = preview, modifier = Modifier.size(46.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preview.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ExcevoColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatEntryMeta(preview),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ExcevoColors.AccentLavender,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedUris.isEmpty()) {
                            "Use device pickers above or select recent items"
                        } else {
                            "${selectedUris.size} item(s) selected"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcevoColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onImportUris(selectedUris.toList()) },
                        enabled = selectedUris.isNotEmpty() && !isImporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ExcevoColors.AccentPurple,
                            contentColor = ExcevoColors.TextPrimary,
                        ),
                        modifier = Modifier.testTag("media_library_confirm_import_button"),
                    ) {
                        Text(if (selectedUris.size > 1) "Import (${selectedUris.size})" else "Import Selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicePickerActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        color = ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = ExcevoColors.TextPrimary)
        }
    }
}

@Composable
private fun MediaEntryRowCard(
    entry: MediaLibraryEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("media_library_entry_${entry.displayName}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MediaEntryThumbnail(entry = entry, modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = ExcevoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatEntryMeta(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Selected",
                    tint = ExcevoColors.AccentLavender,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaEntryThumbnail(
    entry: MediaLibraryEntry,
    modifier: Modifier = Modifier,
) {
    val thumbFile = entry.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ExcevoColors.VoidCharcoal)
            .border(1.dp, ExcevoColors.HairlineBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbFile != null) {
            AsyncImage(
                model = thumbFile,
                contentDescription = entry.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = when (entry.mediaType) {
                    MediaType.IMAGE -> Icons.Outlined.Image
                    MediaType.VIDEO -> Icons.Outlined.VideoLibrary
                    MediaType.AUDIO -> Icons.Outlined.Audiotrack
                },
                contentDescription = entry.mediaType.name,
                tint = when (entry.mediaType) {
                    MediaType.IMAGE -> ExcevoColors.AccentLavender
                    MediaType.VIDEO -> ExcevoColors.AccentPurple
                    MediaType.AUDIO -> ExcevoColors.PlayheadAmber
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyMediaCategoryCard(
    tab: MediaLibraryTab,
    hasFolders: Boolean,
    onPrimaryAction: () -> Unit,
) {
    Surface(
        color = ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (tab) {
                    MediaLibraryTab.RECENT -> "No recent media imported yet"
                    MediaLibraryTab.IMAGES -> "No recent images yet"
                    MediaLibraryTab.VIDEOS -> "No recent videos yet"
                    MediaLibraryTab.AUDIO -> "No recent audio tracks yet"
                    MediaLibraryTab.FOLDERS -> if (hasFolders) "No supported media in selected folder" else "No device folders linked yet"
                },
                style = MaterialTheme.typography.titleSmall,
                color = ExcevoColors.TextPrimary,
            )
            Text(
                text = when (tab) {
                    MediaLibraryTab.FOLDERS -> "Link a device folder via Android Storage Access Framework without broad storage permissions."
                    else -> "Pick directly from your device using the buttons above or below."
                },
                style = MaterialTheme.typography.bodySmall,
                color = ExcevoColors.TextSecondary,
            )
            OutlinedButton(
                onClick = onPrimaryAction,
                border = BorderStroke(1.dp, ExcevoColors.AccentPurple),
                modifier = Modifier.testTag("empty_library_pick_button"),
            ) {
                Text(
                    text = when (tab) {
                        MediaLibraryTab.IMAGES -> "Browse Device Photos"
                        MediaLibraryTab.VIDEOS -> "Browse Device Videos"
                        MediaLibraryTab.AUDIO -> "Browse Audio Files"
                        MediaLibraryTab.FOLDERS -> "Select Device Folder"
                        MediaLibraryTab.RECENT -> "Browse Device Media"
                    },
                    color = ExcevoColors.AccentLavender,
                )
            }
        }
    }
}

private fun RecentMediaItem.toLibraryEntry(): MediaLibraryEntry = MediaLibraryEntry(
    uriString = sourceUri,
    displayName = displayName,
    mediaType = mediaType,
    mimeType = mimeType,
    durationUs = durationUs,
    width = width,
    height = height,
    sampleRateHz = sampleRateHz,
    channelCount = channelCount,
    fileSizeBytes = fileSizeBytes,
    thumbnailPath = thumbnailPath,
)

private fun formatEntryMeta(entry: MediaLibraryEntry): String {
    val parts = mutableListOf<String>()
    parts.add(entry.mediaType.name)
    if (entry.width > 0 && entry.height > 0) {
        parts.add("${entry.width}×${entry.height}")
    }
    if (entry.durationUs > 0L && entry.mediaType != MediaType.IMAGE) {
        parts.add(TimelineMath.formatDurationCompact(entry.durationUs))
    }
    if (entry.sampleRateHz > 0) {
        parts.add("${entry.sampleRateHz / 1000.0}kHz · ${entry.channelCount}ch")
    }
    if (entry.fileSizeBytes > 0L) {
        parts.add(StorageManager.formatBytes(entry.fileSizeBytes))
    }
    return parts.joinToString(" · ")
}
