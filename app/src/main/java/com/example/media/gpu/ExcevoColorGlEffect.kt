package com.example.media.gpu

import android.content.Context
import android.graphics.Matrix
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.MatrixTransformation

@OptIn(UnstableApi::class)
class ExcevoColorGlEffect(
    private val liveState: ExcevoLiveRenderState,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ExcevoColorGlShaderProgram(useHdr = useHdr, liveState = liveState)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        // Keep active in the pipeline so live slider adjustments render without rebuilding ExoPlayer.
        return false
    }
}

@OptIn(UnstableApi::class)
class ExcevoColorGlShaderProgram(
    useHdr: Boolean,
    private val liveState: ExcevoLiveRenderState,
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var texelWidth: Float = 1f / 1080f
    private var texelHeight: Float = 1f / 1920f

    init {
        try {
            val isEs3 = runCatching { GlUtil.getContextMajorVersion() >= 3L }.getOrDefault(false)
            val vert = if (isEs3) ExcevoGlslShaders.VERTEX_SHADER_ES3 else ExcevoGlslShaders.VERTEX_SHADER_ES2
            val frag = if (isEs3) ExcevoGlslShaders.FRAGMENT_SHADER_ES3 else ExcevoGlslShaders.FRAGMENT_SHADER_ES2
            val program = GlProgram(vert, frag)
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            glProgram = program
        } catch (e: Exception) {
            throw VideoFrameProcessingException.from(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val safeW = inputWidth.coerceAtLeast(1)
        val safeH = inputHeight.coerceAtLeast(1)
        texelWidth = 1f / safeW.toFloat()
        texelHeight = 1f / safeH.toFloat()
        return Size(safeW, safeH)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        val u = liveState.uniforms.get()
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniformIfPresent("uTexelSize", floatArrayOf(texelWidth, texelHeight))
            program.setFloatUniform("uExposure", u.exposure)
            program.setFloatUniform("uBrightness", u.brightness)
            program.setFloatUniform("uContrast", u.contrast)
            program.setFloatUniform("uSaturation", u.saturation)
            program.setFloatUniform("uTemperature", u.temperature)
            program.setFloatUniform("uTint", u.tint)
            program.setFloatUniform("uHighlights", u.highlights)
            program.setFloatUniform("uShadows", u.shadows)
            program.setFloatUniform("uSharpness", u.sharpness)
            program.setFloatUniform("uBlur", u.blur)
            program.setFloatUniform("uGlow", u.glow)
            program.setFloatUniform("uVignette", u.vignette)
            program.setFloatUniform("uGrayscale", u.grayscale)
            program.setFloatUniform("uSepia", u.sepia)
            program.bindAttributesAndUniforms()

            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException.from(e)
        }
    }

    override fun release() {
        super.release()
        runCatching { glProgram?.delete() }
        glProgram = null
    }
}

@OptIn(UnstableApi::class)
class ExcevoTransformMatrixEffect(
    private val liveState: ExcevoLiveRenderState,
) : MatrixTransformation {

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val snap = liveState.transformSnapshot.get()
        val t = snap.transform
        val matrix = Matrix()
        val fillBoost = if (snap.scaleMode == com.example.core.model.CanvasScaleMode.FILL) {
            val aRatio = snap.targetAspect / snap.sourceAspect
            if (aRatio > 1f) aRatio else 1f / aRatio
        } else {
            1f
        }
        val zScale = (1000f / (1000f - t.positionZ).coerceAtLeast(150f)).coerceIn(0.25f, 3.0f)
        val sx = (t.scaleX * fillBoost * zScale).coerceIn(0.1f, 6f) * if (t.flipH) -1f else 1f
        val sy = (t.scaleY * fillBoost * zScale).coerceIn(0.1f, 6f) * if (t.flipV) -1f else 1f
        val pivotX = ((t.anchorX - 0.5f) * 2f).coerceIn(-1f, 1f)
        val pivotY = ((0.5f - t.anchorY) * 2f).coerceIn(-1f, 1f)
        matrix.postTranslate(-pivotX, -pivotY)
        matrix.postScale(sx, sy)
        if (t.skewXDeg != 0f || t.skewYDeg != 0f) {
            val kx = kotlin.math.tan(Math.toRadians(t.skewXDeg.coerceIn(-60f, 60f).toDouble())).toFloat()
            val ky = kotlin.math.tan(Math.toRadians(t.skewYDeg.coerceIn(-60f, 60f).toDouble())).toFloat()
            matrix.postSkew(kx, ky)
        }
        if (t.rotationDeg != 0f) {
            matrix.postRotate(-t.rotationDeg)
        }
        matrix.postTranslate(pivotX, pivotY)
        if (t.positionX != 0f || t.positionY != 0f) {
            matrix.postTranslate(
                (t.positionX / 540f).coerceIn(-2f, 2f),
                (-t.positionY / 540f).coerceIn(-2f, 2f),
            )
        }
        return matrix
    }
}
