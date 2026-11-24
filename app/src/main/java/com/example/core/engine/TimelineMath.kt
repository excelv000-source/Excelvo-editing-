package com.example.core.engine

import com.example.core.model.EasingType
import com.example.core.model.Keyframe
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineTrack
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object TimelineMath {
    const val MIN_CLIP_DURATION_US: Long = 100_000L // 0.1 seconds
    const val DEFAULT_IMAGE_DURATION_US: Long = 3_000_000L // 3.0 seconds

    fun formatTimecode(timeUs: Long, fps: Int): String {
        val safeUs = max(0L, timeUs)
        val safeFps = max(1, fps)
        val totalSeconds = safeUs / 1_000_000L
        val remainderUs = safeUs % 1_000_000L
        val frame = ((remainderUs * safeFps) / 1_000_000L).toInt().coerceIn(0, safeFps - 1)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            "${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)}:${pad2(frame.toLong())}"
        } else {
            "${pad2(minutes)}:${pad2(seconds)}:${pad2(frame.toLong())}"
        }
    }

    fun formatDurationCompact(timeUs: Long): String {
        val safeUs = max(0L, timeUs)
        val totalTenths = safeUs / 100_000L
        val seconds = (totalTenths / 10L) % 60L
        val minutes = (totalTenths / 600L) % 60L
        val hours = totalTenths / 36_000L
        return if (hours > 0) {
            "${hours}h ${pad2(minutes)}m"
        } else if (minutes > 0) {
            "${pad2(minutes)}:${pad2(seconds)}"
        } else {
            val tenths = totalTenths % 10L
            "${seconds}.${tenths}s"
        }
    }

    fun stepFrameUs(currentUs: Long, deltaFrames: Int, fps: Int, maxDurationUs: Long): Long {
        val safeFps = max(1, fps)
        val currentFrame = ((max(0L, currentUs) * safeFps) + 500_000L) / 1_000_000L
        val targetFrame = max(0L, currentFrame + deltaFrames)
        val targetUs = kotlin.math.ceil(targetFrame * 1_000_000.0 / safeFps).toLong()
        return targetUs.coerceIn(0L, max(0L, maxDurationUs))
    }

    fun usToPx(timeUs: Long, pxPerSecond: Float): Float =
        (timeUs / 1_000_000f) * pxPerSecond

    fun pxToUs(px: Float, pxPerSecond: Float): Long {
        if (pxPerSecond <= 0f) return 0L
        return ((px / pxPerSecond) * 1_000_000.0).toLong()
    }

    fun findActiveClipAt(track: TimelineTrack, playheadUs: Long): TimelineClip? {
        if (track.clips.isEmpty()) return null
        val clamped = playheadUs.coerceAtLeast(0L)
        return track.clips.firstOrNull { clip ->
            clamped >= clip.timelineStartUs && clamped < clip.timelineEndUs
        } ?: track.clips.lastOrNull()?.takeIf { clamped == it.timelineEndUs && it.timelineDurationUs > 0L }
    }

    fun timelineUsToSourceUs(clip: TimelineClip, timelineUs: Long): Long {
        val localUs = (timelineUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
        val mapped = clip.sourceStartUs + (localUs * clip.speedMultiplier).toLong()
        return mapped.coerceIn(clip.sourceStartUs, clip.sourceEndUs)
    }

    fun recalculateSequentialStarts(clips: List<TimelineClip>): List<TimelineClip> {
        var cursorUs = 0L
        return clips.map { clip ->
            val updated = clip.copy(timelineStartUs = cursorUs)
            cursorUs += updated.timelineDurationUs
            updated
        }
    }

    fun snapTimeUs(
        candidateUs: Long,
        snapTargetsUs: List<Long>,
        thresholdUs: Long,
    ): Long {
        var best = candidateUs
        var minDist = Long.MAX_VALUE
        for (target in snapTargetsUs) {
            val dist = kotlin.math.abs(candidateUs - target)
            if (dist <= thresholdUs && dist < minDist) {
                minDist = dist
                best = target
            }
        }
        return max(0L, best)
    }

    private fun pad2(value: Long): String =
        if (value < 10L) "0$value" else value.toString()
}

object KeyframeInterpolator {
    fun evaluate(
        keyframes: List<Keyframe>,
        timeOffsetUs: Long,
        defaultValue: Float,
    ): Float {
        if (keyframes.isEmpty()) return defaultValue
        val sorted = keyframes.sortedBy { it.timeOffsetUs }
        if (timeOffsetUs <= sorted.first().timeOffsetUs) return sorted.first().value
        if (timeOffsetUs >= sorted.last().timeOffsetUs) return sorted.last().value

        for (i in 0 until sorted.lastIndex) {
            val k0 = sorted[i]
            val k1 = sorted[i + 1]
            if (timeOffsetUs in k0.timeOffsetUs..k1.timeOffsetUs) {
                val span = (k1.timeOffsetUs - k0.timeOffsetUs).toFloat()
                if (span <= 0f || k0.easing == EasingType.HOLD) return k0.value
                val rawT = ((timeOffsetUs - k0.timeOffsetUs) / span).coerceIn(0f, 1f)
                val easedT = applyEasing(rawT, k0.easing)
                return k0.value + (k1.value - k0.value) * easedT
            }
        }
        return sorted.last().value
    }

    private fun applyEasing(t: Float, easing: EasingType): Float {
        val x = min(1f, max(0f, t))
        return when (easing) {
            EasingType.LINEAR -> x
            EasingType.EASE_IN -> x * x * x
            EasingType.EASE_OUT -> 1f - (1f - x).pow(3)
            EasingType.EASE_IN_OUT ->
                if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            EasingType.HOLD -> 0f
        }
    }
}
