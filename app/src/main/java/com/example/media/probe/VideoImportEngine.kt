package com.example.media.probe

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.example.core.engine.TimelineMath
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.ports.ImportFailureReason
import com.example.core.ports.MediaImportException
import java.io.File
import java.io.FileOutputStream

object VideoImportEngine {

    private const val FILMSTRIP_FRAME_COUNT = 6

    private data class ExtractorVideoInfo(
        val fps: Float?,
        val videoCodec: String,
        val audioCodec: String,
        val hasVideoTrack: Boolean,
        val hasAudioTrack: Boolean,
        val width: Int,
        val height: Int,
        val durationUs: Long,
    )

    fun probeAndPrepare(
        context: Context,
        source: ResolvedMediaSource,
        assetId: String,
        projectId: String,
    ): MediaAsset {
        val targetUri = source.effectiveFilePathOrUri(context)
        val extractorInfo = inspectTracks(context, targetUri)

        if (extractorInfo != null && extractorInfo.hasVideoTrack && extractorInfo.videoCodec.isNotBlank()) {
            if (!hasDecoderForMime(extractorInfo.videoCodec)) {
                throw MediaImportException(
                    ImportFailureReason.CODEC_UNAVAILABLE,
                    "Unsupported video codec (${extractorInfo.videoCodec}) in \"${source.displayName}\" on this device.",
                )
            }
        }

        val retriever = MediaMetadataRetriever()
        val projectDir = File(File(context.filesDir, "excevo_projects"), projectId)
        val thumbDir = File(projectDir, "thumbs").apply { mkdirs() }
        val filmstripDir = File(File(projectDir, "filmstrips"), assetId).apply { mkdirs() }

        var width = extractorInfo?.width ?: 0
        var height = extractorInfo?.height ?: 0
        var rotation = 0
        var durationUs = extractorInfo?.durationUs ?: 0L
        var hasAudio = extractorInfo?.hasAudioTrack ?: false
        var thumbPath: String? = null
        val filmstripPaths = mutableListOf<String>()

        try {
            runCatching {
                if (targetUri.scheme == "file" && !targetUri.path.isNullOrBlank()) {
                    retriever.setDataSource(targetUri.path)
                } else {
                    retriever.setDataSource(context, targetUri)
                }
            }.onFailure { err ->
                if (extractorInfo == null || !extractorInfo.hasVideoTrack) {
                    throw MediaImportException(
                        ImportFailureReason.CORRUPTED_MEDIA,
                        "Unable to open video \"${source.displayName}\": ${err.localizedMessage ?: "Unsupported container or corrupted file."}",
                        err,
                    )
                }
            }

            val retrieverDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (retrieverDurationMs != null && retrieverDurationMs > 0L) {
                durationUs = retrieverDurationMs * 1000L
            }
            val rw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val rh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (rw != null && rw > 0) width = rw
            if (rh != null && rh > 0) height = rh
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasAudioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (hasAudioMeta != null) {
                hasAudio = hasAudioMeta.equals("yes", ignoreCase = true)
            }

            if (width <= 0 || height <= 0 || durationUs <= 0L) {
                if (extractorInfo == null || !extractorInfo.hasVideoTrack) {
                    throw MediaImportException(
                        ImportFailureReason.CORRUPTED_MEDIA,
                        "\"${source.displayName}\" does not contain a readable video stream.",
                    )
                }
            }

            durationUs = durationUs.coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_US)
            width = width.coerceAtLeast(2)
            height = height.coerceAtLeast(2)

            val heroTimeUs = minOf(500_000L, durationUs / 2L)
            val heroFrame = retriever.getFrameAtTime(heroTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
            if (heroFrame != null) {
                thumbPath = saveScaledFrame(heroFrame, File(thumbDir, "$assetId.jpg"), maxDim = 640, quality = 85)
                heroFrame.recycle()
            }

            for (i in 0 until FILMSTRIP_FRAME_COUNT) {
                val sampleUs = ((durationUs * (i + 0.5)) / FILMSTRIP_FRAME_COUNT).toLong()
                    .coerceIn(0L, (durationUs - 1_000L).coerceAtLeast(0L))
                val frame = runCatching {
                    retriever.getFrameAtTime(sampleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull()
                if (frame != null) {
                    val saved = saveScaledFrame(frame, File(filmstripDir, "f_$i.jpg"), maxDim = 180, quality = 76)
                    if (saved != null) filmstripPaths.add(saved)
                    frame.recycle()
                }
            }
            if (filmstripPaths.isEmpty() && thumbPath != null) {
                filmstripPaths.add(thumbPath)
            }
        } finally {
            runCatching { retriever.release() }
        }

        val (visualW, visualH) = if (rotation == 90 || rotation == 270) height to width else width to height

        return MediaAsset(
            id = assetId,
            sourceUri = source.uri.toString(),
            displayName = source.displayName,
            mediaType = MediaType.VIDEO,
            durationUs = durationUs,
            width = visualW,
            height = visualH,
            rotationDeg = rotation,
            fps = extractorInfo?.fps ?: 30f,
            hasAudio = hasAudio,
            fileSizeBytes = source.fileSizeBytes,
            thumbnailPath = thumbPath,
            mimeType = source.mimeType,
            audioCodec = extractorInfo?.audioCodec.orEmpty(),
            videoCodec = extractorInfo?.videoCodec.orEmpty(),
            filmstripPaths = filmstripPaths,
            cachedLocalPath = source.cachedLocalPath,
            uriPersisted = source.uriPersisted,
            isAccessible = true,
        )
    }

    private fun inspectTracks(context: Context, uri: android.net.Uri): ExtractorVideoInfo? {
        val extractor = MediaExtractor()
        return try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            var fps: Float? = null
            var videoCodec = ""
            var audioCodec = ""
            var hasVideo = false
            var hasAudio = false
            var width = 0
            var height = 0
            var durationUs = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && !hasVideo) {
                    hasVideo = true
                    videoCodec = mime
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                            .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull() }
                    }
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) width = format.getInteger(MediaFormat.KEY_WIDTH)
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                    }
                } else if (mime.startsWith("audio/") && !hasAudio) {
                    hasAudio = true
                    audioCodec = mime
                }
            }
            ExtractorVideoInfo(
                fps = fps,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                hasVideoTrack = hasVideo,
                hasAudioTrack = hasAudio,
                width = width,
                height = height,
                durationUs = durationUs,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun hasDecoderForMime(mime: String): Boolean {
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val hasHwOrSw = list.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            // If codec list is empty (e.g. headless JVM Robolectric environment), avoid false negatives
            if (list.codecInfos.isEmpty()) true else hasHwOrSw
        }.getOrDefault(true)
    }

    private fun saveScaledFrame(source: Bitmap, outFile: File, maxDim: Int, quality: Int): String? = runCatching {
        val scale = minOf(1f, maxDim.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1))
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, targetW, targetH, true) else source
        FileOutputStream(outFile).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        if (scaled !== source) scaled.recycle()
        outFile.absolutePath
    }.getOrNull()
}
