package com.example.media.probe

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.example.core.engine.TimelineMath
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.ports.ImportFailureReason
import com.example.core.ports.MediaImportException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

object AudioImportEngine {

    const val DEFAULT_WAVEFORM_BARS = 48

    private data class AudioTrackMeta(
        val durationUs: Long,
        val sampleRateHz: Int,
        val channelCount: Int,
        val audioCodec: String,
    )

    fun probeAndPrepare(
        context: Context,
        source: ResolvedMediaSource,
        assetId: String,
    ): MediaAsset {
        val targetUri = source.effectiveFilePathOrUri(context)

        // Check pure WAV PCM fast-path first (handles uncompressed RIFF WAVE reliably on every Android & JVM target)
        val wavAnalysis = runCatching {
            source.openReadableStream(context).use { stream ->
                analyzeWavPcm(stream, DEFAULT_WAVEFORM_BARS)
            }
        }.getOrNull()

        val extractorMeta = inspectAudioTrack(context, targetUri)

        if (extractorMeta != null && extractorMeta.audioCodec.isNotBlank() && wavAnalysis == null) {
            if (!hasAudioDecoderForMime(extractorMeta.audioCodec)) {
                throw MediaImportException(
                    ImportFailureReason.CODEC_UNAVAILABLE,
                    "Unsupported audio codec (${extractorMeta.audioCodec}) in \"${source.displayName}\".",
                )
            }
        }

        val retrieverMeta = inspectWithRetriever(context, targetUri)

        val durationUs = listOfNotNull(
            wavAnalysis?.durationUs,
            extractorMeta?.durationUs?.takeIf { it > 0L },
            retrieverMeta?.durationUs?.takeIf { it > 0L },
        ).firstOrNull() ?: throw MediaImportException(
            ImportFailureReason.CORRUPTED_MEDIA,
            "Unable to read audio stream in \"${source.displayName}\". The file may be corrupted or unsupported.",
        )

        val sampleRate = wavAnalysis?.sampleRateHz
            ?: extractorMeta?.sampleRateHz?.takeIf { it > 0 }
            ?: retrieverMeta?.sampleRateHz?.takeIf { it > 0 }
            ?: 44100

        val channels = wavAnalysis?.channelCount
            ?: extractorMeta?.channelCount?.takeIf { it > 0 }
            ?: 2

        val codec = when {
            wavAnalysis != null -> "audio/raw"
            !extractorMeta?.audioCodec.isNullOrBlank() -> extractorMeta.audioCodec
            else -> source.mimeType.ifBlank { "audio/mpeg" }
        }

        val waveform = wavAnalysis?.waveform
            ?: decodeWaveformViaMediaCodec(context, targetUri, durationUs, DEFAULT_WAVEFORM_BARS)

        return MediaAsset(
            id = assetId,
            sourceUri = source.uri.toString(),
            displayName = source.displayName,
            mediaType = MediaType.AUDIO,
            durationUs = durationUs.coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_US),
            width = 0,
            height = 0,
            fps = 30f,
            hasAudio = true,
            fileSizeBytes = source.fileSizeBytes,
            thumbnailPath = null,
            mimeType = source.mimeType.ifBlank { codec },
            sampleRateHz = sampleRate,
            channelCount = channels,
            audioCodec = codec,
            waveformSamples = waveform,
            cachedLocalPath = source.cachedLocalPath,
            uriPersisted = source.uriPersisted,
            isAccessible = true,
        )
    }

    private fun inspectAudioTrack(context: Context, uri: android.net.Uri): AudioTrackMeta? {
        val extractor = MediaExtractor()
        return try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    val dur = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                    val sr = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                    val ch = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                    return AudioTrackMeta(dur, sr, ch, mime)
                }
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun inspectWithRetriever(context: Context, uri: android.net.Uri): AudioTrackMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val sr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            AudioTrackMeta(
                durationUs = durationMs * 1000L,
                sampleRateHz = sr,
                channelCount = 2,
                audioCodec = "",
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun hasAudioDecoderForMime(mime: String): Boolean {
        if (mime.equals("audio/raw", ignoreCase = true)) return true
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            if (list.codecInfos.isEmpty()) true
            else list.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        }.getOrDefault(true)
    }

    internal data class WavAnalysisResult(
        val durationUs: Long,
        val sampleRateHz: Int,
        val channelCount: Int,
        val waveform: List<Float>,
    )

    internal fun analyzeWavPcm(input: InputStream, bucketCount: Int): WavAnalysisResult? {
        val header = ByteArray(12)
        if (readExact(input, header, 12) < 12) return null
        if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(header, 8, 4, Charsets.US_ASCII) != "WAVE"
        ) return null

        var audioFormat = 1
        var channels = 1
        var sampleRate = 44100
        var bitsPerSample = 16
        var dataSize = 0
        val chunkHeader = ByteArray(8)

        while (readExact(input, chunkHeader, 8) == 8) {
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val bb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)
            val chunkSize = bb.getInt(4)
            if (chunkSize < 0) break

            if (chunkId == "fmt " && chunkSize >= 16) {
                val fmtBytes = ByteArray(chunkSize.coerceAtMost(256))
                if (readExact(input, fmtBytes, fmtBytes.size) < 16) return null
                val remainingFmt = chunkSize - fmtBytes.size
                if (remainingFmt > 0) skipExact(input, remainingFmt.toLong())
                val fmtBb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                audioFormat = fmtBb.getShort(0).toInt() and 0xFFFF
                channels = fmtBb.getShort(2).toInt() and 0xFFFF
                sampleRate = fmtBb.getInt(4)
                bitsPerSample = fmtBb.getShort(14).toInt() and 0xFFFF
            } else if (chunkId == "data") {
                dataSize = chunkSize
                break
            } else {
                skipExact(input, chunkSize.toLong())
            }
            if ((chunkSize and 1) == 1) {
                skipExact(input, 1L)
            }
        }

        if (dataSize <= 0 || sampleRate <= 0 || channels <= 0) return null
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = bytesPerSample * channels
        val totalFrames = dataSize / frameSize
        if (totalFrames <= 0) return null

        val durationUs = (totalFrames * 1_000_000L) / sampleRate
        val buckets = FloatArray(bucketCount) { 0.06f }
        val framesPerBucket = (totalFrames.toFloat() / bucketCount).coerceAtLeast(1f)
        val buffer = ByteArray(16 * 1024)
        var framesReadSoFar = 0L

        for (b in 0 until bucketCount) {
            val endFrame = (((b + 1) * framesPerBucket).toLong()).coerceAtMost(totalFrames.toLong())
            val bucketFrames = (endFrame - framesReadSoFar).coerceAtLeast(0L)
            var bytesRemaining = bucketFrames * frameSize
            var peak = 0f

            while (bytesRemaining > 0L) {
                val alignedLimit = ((buffer.size / frameSize) * frameSize).coerceAtLeast(frameSize)
                val toRead = minOf(bytesRemaining, alignedLimit.toLong()).toInt()
                val actual = readExact(input, buffer, toRead)
                if (actual <= 0) break
                val bb = ByteBuffer.wrap(buffer, 0, actual).order(ByteOrder.LITTLE_ENDIAN)
                val framesInBuf = actual / frameSize
                val stride = (framesInBuf / 24).coerceAtLeast(1)
                var f = 0
                while (f < framesInBuf) {
                    val off = f * frameSize
                    val sampleNorm = when {
                        audioFormat == 3 && bitsPerSample == 32 && off + 4 <= actual ->
                            abs(bb.getFloat(off)).coerceIn(0f, 1f)
                        bitsPerSample == 8 -> abs((buffer[off].toInt() and 0xFF) - 128) / 128f
                        bitsPerSample == 16 && off + 2 <= actual -> abs(bb.getShort(off).toInt()) / 32768f
                        bitsPerSample == 24 && off + 3 <= actual -> {
                            val v = (buffer[off].toInt() and 0xFF) or
                                ((buffer[off + 1].toInt() and 0xFF) shl 8) or
                                (buffer[off + 2].toInt() shl 16)
                            abs(v) / 8388608f
                        }
                        bitsPerSample == 32 && off + 4 <= actual ->
                            abs(bb.getInt(off).toFloat() / Int.MAX_VALUE.toFloat())
                        else -> 0.1f
                    }
                    if (sampleNorm > peak) peak = sampleNorm
                    f += stride
                }
                bytesRemaining -= actual
                framesReadSoFar += actual / frameSize
            }
            buckets[b] = peak.coerceIn(0.06f, 1f)
        }

        return WavAnalysisResult(
            durationUs = durationUs,
            sampleRateHz = sampleRate,
            channelCount = channels,
            waveform = buckets.toList(),
        )
    }

    private fun readExact(input: InputStream, dest: ByteArray, targetBytes: Int): Int {
        var total = 0
        while (total < targetBytes) {
            val r = input.read(dest, total, targetBytes - total)
            if (r <= 0) break
            total += r
        }
        return total
    }

    private fun skipExact(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val discard = ByteArray(4096)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(discard, 0, minOf(remaining, discard.size.toLong()).toInt())
                if (read <= 0) break
                remaining -= read
            }
        }
    }

    private fun decodeWaveformViaMediaCodec(
        context: Context,
        uri: android.net.Uri,
        durationUs: Long,
        bucketCount: Int,
    ): List<Float> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val peaks = FloatArray(bucketCount) { 0.08f }

        return try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return peaks.toList()
            val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.equals("audio/raw", ignoreCase = true)) return peaks.toList()

            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            // Seek cleanly to N evenly spaced probe windows across the track so even long tracks extract fast
            for (bucketIdx in 0 until bucketCount) {
                val seekUs = (durationUs * bucketIdx) / bucketCount
                extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var bucketPeak = 0f
                var decodedFrames = 0
                var sawEos = false
                var iterations = 0

                while (decodedFrames < 2 && iterations < 12) {
                    iterations++
                    if (!sawEos) {
                        val inIdx = codec.dequeueInputBuffer(5_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val amp = measureBufferPeak(outBuf, bufferInfo.offset, bufferInfo.size, pcmEncoding)
                            if (amp > bucketPeak) bucketPeak = amp
                            decodedFrames++
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                }
                if (bucketPeak > 0f) {
                    peaks[bucketIdx] = bucketPeak.coerceIn(0.06f, 1f)
                }
            }

            // Normalize waveform subtly so quiet tracks remain visually readable
            val maxPeak = peaks.maxOrNull()?.coerceAtLeast(0.1f) ?: 1f
            if (maxPeak > 0.1f && maxPeak < 0.7f) {
                val boost = 0.85f / maxPeak
                for (i in peaks.indices) {
                    peaks[i] = (peaks[i] * boost).coerceIn(0.06f, 1f)
                }
            }
            peaks.toList()
        } catch (_: Throwable) {
            peaks.toList()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun measureBufferPeak(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        pcmEncoding: Int,
    ): Float {
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        dup.position(offset)
        dup.limit((offset + size).coerceAtMost(dup.capacity()))
        var sumSq = 0.0
        var peak = 0f
        var count = 0
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            while (dup.remaining() >= 4) {
                val v = abs(dup.float)
                if (v > peak) peak = v
                sumSq += v * v
                count++
            }
        } else {
            while (dup.remaining() >= 2) {
                val v = abs(dup.short.toInt()) / 32768f
                if (v > peak) peak = v
                sumSq += v * v
                count++
            }
        }
        val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
        return (peak * 0.65f + rms * 0.55f).coerceIn(0f, 1f)
    }
}
