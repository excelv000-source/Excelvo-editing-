package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.AnimatableProperty
import com.example.core.model.TimelineClip
import com.example.ui.theme.ExcevoColors
import kotlin.math.roundToInt

@Composable
fun AudioStudioPanel(
    clip: TimelineClip?,
    onSetAudioEnvelope: (volume: Float, isMuted: Boolean, fadeInUs: Long, fadeOutUs: Long) -> Unit,
    onToggleVolumeKeyframe: (AnimatableProperty, Float) -> Unit,
    onTrim: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
) {
    if (clip == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select an audio or video clip to edit Volume & Fades",
                style = MaterialTheme.typography.labelMedium,
                color = ExcevoColors.TextMuted,
            )
        }
        return
    }

    var draftVol by remember(clip.id, clip.volume, clip.isMuted) {
        mutableFloatStateOf(if (clip.isMuted) 0f else clip.volume.coerceIn(0f, 1.5f))
    }
    var draftFadeInSec by remember(clip.id, clip.fadeInUs) {
        mutableFloatStateOf((clip.fadeInUs / 1_000_000f).coerceIn(0f, 3f))
    }
    var draftFadeOutSec by remember(clip.id, clip.fadeOutUs) {
        mutableFloatStateOf((clip.fadeOutUs / 1_000_000f).coerceIn(0f, 3f))
    }
    val maxFadeSec = ((clip.timelineDurationUs / 2_000_000f).coerceIn(0.1f, 3f))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Quick Audio actions + Mute toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (clip.isMuted) ExcevoColors.AccentPurpleContainer else ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (clip.isMuted) ExcevoColors.AccentPurple else ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onSetAudioEnvelope(
                            clip.volume,
                            !clip.isMuted,
                            (draftFadeInSec * 1_000_000L).toLong(),
                            (draftFadeOutSec * 1_000_000L).toLong(),
                        )
                    }
                    .testTag("audio_studio_mute"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (clip.isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = "Mute",
                        tint = ExcevoColors.AccentLavender,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(if (clip.isMuted) "Muted" else "Mute", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                }
            }

            Surface(
                color = ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleVolumeKeyframe(AnimatableProperty.VOLUME, draftVol) }
                    .testTag("audio_volume_keyframe"),
            ) {
                Text("◇ Vol KF", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.PlayheadAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }

            Surface(
                color = ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTrim),
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Trim", tint = ExcevoColors.TextPrimary, modifier = Modifier.size(13.dp))
                    Text("Trim", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                }
            }

            Surface(
                color = ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSplit),
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ContentCut, contentDescription = "Split", tint = ExcevoColors.TextPrimary, modifier = Modifier.size(13.dp))
                    Text("Split", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.TextPrimary)
                }
            }

            Surface(
                color = ExcevoColors.ElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ExcevoColors.HairlineBorder),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete),
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = ExcevoColors.DangerRed, modifier = Modifier.size(13.dp))
                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = ExcevoColors.DangerRed)
                }
            }
        }

        // Volume Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Vol ${(draftVol * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = ExcevoColors.AccentLavender,
                modifier = Modifier.width(64.dp),
            )
            Slider(
                value = draftVol,
                onValueChange = { draftVol = it },
                onValueChangeFinished = {
                    onSetAudioEnvelope(
                        draftVol,
                        draftVol <= 0.01f,
                        (draftFadeInSec * 1_000_000L).toLong(),
                        (draftFadeOutSec * 1_000_000L).toLong(),
                    )
                },
                valueRange = 0f..1.5f,
                colors = audioSliderColors(),
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .testTag("audio_studio_volume_slider"),
            )
        }

        // Fade In & Fade Out Sliders side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fade In ${String.format("%.1fs", draftFadeInSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.PlayheadAmber,
                )
                Slider(
                    value = draftFadeInSec.coerceIn(0f, maxFadeSec),
                    onValueChange = { draftFadeInSec = it },
                    onValueChangeFinished = {
                        onSetAudioEnvelope(
                            draftVol,
                            clip.isMuted,
                            (draftFadeInSec * 1_000_000L).toLong(),
                            (draftFadeOutSec * 1_000_000L).toLong(),
                        )
                    },
                    valueRange = 0f..maxFadeSec,
                    colors = audioSliderColors(),
                    modifier = Modifier
                        .height(22.dp)
                        .testTag("audio_studio_fade_in_slider"),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fade Out ${String.format("%.1fs", draftFadeOutSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExcevoColors.PlayheadAmber,
                )
                Slider(
                    value = draftFadeOutSec.coerceIn(0f, maxFadeSec),
                    onValueChange = { draftFadeOutSec = it },
                    onValueChangeFinished = {
                        onSetAudioEnvelope(
                            draftVol,
                            clip.isMuted,
                            (draftFadeInSec * 1_000_000L).toLong(),
                            (draftFadeOutSec * 1_000_000L).toLong(),
                        )
                    },
                    valueRange = 0f..maxFadeSec,
                    colors = audioSliderColors(),
                    modifier = Modifier
                        .height(22.dp)
                        .testTag("audio_studio_fade_out_slider"),
                )
            }
        }
    }
}

@Composable
private fun audioSliderColors() = SliderDefaults.colors(
    thumbColor = ExcevoColors.AccentLavender,
    activeTrackColor = ExcevoColors.AccentPurple,
    inactiveTrackColor = ExcevoColors.HairlineBorder,
)
