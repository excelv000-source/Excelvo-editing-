package com.example.core.ports

import com.example.core.model.AspectRatioPreset
import com.example.core.model.CanvasBackgroundSpec
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.ProjectSummary
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class UserRole(val label: String) {
    GUEST("Guest"),
    USER("Registered User"),
    CREATOR("Creator"),
    MODERATOR("Moderator"),
    ADMIN("Admin"),
}

enum class ModerationStatus(val label: String) {
    PENDING_REVIEW("Pending Review"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    REMOVED("Removed"),
}

data class GpuCapabilities(
    val glEsMajorVersion: Int = 2,
    val supportsGlEs3: Boolean = false,
    val isLowMemoryDevice: Boolean = false,
    val useHardwareGpuPipeline: Boolean = false,
    val statusLabel: String = "Software Fallback",
)

data class PlaybackStatus(
    val playheadUs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val activeVideoClipId: String? = null,
    val isUsingGpuEffects: Boolean = false,
    val playbackError: String? = null,
)

/**
 * Cross-platform playback & GPU preview contract.
 * Android implementation: Media3 ExoPlayer + OpenGL ES 3.0 GlEffect shader program.
 * iOS counterpart: AVQueuePlayer + Metal/CoreImage CIFilter pipeline.
 */
interface SequencePlaybackPort {
    val status: StateFlow<PlaybackStatus>
    val gpuCapabilities: GpuCapabilities

    fun syncSequence(project: TimelineProject, playheadUs: Long)
    fun updateLiveVisualState(clip: TimelineClip?, project: TimelineProject)
    fun play()
    fun pause()
    fun seekToTimelineUs(timelineUs: Long)
    fun stepFrames(deltaFrames: Int)
    fun onHostPause()
    fun onHostResume()
    fun clearError()
    fun release()
}

enum class ImportFailureReason {
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    UNSUPPORTED_FORMAT,
    CORRUPTED_MEDIA,
    CODEC_UNAVAILABLE,
    DECODE_ERROR,
}

class MediaImportException(
    val reason: ImportFailureReason,
    override val message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class MediaImportProgress(
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentLabel: String = "",
)

/**
 * Platform adapter contract for extracting real media metadata, thumbnails, filmstrips, and waveforms.
 * Android implementation: ImageDecoder/BitmapFactory + ExifInterface + MediaMetadataRetriever + MediaExtractor + MediaCodec.
 * iOS counterpart: AVURLAsset + AVAssetImageGenerator + CGImageSource.
 */
interface MediaProbePort {
    suspend fun probeAndCacheThumbnail(
        sourceUri: String,
        projectId: String,
    ): Result<MediaAsset>

    suspend fun checkMediaAccessibility(asset: MediaAsset): Boolean = true
}

/**
 * Platform adapter contract for local project storage.
 * Android implementation: Room index + atomic JSON project documents.
 * iOS counterpart: SwiftData/SQLite index + identical shared JSON project schema.
 */
interface ProjectStoragePort {
    fun observeProjectSummaries(): Flow<List<ProjectSummary>>
    suspend fun createProject(
        name: String,
        aspectRatio: AspectRatioPreset,
        frameRate: FrameRatePreset,
        targetResolution: ExportResolutionPreset = ExportResolutionPreset.FHD_1080P,
        scaleMode: CanvasScaleMode = CanvasScaleMode.FIT,
        backgroundId: String = CanvasBackgroundSpec.PURE_BLACK.id,
        customWidthPx: Int? = null,
        customHeightPx: Int? = null,
    ): TimelineProject
    suspend fun loadProject(projectId: String): TimelineProject?
    suspend fun saveProject(project: TimelineProject)
    suspend fun renameProject(projectId: String, newName: String)
    suspend fun duplicateProject(projectId: String): TimelineProject?
    suspend fun deleteProject(projectId: String)
}

/**
 * Cross-platform visual design tokens mirrored between Jetpack Compose (Android) and SwiftUI (iOS).
 * Original EXCEVO EDIT Purple Dark Studio System.
 */
object CrossPlatformDesignTokens {
    const val COLOR_VOID_CHARCOAL = 0xFF0B0B10L
    const val COLOR_DEEP_OBSIDIAN = 0xFF050508L
    const val COLOR_STUDIO_PANEL = 0xFF14141DL
    const val COLOR_ELEVATED_SURFACE = 0xFF1D1E2AL
    const val COLOR_TRACK_SURFACE = 0xFF181823L
    const val COLOR_HAIRLINE_BORDER = 0xFF2A2C3DL
    const val COLOR_ACCENT_PURPLE = 0xFF8B5CF6L
    const val COLOR_ACCENT_VIOLET_GLOW = 0xFFA855F7L
    const val COLOR_ACCENT_LAVENDER = 0xFFC084FCL
    const val COLOR_ACCENT_PURPLE_CONTAINER = 0xFF2B1F4DL
    const val COLOR_PLAYHEAD_AMBER = 0xFFF59E0BL
    const val COLOR_PLAYHEAD_AMBER_CONTAINER = 0xFF3A2909L
    const val COLOR_DANGER_RED = 0xFFEF4444L
    const val COLOR_SUCCESS_EMERALD = 0xFF10B981L
    const val COLOR_TEXT_PRIMARY = 0xFFF4F3F8L
    const val COLOR_TEXT_SECONDARY = 0xFFA19EB3L
    const val COLOR_TEXT_MUTED = 0xFF6E6B80L

    // Floating Glassmorphic Dock Tokens (shared with iOS)
    const val DOCK_CAPSULE_RADIUS_DP = 28
    const val DOCK_HEIGHT_DP = 64
    const val DOCK_CENTER_BUTTON_SIZE_DP = 56
    const val DOCK_CENTER_ELEVATION_OFFSET_DP = 10
    const val DOCK_BOTTOM_CLEARANCE_DP = 108
    const val DOCK_HIDE_DURATION_MS = 220
    const val DOCK_SHOW_DURATION_MS = 260
}
