package com.example.core.engine

import com.example.core.model.SequenceDiagnosticReport
import com.example.core.model.TimelineProject

/**
 * Real deterministic timeline analyzer for Home AI Lab diagnostics (no fake AI stubs).
 */
object SequenceDiagnosticsEngine {
    fun analyze(project: TimelineProject): SequenceDiagnosticReport {
        val clips = project.primaryVideoTrack().clips
        val totalUs = project.totalDurationUs()
        val avgUs = if (clips.isEmpty()) 0L else totalUs / clips.size

        val pacing = when {
            clips.isEmpty() -> "No clips yet"
            avgUs < 1_500_000L -> "Fast Rhythm (<1.5s/cut)"
            avgUs <= 4_000_000L -> "Balanced Pacing (1.5s–4s/cut)"
            else -> "Long-Form / Slow Cuts (>4s/cut)"
        }

        val recommendations = buildList {
            if (clips.isEmpty()) {
                add("Import video or image clips in Studio to evaluate cut pacing and framing.")
            } else {
                if (avgUs > 5_000_000L) {
                    add("Consider splitting long takes at beat peaks to keep retention crisp.")
                }
                val ungradedCount = clips.count {
                    it.colorGrade.exposure == 0f && it.colorGrade.contrast == 0f && it.colorGrade.saturation == 0f
                }
                if (ungradedCount > 0) {
                    add("$ungradedCount clip(s) have neutral color grade — try a free CC preset from Presets.")
                }
                val mismatchedAspect = clips.count { clip ->
                    val asset = project.assets[clip.assetId] ?: return@count false
                    val assetIsPortrait = asset.height > asset.width
                    val seqIsPortrait = project.aspectRatio.heightRatio > project.aspectRatio.widthRatio
                    asset.width > 0 && asset.height > 0 && (assetIsPortrait != seqIsPortrait)
                }
                if (mismatchedAspect > 0) {
                    add("$mismatchedAspect clip(s) differ from ${project.aspectRatio.shortLabel} orientation — check Stage Safe Guides.")
                }
                if (isEmpty()) {
                    add("Sequence framing and cut rhythm look balanced for ${project.aspectRatio.shortLabel} @ ${project.frameRate.fps}fps.")
                }
            }
        }

        return SequenceDiagnosticReport(
            projectId = project.id,
            projectName = project.name,
            clipCount = clips.size,
            totalDurationUs = totalUs,
            averageClipDurationUs = avgUs,
            pacingLabel = pacing,
            recommendations = recommendations,
        )
    }
}
