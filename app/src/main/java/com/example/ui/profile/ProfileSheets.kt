package com.example.ui.profile

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.LibraryAssetItem
import com.example.core.model.LibraryItemType
import com.example.core.ports.UserRole
import com.example.data.storage.StorageManager
import com.example.ui.components.SpecBadge
import com.example.ui.theme.ExcevoColors

@Composable
fun RolesPermissionsDialog(
    onDismiss: () -> Unit,
) {
    val descriptions = listOf(
        UserRole.GUEST to "Local editing, built-in presets, browse community (Phase 6) without sign-in.",
        UserRole.USER to "Google Sign-In via Credential Manager; bookmark, like, apply, and report presets.",
        UserRole.CREATOR to "Upload original presets (Pending Review), edit descriptions, manage My Uploads.",
        UserRole.MODERATOR to "Review Pending Review queue, approve/reject uploads, inspect abuse reports.",
        UserRole.ADMIN to "Full moderation authority, user role management, and policy enforcement.",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = ExcevoColors.AccentLavender)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Roles & Permissions", color = ExcevoColors.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Current session: Guest · Local Studio. Cloud Google Sign-In activates in Phase 6.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.AccentLavender,
                )
                descriptions.forEach { (role, summary) ->
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(role.label, style = MaterialTheme.typography.labelLarge, color = ExcevoColors.TextPrimary)
                            Text(summary, style = MaterialTheme.typography.bodyMedium, color = ExcevoColors.TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ExcevoColors.AccentLavender)
            }
        },
    )
}

@Composable
fun ExcevoLibraryDialog(
    items: List<LibraryAssetItem>,
    selectedImportType: LibraryItemType,
    isImporting: Boolean,
    onSelectImportType: (LibraryItemType) -> Unit,
    onImportClick: () -> Unit,
    onDeleteItem: (LibraryAssetItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderZip, contentDescription = null, tint = ExcevoColors.AccentLavender)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXCEVO Library", color = ExcevoColors.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Store personal LUTs, overlays, fonts, and validated .excevo.json presets locally.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LibraryItemType.entries) { type ->
                        val selected = type == selectedImportType
                        Surface(
                            color = if (selected) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectImportType(type) },
                        ) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) ExcevoColors.AccentLavender else ExcevoColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Button(
                    onClick = onImportClick,
                    enabled = !isImporting,
                    colors = ButtonDefaults.buttonColors(containerColor = ExcevoColors.AccentPurple),
                    modifier = Modifier.fillMaxWidth().testTag("library_import_asset_button"),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import ${selectedImportType.label} File")
                }

                if (items.isEmpty()) {
                    Text(
                        text = "No personal library assets imported yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExcevoColors.TextMuted,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(170.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
                            Surface(
                                color = ExcevoColors.ElevatedSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ExcevoColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${item.itemType.label} · ${StorageManager.formatBytes(item.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ExcevoColors.TextSecondary,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteItem(item) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete ${item.name}",
                                            tint = ExcevoColors.DangerRed,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = ExcevoColors.AccentLavender)
            }
        },
    )
}

@Composable
fun ExtensionsDialog(
    onDismiss: () -> Unit,
) {
    val modules = listOf(
        Triple("Core Timeline Engine", "v2.0 · Pure Kotlin KMP", "Non-destructive clip math, undo/redo command stack, atomic JSON persistence."),
        Triple("Shake Keyframe Synthesizer", "v1.0 · Built-in", "Generates decaying X/Y oscillation keyframes inside clips from Shake presets."),
        Triple("Color Grade Descriptor", "v1.0 · Built-in", "Validated exposure, contrast, saturation, temperature, tint, highlights, shadows."),
        Triple("Media3 GLES Renderer", "Phase 3 Scheduled", "Real-time GPU effect & color shader pipeline + H.264/HEVC Transformer export."),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Extension, contentDescription = null, tint = ExcevoColors.AccentLavender)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extensions & Modules", color = ExcevoColors.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "For Play Policy compliance, EXCEVO uses declarative JSON/shader presets only — dynamic code loading (.dex/.jar/.so) is blocked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )
                modules.forEach { (name, badge, desc) ->
                    Surface(
                        color = ExcevoColors.ElevatedSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, style = MaterialTheme.typography.labelLarge, color = ExcevoColors.TextPrimary)
                                SpecBadge(badge, highlight = !badge.contains("Phase"))
                            }
                            Text(desc, style = MaterialTheme.typography.bodyMedium, color = ExcevoColors.TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ExcevoColors.AccentLavender)
            }
        },
    )
}

@Composable
fun ConnectedDevicesDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Devices, contentDescription = null, tint = ExcevoColors.AccentLavender)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connected Devices", color = ExcevoColors.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = ExcevoColors.ElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ExcevoColors.AccentPurple.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.labelLarge, color = ExcevoColors.TextPrimary)
                            SpecBadge("ACTIVE", highlight = true)
                        }
                        Text(
                            text = "Android SDK ${Build.VERSION.SDK_INT} · Local Studio Storage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ExcevoColors.TextSecondary,
                        )
                    }
                }
                Text(
                    text = "Cross-device project sync between Android and iOS (SwiftUI/AVFoundation) connects in Phase 6 once Firebase Auth + Firestore project manifests are enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ExcevoColors.AccentLavender)
            }
        },
    )
}

@Composable
fun HelpSupportDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ExcevoColors.StudioPanel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = ExcevoColors.AccentLavender)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Help & Schema Guide", color = ExcevoColors.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Preset Format (excevo-preset/v1)",
                    style = MaterialTheme.typography.labelLarge,
                    color = ExcevoColors.AccentLavender,
                )
                Text(
                    text = "Shareable presets use UTF-8 JSON (<= 512 KB) containing category (SHAKES, EFFECTS, CC, FILTERS, TRANSITIONS, TEXT_ANIMATIONS), numeric parameters, and metadata. Executable files and scripts are automatically rejected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )
                Text(
                    text = "100% Free Community Commitment",
                    style = MaterialTheme.typography.labelLarge,
                    color = ExcevoColors.SuccessEmerald,
                )
                Text(
                    text = "All community presets, downloads, and creator tools in EXCEVO EDIT are permanently free. No paid items, checkout flows, or monetization exist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExcevoColors.TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = ExcevoColors.AccentLavender)
            }
        },
    )
}
