package com.example.core.engine

import com.example.core.model.BlendModeType
import com.example.core.model.ColorGradeSpec
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition

sealed class PresetValidationResult {
    data class Valid(val preset: PresetDefinition) : PresetValidationResult()
    data class Invalid(val reason: String) : PresetValidationResult()
}

/**
 * Pure Kotlin declarative preset validator.
 * Prevents Dynamic Code Loading (DCL) and malicious uploads by enforcing strict
 * JSON structure, safe numeric ranges, and rejecting executable file extensions/tokens.
 */
object PresetSchemaValidator {
    const val MAX_PRESET_JSON_BYTES = 512 * 1024 // 512 KB
    private val FORBIDDEN_EXTENSIONS = setOf(
        "dex", "jar", "so", "apk", "sh", "bat", "exe", "js", "mjs", "html", "htm", "svg", "wasm"
    )
    private val FORBIDDEN_PAYLOAD_PATTERNS = listOf(
        "<script", "javascript:", "eval(", "Runtime.getRuntime", "DexClassLoader", "System.load"
    )

    fun validateFileName(fileName: String): String? {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) return "File name cannot be empty."
        val ext = trimmed.substringAfterLast('.', "").lowercase()
        if (ext in FORBIDDEN_EXTENSIONS) {
            return "Blocked file type .$ext — executable or script payloads are prohibited."
        }
        if (ext != "json" && ext != "excevo") {
            return "Unsupported preset extension .$ext. Only .json or .excevo declarative presets are allowed."
        }
        return null
    }

    fun validateRawJsonText(rawJson: String, fallbackId: String = "custom-preset"): PresetValidationResult {
        val bytes = rawJson.encodeToByteArray().size
        if (bytes == 0) return PresetValidationResult.Invalid("Preset payload is empty.")
        if (bytes > MAX_PRESET_JSON_BYTES) {
            return PresetValidationResult.Invalid("Preset exceeds 512 KB limit ($bytes bytes).")
        }
        for (token in FORBIDDEN_PAYLOAD_PATTERNS) {
            if (rawJson.contains(token, ignoreCase = true)) {
                return PresetValidationResult.Invalid("Rejected unsafe payload pattern: $token")
            }
        }
        val format = extractStringField(rawJson, "presetFileFormat")
        if (format != "excevo-preset/v1") {
            return PresetValidationResult.Invalid("Invalid or missing presetFileFormat (expected 'excevo-preset/v1').")
        }
        val title = extractStringField(rawJson, "title")?.trim().orEmpty()
        if (title.length < 2 || title.length > 60) {
            return PresetValidationResult.Invalid("Preset title must be between 2 and 60 characters.")
        }
        val categoryId = extractStringField(rawJson, "category") ?: "OTHER"
        val category = PresetCategory.fromId(categoryId)
        val description = extractStringField(rawJson, "description").orEmpty().take(300)
        val creatorName = extractStringField(rawJson, "creatorName")?.takeIf { it.isNotBlank() } ?: "Community Creator"

        val exposure = extractFloatField(rawJson, "exposure", 0f).coerceIn(-2f, 2f)
        val brightness = extractFloatField(rawJson, "brightness", 0f).coerceIn(-1f, 1f)
        val contrast = extractFloatField(rawJson, "contrast", 0f).coerceIn(-1f, 1f)
        val saturation = extractFloatField(rawJson, "saturation", 0f).coerceIn(-1f, 1f)
        val temperature = extractFloatField(rawJson, "temperature", 0f).coerceIn(-1f, 1f)
        val tint = extractFloatField(rawJson, "tint", 0f).coerceIn(-1f, 1f)
        val highlights = extractFloatField(rawJson, "highlights", 0f).coerceIn(-1f, 1f)
        val shadows = extractFloatField(rawJson, "shadows", 0f).coerceIn(-1f, 1f)
        val sharpness = extractFloatField(rawJson, "sharpness", 0f).coerceIn(0f, 1f)
        val blurRadius = extractFloatField(rawJson, "blurRadius", 0f).coerceIn(0f, 1f)
        val glow = extractFloatField(rawJson, "glow", 0f).coerceIn(0f, 1f)
        val vignette = extractFloatField(rawJson, "vignette", 0f).coerceIn(0f, 1f)
        val grayscale = extractFloatField(rawJson, "grayscale", 0f).coerceIn(0f, 1f)
        val sepia = extractFloatField(rawJson, "sepia", 0f).coerceIn(0f, 1f)
        val filterPresetId = extractStringField(rawJson, "filterPresetId")
        val filterIntensity = extractFloatField(rawJson, "filterIntensity", 1f).coerceIn(0f, 1f)
        val blendMode = extractStringField(rawJson, "blendMode")?.let { BlendModeType.fromId(it) }
        val shakeAmp = extractFloatField(rawJson, "shakeAmplitude", 0f).coerceIn(0f, 120f)
        val shakeFreq = extractFloatField(rawJson, "shakeFrequencyHz", 0f).coerceIn(0f, 30f)
        val effectName = extractStringField(rawJson, "effectName")
        val effectIntensity = extractFloatField(rawJson, "effectIntensity", 0f).coerceIn(0f, 1f)
        val transitionStyle = extractStringField(rawJson, "transitionStyle")
        val transitionDurationMs = extractFloatField(rawJson, "transitionDurationMs", 0f).toInt().coerceIn(0, 2000)
        val oneFrameStyle = extractStringField(rawJson, "oneFrameStyle")
        val textAnimationStyle = extractStringField(rawJson, "textAnimationStyle")

        return PresetValidationResult.Valid(
            PresetDefinition(
                id = extractStringField(rawJson, "id") ?: fallbackId,
                title = title,
                description = description,
                category = category,
                creatorName = creatorName,
                creatorHandle = "@${creatorName.lowercase().replace(" ", "_")}",
                schemaVersion = 1,
                shakeAmplitude = shakeAmp,
                shakeFrequencyHz = shakeFreq,
                effectName = effectName,
                effectIntensity = effectIntensity,
                transitionStyle = transitionStyle,
                transitionDurationMs = transitionDurationMs,
                oneFrameStyle = oneFrameStyle,
                textAnimationStyle = textAnimationStyle,
                blendMode = blendMode,
                colorGrade = ColorGradeSpec(
                    exposure = exposure,
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    temperature = temperature,
                    tint = tint,
                    highlights = highlights,
                    shadows = shadows,
                    sharpness = sharpness,
                    blurRadius = blurRadius,
                    glow = glow,
                    vignette = vignette,
                    grayscale = grayscale,
                    sepia = sepia,
                    filterPresetId = filterPresetId,
                    filterIntensity = filterIntensity,
                ),
                isBuiltIn = false,
            )
        )
    }

    fun toExcevoJson(preset: PresetDefinition): String = buildString {
        appendLine("{")
        appendLine("""  "presetFileFormat": "excevo-preset/v1",""")
        appendLine("""  "id": "${escapeJson(preset.id)}",""")
        appendLine("""  "title": "${escapeJson(preset.title)}",""")
        appendLine("""  "category": "${preset.category.id}",""")
        appendLine("""  "description": "${escapeJson(preset.description)}",""")
        appendLine("""  "creatorName": "${escapeJson(preset.creatorName)}",""")
        appendLine("""  "shakeAmplitude": ${preset.shakeAmplitude},""")
        appendLine("""  "shakeFrequencyHz": ${preset.shakeFrequencyHz},""")
        if (!preset.effectName.isNullOrBlank()) {
            appendLine("""  "effectName": "${escapeJson(preset.effectName)}",""")
        }
        appendLine("""  "effectIntensity": ${preset.effectIntensity},""")
        if (!preset.transitionStyle.isNullOrBlank()) {
            appendLine("""  "transitionStyle": "${escapeJson(preset.transitionStyle)}",""")
            appendLine("""  "transitionDurationMs": ${preset.transitionDurationMs},""")
        }
        if (!preset.oneFrameStyle.isNullOrBlank()) {
            appendLine("""  "oneFrameStyle": "${escapeJson(preset.oneFrameStyle)}",""")
        }
        if (!preset.textAnimationStyle.isNullOrBlank()) {
            appendLine("""  "textAnimationStyle": "${escapeJson(preset.textAnimationStyle)}",""")
        }
        appendLine("""  "exposure": ${preset.colorGrade.exposure},""")
        appendLine("""  "brightness": ${preset.colorGrade.brightness},""")
        appendLine("""  "contrast": ${preset.colorGrade.contrast},""")
        appendLine("""  "saturation": ${preset.colorGrade.saturation},""")
        appendLine("""  "temperature": ${preset.colorGrade.temperature},""")
        appendLine("""  "tint": ${preset.colorGrade.tint},""")
        appendLine("""  "highlights": ${preset.colorGrade.highlights},""")
        appendLine("""  "shadows": ${preset.colorGrade.shadows},""")
        appendLine("""  "sharpness": ${preset.colorGrade.sharpness},""")
        appendLine("""  "blurRadius": ${preset.colorGrade.blurRadius},""")
        appendLine("""  "glow": ${preset.colorGrade.glow},""")
        appendLine("""  "vignette": ${preset.colorGrade.vignette},""")
        appendLine("""  "grayscale": ${preset.colorGrade.grayscale},""")
        appendLine("""  "sepia": ${preset.colorGrade.sepia},""")
        if (preset.blendMode != null) {
            appendLine("""  "blendMode": "${preset.blendMode.id}",""")
        }
        if (!preset.colorGrade.filterPresetId.isNullOrBlank()) {
            appendLine("""  "filterPresetId": "${escapeJson(preset.colorGrade.filterPresetId)}",""")
        }
        appendLine("""  "filterIntensity": ${preset.colorGrade.filterIntensity}""")
        append("}")
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun extractStringField(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"\\]*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractFloatField(json: String, key: String, default: Float): Float {
        val regex = Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: default
    }
}
