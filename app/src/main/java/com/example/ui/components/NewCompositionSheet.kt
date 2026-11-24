package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExcevoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCompositionBottomSheet(
    visible: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onNewProject: () -> Unit,
    onImportVideo: () -> Unit,
    onImportImages: () -> Unit,
    onImportAudio: (() -> Unit)? = null,
    onBrowseMediaLibrary: (() -> Unit)? = null,
    onBlankProject: () -> Unit,
) {
    BackHandler(enabled = visible) {
        if (!isBusy) onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.60f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isBusy,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                Surface(
                    color = ExcevoColors.StudioPanel,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .testTag("new_composition_bottom_sheet"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BottomSheetDefaults.DragHandle(color = ExcevoColors.TextMuted)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "New Composition",
                                style = MaterialTheme.typography.headlineSmall,
                                color = ExcevoColors.TextPrimary,
                            )
                            Text(
                                text = "Configure a custom sequence or import media directly into EXCEVO Studio.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ExcevoColors.TextSecondary,
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        CompositionActionTile(
                            icon = Icons.Outlined.MovieCreation,
                            title = "New Project",
                            subtitle = "Choose aspect ratio, resolution, and frame rate",
                            accent = ExcevoColors.AccentPurple,
                            enabled = !isBusy,
                            testTag = "sheet_action_new_project",
                            onClick = onNewProject,
                        )

                        CompositionActionTile(
                            icon = Icons.Outlined.VideoLibrary,
                            title = "Import Video",
                            subtitle = "Pick a clip from gallery and open directly in Studio",
                            accent = ExcevoColors.AccentVioletGlow,
                            enabled = !isBusy,
                            testTag = "sheet_action_import_video",
                            onClick = onImportVideo,
                        )

                        CompositionActionTile(
                            icon = Icons.Outlined.Image,
                            title = "Import Image",
                            subtitle = "Pick one or more stills for a multi-clip sequence",
                            accent = ExcevoColors.AccentLavender,
                            enabled = !isBusy,
                            testTag = "sheet_action_import_images",
                            onClick = onImportImages,
                        )

                        if (onImportAudio != null) {
                            CompositionActionTile(
                                icon = Icons.Outlined.Audiotrack,
                                title = "Import Audio",
                                subtitle = "Pick MP3, WAV, AAC, or M4A tracks onto A1 waveform",
                                accent = ExcevoColors.PlayheadAmber,
                                enabled = !isBusy,
                                testTag = "sheet_action_import_audio",
                                onClick = onImportAudio,
                            )
                        }

                        if (onBrowseMediaLibrary != null) {
                            CompositionActionTile(
                                icon = Icons.Outlined.PermMedia,
                                title = "Media Library",
                                subtitle = "Recent imports, device folders & multi-format picker",
                                accent = ExcevoColors.SuccessEmerald,
                                enabled = !isBusy,
                                testTag = "sheet_action_media_library",
                                onClick = onBrowseMediaLibrary,
                            )
                        }

                        CompositionActionTile(
                            icon = Icons.Outlined.AddBox,
                            title = "Blank Project",
                            subtitle = "Jump straight into an empty 9:16 30fps timeline",
                            accent = ExcevoColors.PlayheadAmber,
                            enabled = !isBusy,
                            testTag = "sheet_action_blank_project",
                            onClick = onBlankProject,
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompositionActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        color = ExcevoColors.ElevatedSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ExcevoColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ExcevoColors.TextSecondary,
                )
            }
        }
    }
}
