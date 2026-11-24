package com.example.core.command

import com.example.core.engine.EffectGradeMath
import com.example.core.engine.TimelineMath
import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.BlendModeType
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.Keyframe
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import com.example.core.model.TransformSpec
import kotlin.math.abs
import kotlin.math.sin

interface EditCommand {
    val description: String
    fun apply(project: TimelineProject, timestampMs: Long): TimelineProject
}

class AddAssetClipCommand(
    private val asset: MediaAsset,
    private val clipId: String,
    private val defaultImageDurationUs: Long = TimelineMath.DEFAULT_IMAGE_DURATION_US,
) : EditCommand {
    override val description: String = "Add ${asset.displayName}"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val effectiveDurationUs = when (asset.mediaType) {
            MediaType.IMAGE -> defaultImageDurationUs
            MediaType.VIDEO, MediaType.AUDIO -> asset.durationUs.coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_US)
        }
        val targetTrackType = if (asset.mediaType == MediaType.AUDIO) {
            TrackType.AUDIO
        } else {
            TrackType.VIDEO_PRIMARY
        }

        val updatedAssets = project.assets + (asset.id to asset)
        val updatedTracks = project.tracks.map { track ->
            if (track.type != targetTrackType) {
                track
            } else {
                val startUs = track.clips.maxOfOrNull { it.timelineEndUs } ?: 0L
                val newClip = TimelineClip(
                    id = clipId,
                    assetId = asset.id,
                    trackId = track.id,
                    mediaType = asset.mediaType,
                    label = asset.displayName,
                    timelineStartUs = startUs,
                    timelineDurationUs = effectiveDurationUs,
                    sourceStartUs = 0L,
                    sourceEndUs = effectiveDurationUs,
                    sourceTotalDurationUs = if (asset.mediaType == MediaType.IMAGE) {
                        60_000_000L // Images can be extended up to 60s
                    } else {
                        effectiveDurationUs
                    },
                    thumbnailPath = asset.thumbnailPath,
                )
                track.copy(clips = track.clips + newClip)
            }
        }
        return project.copy(
            assets = updatedAssets,
            tracks = updatedTracks,
            updatedAtMs = timestampMs,
        )
    }
}

class TrimClipCommand(
    private val clipId: String,
    private val newSourceStartUs: Long,
    private val newSourceEndUs: Long,
) : EditCommand {
    override val description: String = "Trim clip"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) {
                track
            } else {
                val mapped = track.clips.map { clip ->
                    if (clip.id != clipId) {
                        clip
                    } else {
                        val clampedStart = newSourceStartUs.coerceIn(
                            0L,
                            (clip.sourceTotalDurationUs - TimelineMath.MIN_CLIP_DURATION_US).coerceAtLeast(0L)
                        )
                        val clampedEnd = newSourceEndUs.coerceIn(
                            clampedStart + TimelineMath.MIN_CLIP_DURATION_US,
                            clip.sourceTotalDurationUs.coerceAtLeast(clampedStart + TimelineMath.MIN_CLIP_DURATION_US)
                        )
                        val newTimelineDuration = ((clampedEnd - clampedStart) / clip.speedMultiplier).toLong()
                            .coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_US)
                        clip.copy(
                            sourceStartUs = clampedStart,
                            sourceEndUs = clampedEnd,
                            timelineDurationUs = newTimelineDuration,
                        )
                    }
                }
                val normalized = if (track.type == TrackType.VIDEO_PRIMARY) {
                    TimelineMath.recalculateSequentialStarts(mapped)
                } else {
                    mapped
                }
                track.copy(clips = normalized)
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class SplitClipCommand(
    private val clipId: String,
    private val splitAtTimelineUs: Long,
    private val secondClipId: String,
) : EditCommand {
    override val description: String = "Split clip"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        var didSplit = false
        val updatedTracks = project.tracks.map { track ->
            val target = track.clips.firstOrNull { it.id == clipId }
            if (target == null) {
                track
            } else {
                val minEdge = target.timelineStartUs + TimelineMath.MIN_CLIP_DURATION_US
                val maxEdge = target.timelineEndUs - TimelineMath.MIN_CLIP_DURATION_US
                if (splitAtTimelineUs !in minEdge..maxEdge) {
                    track
                } else {
                    didSplit = true
                    val leftTimelineDur = splitAtTimelineUs - target.timelineStartUs
                    val rightTimelineDur = target.timelineEndUs - splitAtTimelineUs
                    val splitSourceUs = TimelineMath.timelineUsToSourceUs(target, splitAtTimelineUs)

                    val leftClip = target.copy(
                        timelineDurationUs = leftTimelineDur,
                        sourceEndUs = splitSourceUs,
                    )
                    val rightClip = target.copy(
                        id = secondClipId,
                        timelineStartUs = splitAtTimelineUs,
                        timelineDurationUs = rightTimelineDur,
                        sourceStartUs = splitSourceUs,
                    )
                    val newClips = track.clips.flatMap { clip ->
                        if (clip.id == clipId) listOf(leftClip, rightClip) else listOf(clip)
                    }
                    track.copy(clips = newClips)
                }
            }
        }
        return if (didSplit) {
            project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
        } else {
            project
        }
    }
}

class MoveClipOrderCommand(
    private val clipId: String,
    private val deltaIndex: Int,
) : EditCommand {
    override val description: String = if (deltaIndex < 0) "Move clip earlier" else "Move clip later"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            val idx = track.clips.indexOfFirst { it.id == clipId }
            if (idx == -1) {
                track
            } else {
                val targetIdx = (idx + deltaIndex).coerceIn(0, track.clips.lastIndex)
                if (targetIdx == idx) {
                    track
                } else {
                    val mutable = track.clips.toMutableList()
                    val removed = mutable.removeAt(idx)
                    mutable.add(targetIdx, removed)
                    val normalized = if (track.type == TrackType.VIDEO_PRIMARY) {
                        TimelineMath.recalculateSequentialStarts(mutable)
                    } else {
                        mutable
                    }
                    track.copy(clips = normalized)
                }
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class DeleteClipCommand(
    private val clipId: String,
) : EditCommand {
    override val description: String = "Delete clip"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) {
                track
            } else {
                val remaining = track.clips.filterNot { it.id == clipId }
                val normalized = if (track.type == TrackType.VIDEO_PRIMARY) {
                    TimelineMath.recalculateSequentialStarts(remaining)
                } else {
                    remaining
                }
                track.copy(clips = normalized)
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateProjectSettingsCommand(
    private val name: String,
    private val aspectRatio: AspectRatioPreset,
    private val frameRate: FrameRatePreset,
    private val targetResolution: ExportResolutionPreset? = null,
    private val scaleMode: CanvasScaleMode? = null,
    private val backgroundId: String? = null,
    private val customWidthPx: Int? = null,
    private val customHeightPx: Int? = null,
    private val keepCustomResolution: Boolean = true,
) : EditCommand {
    override val description: String = "Update canvas settings"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val cleanName = name.trim().ifEmpty { project.name }
        return project.copy(
            name = cleanName,
            aspectRatio = aspectRatio,
            frameRate = frameRate,
            targetResolution = targetResolution ?: project.targetResolution,
            scaleMode = scaleMode ?: project.scaleMode,
            backgroundId = backgroundId ?: project.backgroundId,
            customWidthPx = if (keepCustomResolution) customWidthPx ?: project.customWidthPx else null,
            customHeightPx = if (keepCustomResolution) customHeightPx ?: project.customHeightPx else null,
            updatedAtMs = timestampMs,
        )
    }
}

class UpdateClipColorGradeCommand(
    private val clipId: String,
    private val colorGrade: ColorGradeSpec,
    override val description: String = "Adjust effects",
) : EditCommand {
    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) {
                track
            } else {
                track.copy(
                    clips = track.clips.map { clip ->
                        if (clip.id == clipId) clip.copy(colorGrade = colorGrade) else clip
                    }
                )
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateClipTransformCommand(
    private val clipId: String,
    private val transform: TransformSpec,
    override val description: String = "Transform clip",
) : EditCommand {
    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) {
                track
            } else {
                track.copy(
                    clips = track.clips.map { clip ->
                        if (clip.id == clipId) clip.copy(transform = transform) else clip
                    }
                )
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateClipAudioCommand(
    private val clipId: String,
    private val volume: Float,
    private val isMuted: Boolean,
    override val description: String = "Adjust clip audio",
) : EditCommand {
    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val clampedVol = volume.coerceIn(0f, 1.5f)
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) {
                track
            } else {
                track.copy(
                    clips = track.clips.map { clip ->
                        if (clip.id == clipId) {
                            clip.copy(volume = clampedVol, isMuted = isMuted)
                        } else {
                            clip
                        }
                    }
                )
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class DuplicateClipCommand(
    private val clipId: String,
    private val newClipId: String,
) : EditCommand {
    override val description: String = "Duplicate clip"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        var duplicated = false
        val updatedTracks = project.tracks.map { track ->
            val idx = track.clips.indexOfFirst { it.id == clipId }
            if (idx == -1) {
                track
            } else {
                duplicated = true
                val orig = track.clips[idx]
                val copy = orig.copy(
                    id = newClipId,
                    label = "${orig.label} (Copy)",
                    timelineStartUs = orig.timelineEndUs,
                )
                val mutable = track.clips.toMutableList()
                mutable.add(idx + 1, copy)
                val normalized = if (track.type == TrackType.VIDEO_PRIMARY) {
                    TimelineMath.recalculateSequentialStarts(mutable)
                } else {
                    mutable
                }
                track.copy(clips = normalized)
            }
        }
        return if (duplicated) project.copy(tracks = updatedTracks, updatedAtMs = timestampMs) else project
    }
}

class ToggleGroupClipCommand(
    private val clipId: String,
    private val playheadUs: Long,
    private val newGroupId: String,
) : EditCommand {
    override val description: String = "Toggle clip group"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val selected = project.allClips().firstOrNull { it.id == clipId } ?: return project
        val existingGroup = selected.groupId
        val targetClipIds: Set<String> = if (existingGroup != null) {
            project.allClips()
                .filter { it.groupId == existingGroup }
                .map { it.id }
                .toSet()
        } else {
            val activeAtPlayhead = project.tracks
                .filter { it.type != TrackType.AUDIO }
                .flatMap { it.clips }
                .filter { playheadUs in it.timelineStartUs..it.timelineEndUs && it.groupId == null }
                .map { it.id }
                .toSet()
            if (activeAtPlayhead.contains(clipId)) activeAtPlayhead else setOf(clipId)
        }

        val updatedTracks = project.tracks.map { track ->
            track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id !in targetClipIds) {
                        clip
                    } else if (existingGroup != null) {
                        clip.copy(groupId = null)
                    } else {
                        clip.copy(groupId = newGroupId)
                    }
                }
            )
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class AddTrackCommand(
    private val trackId: String,
    private val trackType: TrackType,
    private val name: String,
) : EditCommand {
    override val description: String = "Add $name track"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        if (project.tracks.any { it.id == trackId }) return project
        val newTrack = TimelineTrack(id = trackId, type = trackType, name = name)
        val mutable = project.tracks.toMutableList()
        val insertIdx = when (trackType) {
            TrackType.OVERLAY, TrackType.TEXT, TrackType.ADJUSTMENT -> {
                val primaryIdx = mutable.indexOfFirst { it.type == TrackType.VIDEO_PRIMARY }
                if (primaryIdx >= 0) primaryIdx else 0
            }
            TrackType.VIDEO_PRIMARY -> mutable.indexOfLast { it.type != TrackType.AUDIO }.let { if (it < 0) 0 else it + 1 }
            TrackType.AUDIO -> mutable.size
        }
        mutable.add(insertIdx.coerceIn(0, mutable.size), newTrack)
        return project.copy(tracks = mutable, updatedAtMs = timestampMs)
    }
}

class RemoveTrackCommand(
    private val trackId: String,
) : EditCommand {
    override val description: String = "Remove track"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val target = project.tracks.firstOrNull { it.id == trackId } ?: return project
        if (target.type == TrackType.VIDEO_PRIMARY) return project
        if (target.type == TrackType.AUDIO && project.tracks.count { it.type == TrackType.AUDIO } <= 1) return project
        val remaining = project.tracks.filterNot { it.id == trackId }
        return project.copy(tracks = remaining, updatedAtMs = timestampMs)
    }
}

class ToggleTrackVisibilityCommand(
    private val trackId: String,
) : EditCommand {
    override val description: String = "Toggle track visibility"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        var changed = false
        val updatedTracks = project.tracks.map { track ->
            if (track.id != trackId) {
                track
            } else {
                changed = true
                if (track.type == TrackType.AUDIO) {
                    track.copy(isMuted = !track.isMuted, isHidden = !track.isHidden)
                } else {
                    track.copy(isHidden = !track.isHidden)
                }
            }
        }
        return if (changed) project.copy(tracks = updatedTracks, updatedAtMs = timestampMs) else project
    }
}

class ResetClipAllCommand(
    private val clipId: String,
) : EditCommand {
    override val description: String = "Reset clip properties"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        var changed = false
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else {
                        changed = true
                        clip.copy(
                            transform = TransformSpec(),
                            colorGrade = ColorGradeSpec(),
                            effectStack = emptyList(),
                            blendMode = BlendModeType.NORMAL,
                            keyframes = emptyMap(),
                        )
                    }
                }
            )
        }
        return if (changed) project.copy(tracks = updatedTracks, updatedAtMs = timestampMs) else project
    }
}

class MoveClipToTrackCommand(
    private val clipId: String,
    private val targetTrackId: String,
) : EditCommand {
    override val description: String = "Move clip to track"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val sourceTrack = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: return project
        if (sourceTrack.id == targetTrackId) return project
        val targetTrack = project.tracks.firstOrNull { it.id == targetTrackId } ?: return project
        val movingClip = sourceTrack.clips.first { it.id == clipId }

        val audioAllowed = targetTrack.type == TrackType.AUDIO && movingClip.mediaType == MediaType.AUDIO
        val visualAllowed = targetTrack.type != TrackType.AUDIO && movingClip.mediaType != MediaType.AUDIO
        if (!audioAllowed && !visualAllowed) return project

        val updatedTracks = project.tracks.map { track ->
            when (track.id) {
                sourceTrack.id -> {
                    val remaining = track.clips.filterNot { it.id == clipId }
                    val norm = if (track.type == TrackType.VIDEO_PRIMARY) {
                        TimelineMath.recalculateSequentialStarts(remaining)
                    } else {
                        remaining
                    }
                    track.copy(clips = norm)
                }
                targetTrack.id -> {
                    val moved = movingClip.copy(trackId = targetTrack.id)
                    val combined = track.clips + moved
                    val norm = if (track.type == TrackType.VIDEO_PRIMARY) {
                        TimelineMath.recalculateSequentialStarts(combined)
                    } else {
                        combined
                    }
                    track.copy(clips = norm)
                }
                else -> track
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateClipBlendCommand(
    private val clipId: String,
    private val blendMode: BlendModeType,
    private val opacity: Float,
) : EditCommand {
    override val description: String = "Update blending (${blendMode.label})"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val clampedOpacity = opacity.coerceIn(0f, 1f)
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(
                            blendMode = blendMode,
                            transform = clip.transform.copy(opacity = clampedOpacity),
                        )
                    } else clip
                }
            )
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateClipSpeedCommand(
    private val clipId: String,
    private val speedMultiplier: Float,
) : EditCommand {
    override val description: String = "Speed ${String.format("%.2fx", speedMultiplier)}"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val clampedSpeed = speedMultiplier.coerceIn(0.25f, 4.0f)
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) {
                track
            } else {
                val mapped = track.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else {
                        val sourceSpan = (clip.sourceEndUs - clip.sourceStartUs)
                            .coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_US)
                        val newDur = (sourceSpan / clampedSpeed).toLong()
                            .coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_US)
                        clip.copy(speedMultiplier = clampedSpeed, timelineDurationUs = newDur)
                    }
                }
                val normalized = if (track.type == TrackType.VIDEO_PRIMARY) {
                    TimelineMath.recalculateSequentialStarts(mapped)
                } else {
                    mapped
                }
                track.copy(clips = normalized)
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateClipAudioEnvelopeCommand(
    private val clipId: String,
    private val volume: Float,
    private val isMuted: Boolean,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    override val description: String = "Update audio envelope",
) : EditCommand {
    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val clampedVol = volume.coerceIn(0f, 1.5f)
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else {
                        val maxFade = (clip.timelineDurationUs / 2L).coerceAtLeast(0L)
                        clip.copy(
                            volume = clampedVol,
                            isMuted = isMuted,
                            fadeInUs = fadeInUs.coerceIn(0L, maxFade),
                            fadeOutUs = fadeOutUs.coerceIn(0L, maxFade),
                        )
                    }
                }
            )
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateClipEffectStackCommand(
    private val clipId: String,
    private val effectStack: List<ClipEffectInstance>,
    private val colorGrade: ColorGradeSpec? = null,
    override val description: String = "Update effects stack",
) : EditCommand {
    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else clip.copy(
                        effectStack = effectStack,
                        colorGrade = colorGrade ?: clip.colorGrade,
                    )
                }
            )
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpsertKeyframeCommand(
    private val clipId: String,
    private val property: AnimatableProperty,
    private val keyframe: Keyframe,
    private val toleranceUs: Long = 45_000L,
) : EditCommand {
    override val description: String = "Keyframe ${property.label}"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else {
                        val clampedOffset = keyframe.timeOffsetUs.coerceIn(0L, clip.timelineDurationUs)
                        val existing = clip.keyframes[property].orEmpty()
                        val filtered = existing.filterNot {
                            it.id == keyframe.id || abs(it.timeOffsetUs - clampedOffset) <= toleranceUs
                        }
                        val nextList = (filtered + keyframe.copy(timeOffsetUs = clampedOffset))
                            .sortedBy { it.timeOffsetUs }
                        clip.copy(keyframes = clip.keyframes + (property to nextList))
                    }
                }
            )
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class RemoveKeyframeCommand(
    private val clipId: String,
    private val property: AnimatableProperty,
    private val keyframeId: String,
) : EditCommand {
    override val description: String = "Remove keyframe"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else {
                        val remaining = clip.keyframes[property].orEmpty().filterNot { it.id == keyframeId }
                        val nextMap = if (remaining.isEmpty()) {
                            clip.keyframes - property
                        } else {
                            clip.keyframes + (property to remaining)
                        }
                        clip.copy(keyframes = nextMap)
                    }
                }
            )
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class UpdateKeyframeEasingCommand(
    private val clipId: String,
    private val property: AnimatableProperty,
    private val keyframeId: String,
    private val easing: EasingType,
) : EditCommand {
    override val description: String = "Easing ${easing.name}"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val updatedTracks = project.tracks.map { track ->
            if (track.clips.none { it.id == clipId }) track
            else track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id != clipId) clip
                    else {
                        val updatedList = clip.keyframes[property].orEmpty().map { kf ->
                            if (kf.id == keyframeId) kf.copy(easing = easing) else kf
                        }
                        clip.copy(keyframes = clip.keyframes + (property to updatedList))
                    }
                }
            )
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

class ApplyPresetCommand(
    private val preset: PresetDefinition,
    private val targetClipId: String? = null,
    private val intensity: Float = 1f,
) : EditCommand {
    override val description: String = "Apply ${preset.title}"

    override fun apply(project: TimelineProject, timestampMs: Long): TimelineProject {
        val scale = intensity.coerceIn(0f, 1f)
        val scaledGrade = EffectGradeMath.scalePresetGrade(preset.colorGrade, scale)
        val scaledStack = preset.effectStack.map { it.copy(intensity = (it.intensity * scale).coerceIn(0f, 1f)) }
        val updatedTracks = project.tracks.map { track ->
            if (track.type != TrackType.VIDEO_PRIMARY && track.type != TrackType.OVERLAY && track.type != TrackType.ADJUSTMENT) {
                track
            } else {
                val updatedClips = track.clips.map { clip ->
                    val shouldApply = targetClipId == null || clip.id == targetClipId
                    if (!shouldApply) {
                        clip
                    } else {
                        when (preset.category) {
                            PresetCategory.CC, PresetCategory.FILTERS -> {
                                clip.copy(
                                    colorGrade = scaledGrade,
                                    blendMode = preset.blendMode ?: clip.blendMode,
                                )
                            }
                            PresetCategory.SHAKES -> {
                                val amp = (preset.shakeAmplitude * scale).coerceAtLeast(2f)
                                val freq = preset.shakeFrequencyHz.coerceAtLeast(6f)
                                val durationSec = (clip.timelineDurationUs / 1_000_000.0).coerceAtLeast(0.2)
                                val steps = (durationSec * freq * 2.0).toInt().coerceIn(4, 24)
                                val posXKeyframes = (0..steps).map { idx ->
                                    val tUs = (clip.timelineDurationUs * idx) / steps
                                    val valPx = (sin(idx * Math.PI / 2.0) * amp).toFloat()
                                    Keyframe("shake_x_${clip.id}_$idx", tUs, valPx, EasingType.EASE_IN_OUT)
                                }
                                val posYKeyframes = (0..steps).map { idx ->
                                    val tUs = (clip.timelineDurationUs * idx) / steps
                                    val valPx = (sin(idx * Math.PI / 2.0 + 1.1) * amp * 0.65).toFloat()
                                    Keyframe("shake_y_${clip.id}_$idx", tUs, valPx, EasingType.EASE_IN_OUT)
                                }
                                clip.copy(
                                    keyframes = clip.keyframes +
                                        (AnimatableProperty.POSITION_X to posXKeyframes) +
                                        (AnimatableProperty.POSITION_Y to posYKeyframes)
                                )
                            }
                            PresetCategory.MOTION -> {
                                val mappedKfs = if (preset.motionKeyframes.isNotEmpty()) {
                                    preset.motionKeyframes.mapValues { (prop, kfs) ->
                                        val maxOrigT = (kfs.maxOfOrNull { it.timeOffsetUs } ?: 1L).coerceAtLeast(1L)
                                        kfs.mapIndexed { idx, kf ->
                                            val mappedT = ((kf.timeOffsetUs.toDouble() / maxOrigT.toDouble()) * clip.timelineDurationUs)
                                                .toLong()
                                                .coerceIn(0L, clip.timelineDurationUs)
                                            val baseVal = if (prop == AnimatableProperty.SCALE_X || prop == AnimatableProperty.SCALE_Y) 1f else 0f
                                            val scaledVal = baseVal + (kf.value - baseVal) * scale
                                            kf.copy(id = "mot_${prop.name}_${clip.id}_$idx", timeOffsetUs = mappedT, value = scaledVal)
                                        }
                                    }
                                } else {
                                    val scaleKfs = listOf(
                                        Keyframe("mot_sx_0_${clip.id}", 0L, 1f, EasingType.EASE_OUT),
                                        Keyframe("mot_sx_1_${clip.id}", clip.timelineDurationUs, 1f + 0.14f * scale, EasingType.EASE_IN_OUT),
                                    )
                                    mapOf(
                                        AnimatableProperty.SCALE_X to scaleKfs,
                                        AnimatableProperty.SCALE_Y to scaleKfs.map { it.copy(id = it.id.replace("sx", "sy")) },
                                    )
                                }
                                clip.copy(keyframes = clip.keyframes + mappedKfs)
                            }
                            PresetCategory.TRANSITIONS -> {
                                val durUs = (preset.transitionDurationMs.coerceIn(120, 1200) * 1000L)
                                    .coerceAtMost((clip.timelineDurationUs / 2L).coerceAtLeast(100_000L))
                                val opacityKfs = listOf(
                                    Keyframe("trans_op_0_${clip.id}", 0L, 1f - scale, EasingType.EASE_OUT),
                                    Keyframe("trans_op_1_${clip.id}", durUs, 1f, EasingType.LINEAR),
                                )
                                val scaleKfs = listOf(
                                    Keyframe("trans_sc_0_${clip.id}", 0L, 1f + 0.08f * scale, EasingType.EASE_OUT),
                                    Keyframe("trans_sc_1_${clip.id}", durUs, 1.0f, EasingType.EASE_IN_OUT),
                                )
                                clip.copy(
                                    colorGrade = if (scaledGrade.isNeutral()) clip.colorGrade else scaledGrade,
                                    keyframes = clip.keyframes +
                                        (AnimatableProperty.OPACITY to opacityKfs) +
                                        (AnimatableProperty.SCALE_X to scaleKfs) +
                                        (AnimatableProperty.SCALE_Y to scaleKfs)
                                )
                            }
                            PresetCategory.ONE_FRAME -> {
                                val frameUs = (1_000_000L / project.frameRate.fps.coerceAtLeast(24))
                                val peakScale = 1.04f + (preset.effectIntensity.coerceIn(0f, 1f) * 0.22f * scale)
                                val scaleKfs = listOf(
                                    Keyframe("1f_sc_0_${clip.id}", 0L, peakScale, EasingType.HOLD),
                                    Keyframe("1f_sc_1_${clip.id}", frameUs, 1.0f, EasingType.EASE_OUT),
                                )
                                clip.copy(
                                    colorGrade = scaledGrade,
                                    keyframes = clip.keyframes +
                                        (AnimatableProperty.SCALE_X to scaleKfs) +
                                        (AnimatableProperty.SCALE_Y to scaleKfs)
                                )
                            }
                            PresetCategory.TEXT_ANIMATIONS -> {
                                val enterUs = 320_000L.coerceAtMost(clip.timelineDurationUs)
                                val posYKfs = listOf(
                                    Keyframe("txt_y_0_${clip.id}", 0L, 28f * scale, EasingType.EASE_OUT),
                                    Keyframe("txt_y_1_${clip.id}", enterUs, 0f, EasingType.EASE_OUT),
                                )
                                val opacityKfs = listOf(
                                    Keyframe("txt_op_0_${clip.id}", 0L, 0f, EasingType.LINEAR),
                                    Keyframe("txt_op_1_${clip.id}", enterUs, 1f, EasingType.EASE_OUT),
                                )
                                clip.copy(
                                    keyframes = clip.keyframes +
                                        (AnimatableProperty.POSITION_Y to posYKfs) +
                                        (AnimatableProperty.OPACITY to opacityKfs)
                                )
                            }
                            else -> {
                                clip.copy(
                                    colorGrade = scaledGrade,
                                    effectStack = if (scaledStack.isNotEmpty()) scaledStack else clip.effectStack,
                                    blendMode = preset.blendMode ?: clip.blendMode,
                                    transform = clip.transform.copy(
                                        scaleX = if (preset.effectIntensity > 0f) 1f + preset.effectIntensity * 0.04f * scale else clip.transform.scaleX,
                                        scaleY = if (preset.effectIntensity > 0f) 1f + preset.effectIntensity * 0.04f * scale else clip.transform.scaleY,
                                    ),
                                )
                            }
                        }
                    }
                }
                track.copy(clips = updatedClips)
            }
        }
        return project.copy(tracks = updatedTracks, updatedAtMs = timestampMs)
    }
}

data class HistorySnapshot(
    val project: TimelineProject,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val lastActionLabel: String?,
)

class CommandHistoryManager(
    initialProject: TimelineProject,
    private val maxHistorySize: Int = 50,
) {
    private val undoStack = ArrayDeque<Pair<TimelineProject, String>>()
    private val redoStack = ArrayDeque<Pair<TimelineProject, String>>()
    private var currentProject: TimelineProject = initialProject
    private var lastAction: String? = null

    fun snapshot(): HistorySnapshot = HistorySnapshot(
        project = currentProject,
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty(),
        lastActionLabel = lastAction,
    )

    fun execute(command: EditCommand, timestampMs: Long): HistorySnapshot {
        val next = command.apply(currentProject, timestampMs)
        if (next != currentProject) {
            undoStack.addLast(currentProject to command.description)
            if (undoStack.size > maxHistorySize) {
                undoStack.removeFirst()
            }
            redoStack.clear()
            currentProject = next
            lastAction = command.description
        }
        return snapshot()
    }

    fun undo(): HistorySnapshot {
        if (undoStack.isEmpty()) return snapshot()
        val (previous, label) = undoStack.removeLast()
        redoStack.addLast(currentProject to label)
        currentProject = previous
        lastAction = "Undo: $label"
        return snapshot()
    }

    fun redo(): HistorySnapshot {
        if (redoStack.isEmpty()) return snapshot()
        val (next, label) = redoStack.removeLast()
        undoStack.addLast(currentProject to label)
        currentProject = next
        lastAction = "Redo: $label"
        return snapshot()
    }

    fun reset(project: TimelineProject): HistorySnapshot {
        undoStack.clear()
        redoStack.clear()
        currentProject = project
        lastAction = null
        return snapshot()
    }
}
