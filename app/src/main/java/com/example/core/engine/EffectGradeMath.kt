package com.example.core.engine

import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.VisualEffectType
import kotlin.math.pow

/**
 * Pure-Kotlin color & visual effects math shared between Android GLES 3.0 shaders,
 * CPU/Compose image fallbacks, and future iOS Metal/CoreImage shaders.
 */
data class GpuEffectUniforms(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val sharpness: Float = 0f,
    val blur: Float = 0f,
    val glow: Float = 0f,
    val vignette: Float = 0f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
) {
    fun isIdentity(): Boolean =
        exposure == 0f &&
            brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            temperature == 0f &&
            tint == 0f &&
            highlights == 0f &&
            shadows == 0f &&
            sharpness == 0f &&
            blur == 0f &&
            glow == 0f &&
            vignette == 0f &&
            grayscale == 0f &&
            sepia == 0f
}

data class ColorFilterDefinition(
    val id: String,
    val name: String,
    val tagline: String,
    val accentHex: Long,
    val gradeDelta: ColorGradeSpec,
)

object ColorFilterCatalog {
    val filters: List<ColorFilterDefinition> = listOf(
        ColorFilterDefinition(
            id = "original",
            name = "Original",
            tagline = "Neutral",
            accentHex = 0xFF6E6B80L,
            gradeDelta = ColorGradeSpec(),
        ),
        ColorFilterDefinition(
            id = "cinema_teal",
            name = "Cinema Teal",
            tagline = "Teal & Warm",
            accentHex = 0xFF38BDF8L,
            gradeDelta = ColorGradeSpec(
                contrast = 0.16f,
                saturation = 0.08f,
                temperature = -0.14f,
                highlights = 0.12f,
                shadows = -0.15f,
                vignette = 0.22f,
            ),
        ),
        ColorFilterDefinition(
            id = "violet_dusk",
            name = "Violet Dusk",
            tagline = "EXCEVO Look",
            accentHex = 0xFFA855F7L,
            gradeDelta = ColorGradeSpec(
                contrast = 0.14f,
                saturation = 0.15f,
                temperature = -0.18f,
                tint = 0.28f,
                vignette = 0.24f,
            ),
        ),
        ColorFilterDefinition(
            id = "vintage_film",
            name = "Vintage Film",
            tagline = "Matte 35mm",
            accentHex = 0xFFF59E0BL,
            gradeDelta = ColorGradeSpec(
                exposure = 0.06f,
                contrast = -0.08f,
                saturation = -0.18f,
                temperature = 0.22f,
                shadows = 0.18f,
                sepia = 0.34f,
                vignette = 0.28f,
            ),
        ),
        ColorFilterDefinition(
            id = "mono_noir",
            name = "Mono Noir",
            tagline = "High-Con B&W",
            accentHex = 0xFFE5E7EBL,
            gradeDelta = ColorGradeSpec(
                contrast = 0.28f,
                highlights = 0.12f,
                shadows = -0.22f,
                grayscale = 1.0f,
                vignette = 0.32f,
            ),
        ),
        ColorFilterDefinition(
            id = "warm_sunset",
            name = "Warm Amber",
            tagline = "Golden Hour",
            accentHex = 0xFFFB923CL,
            gradeDelta = ColorGradeSpec(
                brightness = 0.08f,
                contrast = 0.10f,
                saturation = 0.22f,
                temperature = 0.34f,
                tint = 0.08f,
            ),
        ),
        ColorFilterDefinition(
            id = "arctic_crisp",
            name = "Arctic Crisp",
            tagline = "Clean & Sharp",
            accentHex = 0xFF60A5FAL,
            gradeDelta = ColorGradeSpec(
                exposure = 0.10f,
                contrast = 0.12f,
                saturation = -0.08f,
                temperature = -0.26f,
                sharpness = 0.35f,
            ),
        ),
    )

    fun findById(id: String?): ColorFilterDefinition =
        filters.firstOrNull { it.id == id } ?: filters.first()
}

object EffectGradeMath {

    fun computeUniforms(
        grade: ColorGradeSpec,
        effectStack: List<ClipEffectInstance> = emptyList(),
    ): GpuEffectUniforms {
        val filter = ColorFilterCatalog.findById(grade.filterPresetId)
        val fi = if (filter.id == "original") 0f else grade.filterIntensity.coerceIn(0f, 1f)
        val fg = filter.gradeDelta

        var stackBlur = 0f
        var stackGlow = 0f
        var stackVignette = 0f
        var stackGrayscale = 0f
        var stackSepia = 0f
        var stackSharpen = 0f

        effectStack.forEach { fx ->
            if (fx.enabled && fx.intensity > 0.001f) {
                val amt = fx.intensity.coerceIn(0f, 1f)
                when (fx.type) {
                    VisualEffectType.GAUSSIAN_BLUR -> stackBlur = kotlin.math.max(stackBlur, amt)
                    VisualEffectType.GLOW -> stackGlow = (stackGlow + amt).coerceIn(0f, 1f)
                    VisualEffectType.VIGNETTE -> stackVignette = (stackVignette + amt).coerceIn(0f, 1f)
                    VisualEffectType.GRAYSCALE -> stackGrayscale = kotlin.math.max(stackGrayscale, amt)
                    VisualEffectType.SEPIA -> stackSepia = kotlin.math.max(stackSepia, amt)
                    VisualEffectType.SHARPEN -> stackSharpen = (stackSharpen + amt).coerceIn(0f, 1f)
                }
            }
        }

        return GpuEffectUniforms(
            exposure = (grade.exposure + fg.exposure * fi).coerceIn(-1f, 1f),
            brightness = (grade.brightness + fg.brightness * fi).coerceIn(-1f, 1f),
            contrast = (grade.contrast + fg.contrast * fi).coerceIn(-1f, 1f),
            saturation = (grade.saturation + fg.saturation * fi).coerceIn(-1f, 1f),
            temperature = (grade.temperature + fg.temperature * fi).coerceIn(-1f, 1f),
            tint = (grade.tint + fg.tint * fi).coerceIn(-1f, 1f),
            highlights = (grade.highlights + fg.highlights * fi).coerceIn(-1f, 1f),
            shadows = (grade.shadows + fg.shadows * fi).coerceIn(-1f, 1f),
            sharpness = (grade.sharpness + fg.sharpness * fi + stackSharpen).coerceIn(0f, 1f),
            blur = kotlin.math.max((grade.blurRadius + fg.blurRadius * fi), stackBlur).coerceIn(0f, 1f),
            glow = kotlin.math.max((grade.glow + fg.glow * fi), stackGlow).coerceIn(0f, 1f),
            vignette = (grade.vignette + fg.vignette * fi + stackVignette).coerceIn(0f, 1f),
            grayscale = kotlin.math.max((grade.grayscale + fg.grayscale * fi), stackGrayscale).coerceIn(0f, 1f),
            sepia = kotlin.math.max((grade.sepia + fg.sepia * fi), stackSepia).coerceIn(0f, 1f),
        )
    }

    fun scalePresetGrade(grade: ColorGradeSpec, intensity: Float): ColorGradeSpec {
        val s = intensity.coerceIn(0f, 1f)
        return grade.copy(
            exposure = grade.exposure * s,
            brightness = grade.brightness * s,
            contrast = grade.contrast * s,
            saturation = grade.saturation * s,
            temperature = grade.temperature * s,
            tint = grade.tint * s,
            highlights = grade.highlights * s,
            shadows = grade.shadows * s,
            sharpness = grade.sharpness * s,
            blurRadius = grade.blurRadius * s,
            glow = grade.glow * s,
            vignette = grade.vignette * s,
            grayscale = grade.grayscale * s,
            sepia = grade.sepia * s,
            filterIntensity = grade.filterIntensity * s,
        )
    }

    /**
     * Builds a 4x5 row-major RGBA ColorMatrix (length 20, offset scaled 0..255 for Android/Skia ColorMatrix)
     * matching the GLSL fragment shader for image clips and non-GLES fallback rendering.
     */
    fun buildColorMatrix20(u: GpuEffectUniforms): FloatArray {
        val expGain = 2.0.pow(u.exposure.toDouble()).toFloat()
        val contrastScale = (1f + u.contrast * 0.75f).coerceIn(0.2f, 2.2f)
        val brightnessOffset = (u.brightness * 0.32f + (u.shadows - u.highlights * 0.35f) * 0.08f) * 255f
        val tempShift = u.temperature * 0.16f
        val tintShift = u.tint * 0.14f

        val rGain = (expGain * (1f + tempShift + tintShift * 0.5f)).coerceAtLeast(0.05f)
        val gGain = (expGain * (1f - tintShift)).coerceAtLeast(0.05f)
        val bGain = (expGain * (1f - tempShift + tintShift * 0.5f)).coerceAtLeast(0.05f)

        val sat = (1f + u.saturation).coerceIn(0f, 2.2f) * (1f - u.grayscale.coerceIn(0f, 1f))
        val invSat = 1f - sat
        val lr = 0.2126f * invSat
        val lg = 0.7152f * invSat
        val lb = 0.0722f * invSat

        var m00 = (lr + sat) * rGain * contrastScale
        var m01 = lg * rGain * contrastScale
        var m02 = lb * rGain * contrastScale

        var m10 = lr * gGain * contrastScale
        var m11 = (lg + sat) * gGain * contrastScale
        var m12 = lb * gGain * contrastScale

        var m20 = lr * bGain * contrastScale
        var m21 = lg * bGain * contrastScale
        var m22 = (lb + sat) * bGain * contrastScale

        // Blend sepia tone matrix
        val sep = u.sepia.coerceIn(0f, 1f)
        if (sep > 0f) {
            val invSep = 1f - sep
            val s00 = m00 * 0.393f + m10 * 0.769f + m20 * 0.189f
            val s01 = m01 * 0.393f + m11 * 0.769f + m21 * 0.189f
            val s02 = m02 * 0.393f + m12 * 0.769f + m22 * 0.189f

            val s10 = m00 * 0.349f + m10 * 0.686f + m20 * 0.168f
            val s11 = m01 * 0.349f + m11 * 0.686f + m21 * 0.168f
            val s12 = m02 * 0.349f + m12 * 0.686f + m22 * 0.168f

            val s20 = m00 * 0.272f + m10 * 0.534f + m20 * 0.131f
            val s21 = m01 * 0.272f + m11 * 0.534f + m21 * 0.131f
            val s22 = m02 * 0.272f + m12 * 0.534f + m22 * 0.131f

            m00 = m00 * invSep + s00 * sep
            m01 = m01 * invSep + s01 * sep
            m02 = m02 * invSep + s02 * sep
            m10 = m10 * invSep + s10 * sep
            m11 = m11 * invSep + s11 * sep
            m12 = m12 * invSep + s12 * sep
            m20 = m20 * invSep + s20 * sep
            m21 = m21 * invSep + s21 * sep
            m22 = m22 * invSep + s22 * sep
        }

        val contrastShift = 0.5f * (1f - contrastScale) * 255f
        val off = brightnessOffset + contrastShift

        return floatArrayOf(
            m00, m01, m02, 0f, off,
            m10, m11, m12, 0f, off,
            m20, m21, m22, 0f, off,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
