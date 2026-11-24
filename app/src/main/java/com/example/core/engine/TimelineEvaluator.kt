package com.example.core.engine

import com.example.core.model.AnimatableProperty
import com.example.core.model.Keyframe
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TransformSpec
import kotlin.math.abs

/**
 * Pure-Kotlin real-time timeline keyframe & boundary evaluator.
 * Shared across Studio preview canvas, Media3 ExoPlayer GPU pipeline,
 * Audio envelope controller, and future iOS Metal/AVFoundation engine.
 */
object TimelineEvaluator {

    const val KEYFRAME_SNAP_TOLERANCE_US = 45_000L

    fun evaluateTransformAt(clip: TimelineClip, timelineUs: Long): TransformSpec {
        val base = clip.transform
        if (clip.keyframes.isEmpty()) return base
        val localUs = (timelineUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
        return base.copy(
            positionX = evalProp(clip, AnimatableProperty.POSITION_X, localUs, base.positionX),
            positionY = evalProp(clip, AnimatableProperty.POSITION_Y, localUs, base.positionY),
            positionZ = evalProp(clip, AnimatableProperty.POSITION_Z, localUs, base.positionZ),
            scaleX = evalProp(clip, AnimatableProperty.SCALE_X, localUs, base.scaleX).coerceIn(0.05f, 6f),
            scaleY = evalProp(clip, AnimatableProperty.SCALE_Y, localUs, base.scaleY).coerceIn(0.05f, 6f),
            rotationDeg = evalProp(clip, AnimatableProperty.ROTATION_DEG, localUs, base.rotationDeg),
            skewXDeg = evalProp(clip, AnimatableProperty.SKEW_X, localUs, base.skewXDeg).coerceIn(-60f, 60f),
            skewYDeg = evalProp(clip, AnimatableProperty.SKEW_Y, localUs, base.skewYDeg).coerceIn(-60f, 60f),
            opacity = evalProp(clip, AnimatableProperty.OPACITY, localUs, base.opacity).coerceIn(0f, 1f),
            anchorX = evalProp(clip, AnimatableProperty.ANCHOR_X, localUs, base.anchorX).coerceIn(0f, 1f),
            anchorY = evalProp(clip, AnimatableProperty.ANCHOR_Y, localUs, base.anchorY).coerceIn(0f, 1f),
        )
    }

    fun evaluateVolumeAt(
        clip: TimelineClip,
        trackMuted: Boolean,
        timelineUs: Long,
    ): Float {
        if (clip.isMuted || trackMuted) return 0f
        val localUs = (timelineUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
        var vol = evalProp(clip, AnimatableProperty.VOLUME, localUs, clip.volume).coerceIn(0f, 1.5f)
        if (clip.fadeInUs > 0L && localUs < clip.fadeInUs) {
            val factor = (localUs.toFloat() / clip.fadeInUs.toFloat()).coerceIn(0f, 1f)
            vol *= factor
        }
        val remUs = (clip.timelineDurationUs - localUs).coerceAtLeast(0L)
        if (clip.fadeOutUs > 0L && remUs < clip.fadeOutUs) {
            val factor = (remUs.toFloat() / clip.fadeOutUs.toFloat()).coerceIn(0f, 1f)
            vol *= factor
        }
        return vol.coerceIn(0f, 1.5f)
    }

    fun findKeyframeNear(
        clip: TimelineClip,
        property: AnimatableProperty,
        localOffsetUs: Long,
        toleranceUs: Long = KEYFRAME_SNAP_TOLERANCE_US,
    ): Keyframe? {
        val list = clip.keyframes[property].orEmpty()
        return list.minByOrNull { abs(it.timeOffsetUs - localOffsetUs) }
            ?.takeIf { abs(it.timeOffsetUs - localOffsetUs) <= toleranceUs }
    }

    fun collectNavigationPointsUs(project: TimelineProject): List<Long> {
        val points = mutableSetOf(0L, project.totalDurationUs().coerceAtLeast(0L))
        project.allClips().forEach { clip ->
            points.add(clip.timelineStartUs.coerceAtLeast(0L))
            points.add(clip.timelineEndUs.coerceAtLeast(0L))
            clip.keyframes.values.flatten().forEach { kf ->
                val absUs = (clip.timelineStartUs + kf.timeOffsetUs).coerceIn(0L, project.totalDurationUs().coerceAtLeast(0L))
                points.add(absUs)
            }
        }
        return points.sorted()
    }

    fun findPreviousBoundaryUs(project: TimelineProject, playheadUs: Long): Long {
        val points = collectNavigationPointsUs(project)
        val threshold = (playheadUs - 20_000L).coerceAtLeast(0L)
        return points.lastOrNull { it < threshold } ?: 0L
    }

    fun findNextBoundaryUs(project: TimelineProject, playheadUs: Long): Long {
        val maxUs = project.totalDurationUs().coerceAtLeast(0L)
        val points = collectNavigationPointsUs(project)
        val threshold = playheadUs + 20_000L
        return points.firstOrNull { it > threshold } ?: maxUs
    }

    fun normalizedValueFor(transform: TransformSpec, volume: Float, prop: AnimatableProperty): Float = when (prop) {
        AnimatableProperty.POSITION_X -> transform.positionX
        AnimatableProperty.POSITION_Y -> transform.positionY
        AnimatableProperty.POSITION_Z -> transform.positionZ
        AnimatableProperty.SCALE_X -> transform.scaleX
        AnimatableProperty.SCALE_Y -> transform.scaleY
        AnimatableProperty.ROTATION_DEG -> transform.rotationDeg
        AnimatableProperty.SKEW_X -> transform.skewXDeg
        AnimatableProperty.SKEW_Y -> transform.skewYDeg
        AnimatableProperty.OPACITY -> transform.opacity
        AnimatableProperty.ANCHOR_X -> transform.anchorX
        AnimatableProperty.ANCHOR_Y -> transform.anchorY
        AnimatableProperty.VOLUME -> volume
    }

    private fun evalProp(
        clip: TimelineClip,
        prop: AnimatableProperty,
        localOffsetUs: Long,
        defaultValue: Float,
    ): Float {
        val list = clip.keyframes[prop]
        if (list.isNullOrEmpty()) return defaultValue
        return KeyframeInterpolator.evaluate(list, localOffsetUs, defaultValue)
    }
}
