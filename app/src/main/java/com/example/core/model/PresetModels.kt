package com.example.core.model

enum class PresetCategory(val id: String, val label: String) {
    SHAKES("SHAKES", "Shakes"),
    TRANSITIONS("TRANSITIONS", "Transitions"),
    EFFECTS("EFFECTS", "Effects"),
    ONE_FRAME("ONE_FRAME", "1 Frames"),
    CC("CC", "CCs"),
    TEXT_ANIMATIONS("TEXT_ANIMATIONS", "Text"),
    MOTION("MOTION", "Motion"),
    FILTERS("FILTERS", "Filters"),
    OTHER("OTHER", "Other");

    companion object {
        fun fromId(id: String): PresetCategory = when (id.trim().uppercase()) {
            "1_FRAME", "1_FRAMES", "ONE_FRAME", "ONEFRAME" -> ONE_FRAME
            "TEXT", "TEXT_ANIM", "TEXT_ANIMATIONS" -> TEXT_ANIMATIONS
            "CCS", "CC" -> CC
            "MOTION", "TRANSFORM", "KEYFRAME" -> MOTION
            else -> entries.firstOrNull { it.id == id.trim().uppercase() } ?: OTHER
        }
    }
}

data class PresetDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: PresetCategory,
    val creatorName: String,
    val creatorHandle: String,
    val schemaVersion: Int = 1,
    val shakeAmplitude: Float = 0f,
    val shakeFrequencyHz: Float = 0f,
    val effectName: String? = null,
    val effectIntensity: Float = 0f,
    val transitionStyle: String? = null,
    val transitionDurationMs: Int = 0,
    val oneFrameStyle: String? = null,
    val textAnimationStyle: String? = null,
    val colorGrade: ColorGradeSpec = ColorGradeSpec(),
    val effectStack: List<ClipEffectInstance> = emptyList(),
    val motionKeyframes: Map<AnimatableProperty, List<Keyframe>> = emptyMap(),
    val blendMode: BlendModeType? = null,
    val tags: List<String> = emptyList(),
    val isBuiltIn: Boolean = true,
    val compatibleAspectRatios: List<AspectRatioPreset> = listOf(
        AspectRatioPreset.RATIO_16_9,
        AspectRatioPreset.RATIO_9_16,
        AspectRatioPreset.RATIO_1_1,
        AspectRatioPreset.RATIO_4_5,
        AspectRatioPreset.RATIO_21_9,
    ),
    val minEngineVersion: String = "1.0",
)

enum class LibraryItemType(val label: String) {
    LUT("LUT"),
    OVERLAY("Overlay"),
    FONT("Font"),
    PRESET("Preset"),
}

data class LibraryAssetItem(
    val id: String,
    val itemType: LibraryItemType,
    val name: String,
    val localPath: String,
    val sizeBytes: Long,
    val createdAtMs: Long,
)

data class StorageStats(
    val projectFilesBytes: Long,
    val thumbnailBytes: Long,
    val libraryBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long get() = projectFilesBytes + thumbnailBytes + libraryBytes + cacheBytes
}

enum class ThemeIntensity(val label: String) {
    STUDIO_DARK("Studio Dark"),
    DEEP_OBSIDIAN("Deep Obsidian"),
}

enum class WorkspacePreset(
    val id: String,
    val label: String,
    val shortLabel: String,
    val subtitle: String,
) {
    EXCEVO_STUDIO(
        id = "excevo_studio",
        label = "Excevo Studio",
        shortLabel = "Excevo",
        subtitle = "Default balanced dual-tier dark purple pro workspace",
    ),
    ALIGHT_MOTION_INSPIRED(
        id = "alight_motion",
        label = "Alight Motion Inspired",
        shortLabel = "Motion",
        subtitle = "Keyframe, XYZ touchpad & easing curve priority",
    ),
    CAPCUT_INSPIRED(
        id = "capcut",
        label = "CapCut Inspired",
        shortLabel = "Fast Cut",
        subtitle = "Rapid split/trim, audio & filter dock focus",
    ),
    NOVA_INSPIRED(
        id = "nova",
        label = "Nova Inspired",
        shortLabel = "FX Deck",
        subtitle = "Preset-forward & stacked GPU effects layout",
    );

    companion object {
        fun fromId(id: String?): WorkspacePreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: EXCEVO_STUDIO
    }
}

enum class ProxyQuality(val label: String, val maxHeightPx: Int) {
    PROXY_720P("720p Fast Scrub", 720),
    PROXY_1080P("1080p High Detail", 1080),
}

data class UserPreferences(
    val themeIntensity: ThemeIntensity = ThemeIntensity.STUDIO_DARK,
    val workspacePreset: WorkspacePreset = WorkspacePreset.EXCEVO_STUDIO,
    val proxyQuality: ProxyQuality = ProxyQuality.PROXY_720P,
    val hardwareDecodeEnabled: Boolean = true,
    val timelineSnapEnabled: Boolean = true,
    val scrubCacheLimitMb: Int = 512,
    val aiUsageCount: Int = 0,
)

data class SequenceDiagnosticReport(
    val projectId: String,
    val projectName: String,
    val clipCount: Int,
    val totalDurationUs: Long,
    val averageClipDurationUs: Long,
    val pacingLabel: String,
    val recommendations: List<String>,
)
