package com.example.media.gpu

import com.example.core.engine.EffectGradeMath
import com.example.core.engine.GpuEffectUniforms
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.TransformSpec
import java.util.concurrent.atomic.AtomicReference

data class LiveTransformSnapshot(
    val transform: TransformSpec = TransformSpec(),
    val scaleMode: CanvasScaleMode = CanvasScaleMode.FIT,
    val targetAspect: Float = 9f / 16f,
    val sourceAspect: Float = 9f / 16f,
)

class ExcevoLiveRenderState {
    val uniforms = AtomicReference(GpuEffectUniforms())
    val transformSnapshot = AtomicReference(LiveTransformSnapshot())

    fun updateGrade(grade: ColorGradeSpec, effectStack: List<ClipEffectInstance> = emptyList()) {
        uniforms.set(EffectGradeMath.computeUniforms(grade, effectStack))
    }

    fun updateTransform(
        transform: TransformSpec,
        scaleMode: CanvasScaleMode,
        targetAspect: Float,
        sourceAspect: Float,
    ) {
        transformSnapshot.set(
            LiveTransformSnapshot(
                transform = transform,
                scaleMode = scaleMode,
                targetAspect = targetAspect.coerceIn(0.25f, 4f),
                sourceAspect = sourceAspect.coerceIn(0.25f, 4f),
            )
        )
    }
}
