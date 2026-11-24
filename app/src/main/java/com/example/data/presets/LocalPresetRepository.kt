package com.example.data.presets

import com.example.core.command.ApplyPresetCommand
import com.example.core.engine.EffectGradeMath
import com.example.core.engine.PresetSchemaValidator
import com.example.core.engine.PresetValidationResult
import com.example.core.model.AnimatableProperty
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.Keyframe
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.TimelineClip
import com.example.core.ports.ProjectStoragePort
import com.example.data.db.SavedPresetDao
import com.example.data.db.SavedPresetEntity
import com.example.data.storage.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.UUID

class LocalPresetRepository(
    private val savedPresetDao: SavedPresetDao,
    private val projectStorage: ProjectStoragePort,
    private val storageManager: StorageManager? = null,
) {
    private val builtInPresets: List<PresetDefinition> = listOf(
        // SHAKES
        PresetDefinition(
            id = "builtin_shake_impact",
            title = "Impact Punch Shake",
            description = "High-energy 14 Hz decaying beat-hit camera displacement with subtle ease curves.",
            category = PresetCategory.SHAKES,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            shakeAmplitude = 28f,
            shakeFrequencyHz = 14f,
            tags = listOf("Beat", "Impact", "Motion"),
        ),
        PresetDefinition(
            id = "builtin_shake_handheld",
            title = "Handheld Micro Drift",
            description = "Organic documentary camera float at 3.5 Hz for natural kinetic realism.",
            category = PresetCategory.SHAKES,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            shakeAmplitude = 8f,
            shakeFrequencyHz = 3.5f,
            tags = listOf("Organic", "Documentary", "Subtle"),
        ),
        PresetDefinition(
            id = "builtin_shake_velocity",
            title = "Velocity Snap Shake",
            description = "Sharp horizontal whip impulse tuned for 60fps montage transitions.",
            category = PresetCategory.SHAKES,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            shakeAmplitude = 42f,
            shakeFrequencyHz = 18f,
            tags = listOf("AMV", "60fps", "Fast"),
        ),
        // TRANSITIONS
        PresetDefinition(
            id = "builtin_trans_whip_pan",
            title = "Whip Pan Slide",
            description = "Fast directional momentum push with subtle ease-out scale settle across cuts.",
            category = PresetCategory.TRANSITIONS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            transitionStyle = "WhipSlide",
            transitionDurationMs = 320,
            tags = listOf("Whip", "Slide", "Fast"),
        ),
        PresetDefinition(
            id = "builtin_trans_luma_dissolve",
            title = "Violet Luma Dissolve",
            description = "Smooth luminance cross-blend accented with subtle lavender shadow roll-off.",
            category = PresetCategory.TRANSITIONS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            transitionStyle = "LumaDissolve",
            transitionDurationMs = 480,
            colorGrade = ColorGradeSpec(tint = 0.14f),
            tags = listOf("Luma", "Smooth", "Dissolve"),
        ),
        PresetDefinition(
            id = "builtin_trans_prism_cut",
            title = "Prism Split Cut",
            description = "Crisp optical split-frame entrance designed for music-synced montage edits.",
            category = PresetCategory.TRANSITIONS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            transitionStyle = "PrismSplit",
            transitionDurationMs = 260,
            effectIntensity = 0.5f,
            tags = listOf("Prism", "Split", "Sync"),
        ),
        // EFFECTS
        PresetDefinition(
            id = "builtin_fx_violet_pulse",
            title = "Violet Prism Pulse",
            description = "Subtle zoom pop paired with cool violet shadow lift for futuristic edits.",
            category = PresetCategory.EFFECTS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            effectName = "PrismZoomPop",
            effectIntensity = 0.65f,
            colorGrade = ColorGradeSpec(contrast = 0.14f, tint = 0.18f, vignette = 0.25f),
            tags = listOf("Prism", "Zoom", "Pulse"),
        ),
        PresetDefinition(
            id = "builtin_fx_halation",
            title = "Anamorphic Bloom",
            description = "Soft highlight bloom and deep cinema vignette calibrated for night footage.",
            category = PresetCategory.EFFECTS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            effectName = "AnamorphicBloom",
            effectIntensity = 0.55f,
            colorGrade = ColorGradeSpec(highlights = 0.22f, shadows = -0.15f, vignette = 0.35f),
            tags = listOf("Cinema", "Night", "Glow"),
        ),
        PresetDefinition(
            id = "builtin_fx_chromatic",
            title = "Chromatic Edge Shift",
            description = "Micro-scale edge energy and contrast punch for dynamic action clips.",
            category = PresetCategory.EFFECTS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            effectName = "ChromaticEdge",
            effectIntensity = 0.45f,
            colorGrade = ColorGradeSpec(contrast = 0.2f, saturation = 0.12f),
            tags = listOf("Chromatic", "Glitch", "Action"),
        ),
        // 1 FRAMES
        PresetDefinition(
            id = "builtin_1f_strobe_flash",
            title = "1F Strobe Impact",
            description = "Single-frame highlight strobe impulse on clip entry to accentuate heavy downbeats.",
            category = PresetCategory.ONE_FRAME,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            oneFrameStyle = "StrobeFlash",
            effectIntensity = 0.72f,
            colorGrade = ColorGradeSpec(exposure = 0.28f, contrast = 0.18f),
            tags = listOf("1F", "Flash", "Beat"),
        ),
        PresetDefinition(
            id = "builtin_1f_zoom_kick",
            title = "1F Scale Kick",
            description = "Instant one-frame micro-zoom punch that snaps cleanly back to native scale.",
            category = PresetCategory.ONE_FRAME,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            oneFrameStyle = "ScaleKick",
            effectIntensity = 0.85f,
            tags = listOf("1F", "Scale", "Punch"),
        ),
        PresetDefinition(
            id = "builtin_1f_violet_snap",
            title = "1F Violet Snap",
            description = "Crisp single-frame violet contrast accent ideal for rhythmic transition cuts.",
            category = PresetCategory.ONE_FRAME,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            oneFrameStyle = "VioletSnap",
            effectIntensity = 0.60f,
            colorGrade = ColorGradeSpec(contrast = 0.25f, tint = 0.30f),
            tags = listOf("1F", "Violet", "Rhythm"),
        ),
        // COLOR CORRECTIONS (CC)
        PresetDefinition(
            id = "builtin_cc_violet_noir",
            title = "Violet Noir Cinema",
            description = "Crushed obsidian blacks, cool midtone tint, and crisp highlight roll-off.",
            category = PresetCategory.CC,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            colorGrade = ColorGradeSpec(
                exposure = -0.05f,
                contrast = 0.28f,
                saturation = -0.15f,
                temperature = -0.18f,
                tint = 0.24f,
                shadows = -0.22f,
                vignette = 0.3f,
            ),
            tags = listOf("Dark", "Moody", "Violet"),
        ),
        PresetDefinition(
            id = "builtin_cc_teal_amber",
            title = "Teal & Amber Film",
            description = "Classic cinema warm highlights balanced against deep cool shadows.",
            category = PresetCategory.CC,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            colorGrade = ColorGradeSpec(
                contrast = 0.22f,
                saturation = 0.16f,
                temperature = 0.20f,
                tint = -0.12f,
                highlights = 0.15f,
                shadows = -0.18f,
            ),
            tags = listOf("Film", "Warm", "Cinema"),
        ),
        PresetDefinition(
            id = "builtin_cc_editorial",
            title = "Crisp Editorial Clean",
            description = "Balanced neutral daylight contrast and clean skin-tone saturation.",
            category = PresetCategory.CC,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            colorGrade = ColorGradeSpec(
                exposure = 0.10f,
                contrast = 0.16f,
                saturation = 0.08f,
                highlights = -0.08f,
                shadows = 0.12f,
            ),
            tags = listOf("Clean", "Vlog", "Rec709"),
        ),
        // TEXT ANIMATIONS
        PresetDefinition(
            id = "builtin_text_kinetic_rise",
            title = "Kinetic Rise Title",
            description = "Precision upward typographic reveal with damped settle for lower-thirds and openers.",
            category = PresetCategory.TEXT_ANIMATIONS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            textAnimationStyle = "KineticRise",
            tags = listOf("Kinetic", "Title", "Reveal"),
        ),
        PresetDefinition(
            id = "builtin_text_type_pulse",
            title = "Studio Tracking Pop",
            description = "Clean letter-tracking expand animation tuned for crisp title cards and captions.",
            category = PresetCategory.TEXT_ANIMATIONS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            textAnimationStyle = "TrackingPop",
            effectIntensity = 0.4f,
            tags = listOf("Tracking", "Clean", "Caption"),
        ),
        PresetDefinition(
            id = "builtin_text_cinema_fade",
            title = "Editorial Slate Fade",
            description = "Smooth opacity glide with subtle contrast framing for documentary title slates.",
            category = PresetCategory.TEXT_ANIMATIONS,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            textAnimationStyle = "SlateFade",
            tags = listOf("Fade", "Editorial", "Slate"),
        ),
        // MOTION (KEYFRAMED TRANSFORM PRESETS)
        PresetDefinition(
            id = "builtin_motion_dolly_push",
            title = "Smooth Dolly Push",
            description = "Gradual ease-in-out scale and Z-depth drift for cinematic talking-head emphasis.",
            category = PresetCategory.MOTION,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            motionKeyframes = mapOf(
                AnimatableProperty.SCALE_X to listOf(
                    Keyframe("m_dp_sx0", 0L, 1.0f, EasingType.EASE_IN_OUT),
                    Keyframe("m_dp_sx1", 1_000_000L, 1.18f, EasingType.EASE_OUT),
                ),
                AnimatableProperty.SCALE_Y to listOf(
                    Keyframe("m_dp_sy0", 0L, 1.0f, EasingType.EASE_IN_OUT),
                    Keyframe("m_dp_sy1", 1_000_000L, 1.18f, EasingType.EASE_OUT),
                ),
                AnimatableProperty.POSITION_Z to listOf(
                    Keyframe("m_dp_pz0", 0L, 0f, EasingType.EASE_OUT),
                    Keyframe("m_dp_pz1", 1_000_000L, 65f, EasingType.EASE_OUT),
                ),
            ),
            tags = listOf("Motion", "Zoom", "Dolly"),
        ),
        PresetDefinition(
            id = "builtin_motion_slide_settle",
            title = "Pan & Tilt Settle",
            description = "Dynamic horizontal pan with damped rotation settle across the clip span.",
            category = PresetCategory.MOTION,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            motionKeyframes = mapOf(
                AnimatableProperty.POSITION_X to listOf(
                    Keyframe("m_ps_x0", 0L, -90f, EasingType.EASE_OUT),
                    Keyframe("m_ps_x1", 1_000_000L, 0f, EasingType.EASE_OUT),
                ),
                AnimatableProperty.ROTATION_DEG to listOf(
                    Keyframe("m_ps_r0", 0L, -4.5f, EasingType.EASE_OUT),
                    Keyframe("m_ps_r1", 1_000_000L, 0f, EasingType.EASE_OUT),
                ),
            ),
            tags = listOf("Pan", "EaseOut", "Keyframe"),
        ),
        PresetDefinition(
            id = "builtin_motion_float_drift",
            title = "2.5D Float Drift",
            description = "Gentle vertical float paired with subtle 2.5D Z depth breathing.",
            category = PresetCategory.MOTION,
            creatorName = "EXCEVO Studio",
            creatorHandle = "@excevo_official",
            motionKeyframes = mapOf(
                AnimatableProperty.POSITION_Y to listOf(
                    Keyframe("m_fd_y0", 0L, 0f, EasingType.EASE_IN_OUT),
                    Keyframe("m_fd_y1", 500_000L, -24f, EasingType.EASE_IN_OUT),
                    Keyframe("m_fd_y2", 1_000_000L, 0f, EasingType.EASE_IN_OUT),
                ),
                AnimatableProperty.POSITION_Z to listOf(
                    Keyframe("m_fd_z0", 0L, 0f, EasingType.EASE_IN_OUT),
                    Keyframe("m_fd_z1", 500_000L, 48f, EasingType.EASE_IN_OUT),
                    Keyframe("m_fd_z2", 1_000_000L, 0f, EasingType.EASE_IN_OUT),
                ),
            ),
            tags = listOf("2.5D", "Float", "Loop"),
        ),
    )

    private val customLocalPresets = MutableStateFlow<List<PresetDefinition>>(emptyList())

    val allPresets: Flow<List<PresetDefinition>> = customLocalPresets.combine(
        savedPresetDao.observeSavedPresetIds()
    ) { custom, _ ->
        builtInPresets + custom
    }

    val savedPresetIds: Flow<Set<String>> = savedPresetDao.observeSavedPresetIds().combine(customLocalPresets) { ids, _ ->
        ids.toSet()
    }

    suspend fun toggleSaved(presetId: String): Boolean {
        val currentlySaved = savedPresetDao.isSaved(presetId)
        if (currentlySaved) {
            savedPresetDao.unsave(presetId)
        } else {
            savedPresetDao.save(SavedPresetEntity(presetId = presetId, savedAtMs = System.currentTimeMillis()))
        }
        return !currentlySaved
    }

    fun validateAndAddLocalPreset(rawJson: String): PresetValidationResult {
        val result = PresetSchemaValidator.validateRawJsonText(rawJson)
        if (result is PresetValidationResult.Valid) {
            customLocalPresets.value = (customLocalPresets.value.filterNot { it.id == result.preset.id }) + result.preset
        }
        return result
    }

    suspend fun saveCustomPresetFromClip(
        title: String,
        category: PresetCategory,
        clip: TimelineClip,
    ): Result<PresetDefinition> = runCatching {
        val cleanTitle = title.trim().ifBlank { "Custom ${category.label}" }.take(60)
        val id = "custom_${UUID.randomUUID().toString().take(8)}"
        val uniforms = EffectGradeMath.computeUniforms(clip.colorGrade, clip.effectStack)
        val mergedGrade = clip.colorGrade.copy(
            blurRadius = uniforms.blur,
            glow = uniforms.glow,
            vignette = uniforms.vignette,
            grayscale = uniforms.grayscale,
            sepia = uniforms.sepia,
            sharpness = uniforms.sharpness,
        )
        val preset = PresetDefinition(
            id = id,
            title = cleanTitle,
            description = "Saved from ${clip.label} (${category.label})",
            category = category,
            creatorName = "You (Local Studio)",
            creatorHandle = "@local_studio",
            colorGrade = mergedGrade,
            effectStack = clip.effectStack,
            motionKeyframes = clip.keyframes,
            blendMode = clip.blendMode,
            tags = listOf("Custom", category.label),
            isBuiltIn = false,
        )
        val json = PresetSchemaValidator.toExcevoJson(preset)
        customLocalPresets.value = (customLocalPresets.value.filterNot { it.id == preset.id }) + preset
        storageManager?.saveGeneratedPresetToLibrary(
            presetId = preset.id,
            title = preset.title,
            rawJson = json,
        )?.getOrThrow()
        if (!savedPresetDao.isSaved(preset.id)) {
            savedPresetDao.save(SavedPresetEntity(presetId = preset.id, savedAtMs = System.currentTimeMillis()))
        }
        preset
    }

    suspend fun downloadFreeCommunityPreset(preset: PresetDefinition): Result<String> = runCatching {
        val json = PresetSchemaValidator.toExcevoJson(preset)
        if (storageManager != null) {
            storageManager.saveGeneratedPresetToLibrary(
                presetId = preset.id,
                title = preset.title,
                rawJson = json,
            ).getOrThrow()
        }
        if (!savedPresetDao.isSaved(preset.id)) {
            savedPresetDao.save(SavedPresetEntity(presetId = preset.id, savedAtMs = System.currentTimeMillis()))
        }
        "Downloaded \"${preset.title}\" (100% Free) to EXCEVO Library & Saved"
    }

    suspend fun applyPresetToProject(
        preset: PresetDefinition,
        projectId: String,
        intensity: Float = 1f,
    ): Result<Int> = runCatching {
        val project = projectStorage.loadProject(projectId)
            ?: error("Selected project could not be loaded.")
        val clipCount = project.primaryVideoTrack().clips.size
        if (clipCount == 0) {
            error("Project \"${project.name}\" has no video/image clips yet. Import a clip in Studio first.")
        }
        val updated = ApplyPresetCommand(
            preset = preset,
            targetClipId = null,
            intensity = intensity,
        ).apply(project, System.currentTimeMillis())
        projectStorage.saveProject(updated)
        clipCount
    }
}
