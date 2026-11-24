package com.example.core.model

enum class AspectRatioPreset(
    val id: String,
    val label: String,
    val shortLabel: String,
    val widthRatio: Float,
    val heightRatio: Float,
    val exportWidth1080: Int,
    val exportHeight1080: Int,
) {
    RATIO_16_9("16:9", "Widescreen Cinema", "16:9", 16f, 9f, 1920, 1080),
    RATIO_9_16("9:16", "Vertical Reels", "9:16", 9f, 16f, 1080, 1920),
    RATIO_1_1("1:1", "Square Feed", "1:1", 1f, 1f, 1080, 1080),
    RATIO_4_5("4:5", "Portrait Social", "4:5", 4f, 5f, 1080, 1350),
    RATIO_4_3("4:3", "Classic Frame", "4:3", 4f, 3f, 1440, 1080),
    RATIO_3_4("3:4", "Portrait Photo", "3:4", 3f, 4f, 1080, 1440),
    RATIO_21_9("21:9", "Anamorphic Wide", "21:9", 21f, 9f, 2520, 1080);

    val aspectFloat: Float get() = widthRatio / heightRatio

    companion object {
        fun fromId(id: String): AspectRatioPreset =
            entries.firstOrNull { it.id == id } ?: RATIO_9_16
    }
}

enum class ExportResolutionPreset(
    val id: String,
    val shortLabel: String,
    val label: String,
    val shortEdgePx: Int,
) {
    HD_720P("720p", "720p", "720p HD", 720),
    FHD_1080P("1080p", "1080p", "1080p FHD", 1080),
    QHD_2K("2K", "2K", "2K QHD", 1440),
    UHD_4K("4K", "4K", "4K UHD", 2160);

    fun dimensionsFor(aspect: AspectRatioPreset): Pair<Int, Int> {
        val ratio = aspect.aspectFloat
        return if (ratio >= 1f) {
            val h = shortEdgePx
            val w = (((h * ratio) / 2f).toInt().coerceAtLeast(1)) * 2
            w to h
        } else {
            val w = shortEdgePx
            val h = (((w / ratio) / 2f).toInt().coerceAtLeast(1)) * 2
            w to h
        }
    }

    companion object {
        fun fromId(id: String): ExportResolutionPreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FHD_1080P
    }
}

enum class FrameRatePreset(val fps: Int, val label: String) {
    FPS_24(24, "24 fps"),
    FPS_30(30, "30 fps"),
    FPS_60(60, "60 fps");

    val frameDurationUs: Long get() = 1_000_000L / fps

    companion object {
        fun fromFps(fps: Int): FrameRatePreset =
            entries.firstOrNull { it.fps == fps } ?: FPS_30
    }
}

enum class MediaType {
    VIDEO,
    IMAGE,
    AUDIO,
}

data class MediaAsset(
    val id: String,
    val sourceUri: String,
    val displayName: String,
    val mediaType: MediaType,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val rotationDeg: Int = 0,
    val fps: Float = 30f,
    val hasAudio: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val thumbnailPath: String? = null,
    val mimeType: String = "",
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
    val audioCodec: String = "",
    val videoCodec: String = "",
    val filmstripPaths: List<String> = emptyList(),
    val waveformSamples: List<Float> = emptyList(),
    val cachedLocalPath: String? = null,
    val uriPersisted: Boolean = false,
    val isAccessible: Boolean = true,
) {
    fun playbackUriOrPath(): String = cachedLocalPath?.takeIf { java.io.File(it).exists() } ?: sourceUri
}

enum class TrackType {
    VIDEO_PRIMARY,
    OVERLAY,
    TEXT,
    ADJUSTMENT,
    AUDIO,
}

enum class EasingType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    HOLD,
}

enum class AnimatableProperty(val label: String) {
    POSITION_X("Pos X"),
    POSITION_Y("Pos Y"),
    POSITION_Z("Pos Z"),
    SCALE_X("Scale X"),
    SCALE_Y("Scale Y"),
    ROTATION_DEG("Rotation"),
    OPACITY("Opacity"),
    SKEW_X("Skew X"),
    SKEW_Y("Skew Y"),
    ANCHOR_X("Anchor X"),
    ANCHOR_Y("Anchor Y"),
    VOLUME("Volume"),
}

data class Keyframe(
    val id: String,
    val timeOffsetUs: Long,
    val value: Float,
    val easing: EasingType = EasingType.LINEAR,
)

data class TransformSpec(
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val positionZ: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    val skewXDeg: Float = 0f,
    val skewYDeg: Float = 0f,
    val opacity: Float = 1f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
)

enum class BlendModeType(val id: String, val label: String, val requiresApi29: Boolean = false) {
    NORMAL("normal", "Normal", false),
    MULTIPLY("multiply", "Multiply", true),
    SCREEN("screen", "Screen", true),
    OVERLAY("overlay", "Overlay", true),
    DARKEN("darken", "Darken", true),
    LIGHTEN("lighten", "Lighten", true),
    ADDITIVE("plus", "Add", true);

    companion object {
        fun fromId(id: String): BlendModeType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: NORMAL
    }
}

enum class VisualEffectType(
    val label: String,
    val category: String,
    val defaultIntensity: Float,
) {
    GAUSSIAN_BLUR("Gaussian Blur", "Blur", 0.35f),
    GLOW("Glow / Bloom", "Light", 0.40f),
    VIGNETTE("Vignette", "Lens", 0.45f),
    GRAYSCALE("Monochrome", "Color", 0.75f),
    SEPIA("Sepia Film", "Color", 0.50f),
    SHARPEN("Sharpen", "Detail", 0.35f),
}

data class ClipEffectInstance(
    val id: String,
    val type: VisualEffectType,
    val enabled: Boolean = true,
    val intensity: Float = type.defaultIntensity,
)

enum class CanvasScaleMode(val id: String, val label: String) {
    FIT("fit", "Fit"),
    FILL("fill", "Fill");

    companion object {
        fun fromId(id: String): CanvasScaleMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FIT
    }
}

enum class CanvasBackgroundSpec(
    val id: String,
    val label: String,
    val colorArgb: Long,
    val secondaryColorArgb: Long,
) {
    PURE_BLACK("black", "Studio Black", 0xFF000000L, 0xFF09090FL),
    CHARCOAL("charcoal", "Charcoal", 0xFF14151DL, 0xFF1E202CL),
    MIDNIGHT_VIOLET("violet", "Midnight Violet", 0xFF181129L, 0xFF2B184AL),
    SLATE_BLUE("slate", "Slate Blue", 0xFF0F1826L, 0xFF1C2E47L),
    WARM_GRAIN("warm", "Warm Obsidian", 0xFF1F1714L, 0xFF33231CL);

    companion object {
        fun fromId(id: String): CanvasBackgroundSpec =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PURE_BLACK
    }
}

data class ColorGradeSpec(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val sharpness: Float = 0f,
    val blurRadius: Float = 0f,
    val glow: Float = 0f,
    val vignette: Float = 0f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
    val filterPresetId: String? = null,
    val filterIntensity: Float = 1f,
    val lutId: String? = null,
) {
    fun isNeutral(): Boolean =
        exposure == 0f &&
            brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            temperature == 0f &&
            tint == 0f &&
            highlights == 0f &&
            shadows == 0f &&
            sharpness == 0f &&
            blurRadius == 0f &&
            glow == 0f &&
            vignette == 0f &&
            grayscale == 0f &&
            sepia == 0f &&
            (filterPresetId == null || filterPresetId == "original")
}

data class ChromaKeySpec(
    val enabled: Boolean = false,
    val keyColorRgb: Int = 0x00FF00,
    val similarity: Float = 0.25f,
    val smoothness: Float = 0.1f,
    val spillSuppression: Float = 0.2f,
)

enum class MaskShape {
    NONE,
    RECTANGLE,
    CIRCLE,
    LINEAR,
}

data class MaskSpec(
    val shape: MaskShape = MaskShape.NONE,
    val feather: Float = 0.05f,
    val invert: Boolean = false,
)

data class TimelineClip(
    val id: String,
    val assetId: String,
    val trackId: String,
    val mediaType: MediaType,
    val label: String,
    val timelineStartUs: Long,
    val timelineDurationUs: Long,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val sourceTotalDurationUs: Long,
    val speedMultiplier: Float = 1f,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
    val blendMode: BlendModeType = BlendModeType.NORMAL,
    val transform: TransformSpec = TransformSpec(),
    val colorGrade: ColorGradeSpec = ColorGradeSpec(),
    val effectStack: List<ClipEffectInstance> = emptyList(),
    val chromaKey: ChromaKeySpec = ChromaKeySpec(),
    val mask: MaskSpec = MaskSpec(),
    val keyframes: Map<AnimatableProperty, List<Keyframe>> = emptyMap(),
    val thumbnailPath: String? = null,
    val groupId: String? = null,
) {
    val timelineEndUs: Long get() = timelineStartUs + timelineDurationUs
}

data class TimelineTrack(
    val id: String,
    val type: TrackType,
    val name: String,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val isHidden: Boolean = false,
    val clips: List<TimelineClip> = emptyList(),
)

data class TimelineProject(
    val id: String,
    val name: String,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_9_16,
    val frameRate: FrameRatePreset = FrameRatePreset.FPS_30,
    val targetResolution: ExportResolutionPreset = ExportResolutionPreset.FHD_1080P,
    val scaleMode: CanvasScaleMode = CanvasScaleMode.FIT,
    val backgroundId: String = CanvasBackgroundSpec.PURE_BLACK.id,
    val customWidthPx: Int? = null,
    val customHeightPx: Int? = null,
    val tracks: List<TimelineTrack> = defaultTracks(),
    val assets: Map<String, MediaAsset> = emptyMap(),
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    fun effectiveDimensions(): Pair<Int, Int> {
        val cw = customWidthPx
        val ch = customHeightPx
        return if (cw != null && ch != null && cw >= 120 && ch >= 120) {
            cw to ch
        } else {
            targetResolution.dimensionsFor(aspectRatio)
        }
    }

    fun effectiveAspectFloat(): Float {
        val cw = customWidthPx
        val ch = customHeightPx
        return if (cw != null && ch != null && cw >= 120 && ch >= 120) {
            cw.toFloat() / ch.toFloat()
        } else {
            aspectRatio.aspectFloat
        }
    }

    fun effectiveBackground(): CanvasBackgroundSpec = CanvasBackgroundSpec.fromId(backgroundId)

    fun primaryVideoTrack(): TimelineTrack =
        tracks.firstOrNull { it.type == TrackType.VIDEO_PRIMARY } ?: tracks.first()

    fun allClips(): List<TimelineClip> = tracks.flatMap { it.clips }

    fun totalDurationUs(): Long =
        tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndUs } ?: 0L

    fun clipCount(): Int = tracks.sumOf { it.clips.size }

    fun primaryThumbnailPath(): String? =
        primaryVideoTrack().clips.firstOrNull { !it.thumbnailPath.isNullOrBlank() }?.thumbnailPath
            ?: assets.values.firstOrNull { !it.thumbnailPath.isNullOrBlank() }?.thumbnailPath

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val PRIMARY_TRACK_ID = "track_video_primary"
        const val OVERLAY_TRACK_ID = "track_overlay_1"
        const val TEXT_TRACK_ID = "track_text_1"
        const val AUDIO_TRACK_ID = "track_audio_1"

        fun defaultTracks(): List<TimelineTrack> = listOf(
            TimelineTrack(id = PRIMARY_TRACK_ID, type = TrackType.VIDEO_PRIMARY, name = "Main Video"),
            TimelineTrack(id = OVERLAY_TRACK_ID, type = TrackType.OVERLAY, name = "Overlay"),
            TimelineTrack(id = TEXT_TRACK_ID, type = TrackType.TEXT, name = "Text & Captions"),
            TimelineTrack(id = AUDIO_TRACK_ID, type = TrackType.AUDIO, name = "Audio"),
        )
    }
}

data class ProjectSummary(
    val id: String,
    val name: String,
    val aspectRatio: AspectRatioPreset,
    val frameRate: FrameRatePreset,
    val durationUs: Long,
    val clipCount: Int,
    val thumbnailPath: String?,
    val updatedAtMs: Long,
)
