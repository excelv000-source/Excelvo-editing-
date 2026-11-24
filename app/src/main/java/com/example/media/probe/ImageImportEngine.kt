package com.example.media.probe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import com.example.core.engine.TimelineMath
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.ports.ImportFailureReason
import com.example.core.ports.MediaImportException
import java.io.File
import java.io.FileOutputStream

object ImageImportEngine {

    private const val MAX_PREVIEW_DIM_PX = 1920
    private const val MAX_THUMB_DIM_PX = 480

    fun probeAndPrepare(
        context: Context,
        source: ResolvedMediaSource,
        assetId: String,
        projectId: String,
    ): MediaAsset {
        val exifOrientation = readExifOrientation(context, source)
        val rotationDeg = exifToDegrees(exifOrientation)

        // Step 1: Decode bounds only.
        // Note: BitmapFactory.decodeStream with inJustDecodeBounds=true ALWAYS returns null by Android design;
        // validity MUST be checked via outWidth/outHeight, never via the Bitmap? return value!
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.openReadableStream(context).use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }

        var rawWidth = boundsOpts.outWidth
        var rawHeight = boundsOpts.outHeight
        var decodedBitmap: Bitmap? = null

        // Fallback via ImageDecoder on API 28+ (e.g. HEIC / animated WebP / special color spaces)
        if ((rawWidth <= 0 || rawHeight <= 0) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoderResult = runCatching {
                val imgSource = ImageDecoder.createSource(context.contentResolver, source.effectiveFilePathOrUri(context))
                ImageDecoder.decodeBitmap(imgSource) { decoder, info, _ ->
                    val w = info.size.width.coerceAtLeast(1)
                    val h = info.size.height.coerceAtLeast(1)
                    val scale = minOf(1f, MAX_PREVIEW_DIM_PX.toFloat() / maxOf(w, h))
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1),
                    )
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            if (decoderResult != null) {
                rawWidth = decoderResult.width
                rawHeight = decoderResult.height
                decodedBitmap = decoderResult
            }
        }

        if (rawWidth <= 0 || rawHeight <= 0) {
            throw MediaImportException(
                ImportFailureReason.CORRUPTED_MEDIA,
                "Could not decode \"${source.displayName}\". The image file may be corrupted or unsupported.",
            )
        }

        val visualDims = if (rotationDeg == 90 || rotationDeg == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }

        // Step 2: Memory-safe sampled decode if not already decoded by ImageDecoder
        if (decodedBitmap == null) {
            val sampleSize = calculateInSampleSize(rawWidth, rawHeight, MAX_PREVIEW_DIM_PX, MAX_PREVIEW_DIM_PX)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampled = source.openReadableStream(context).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw MediaImportException(
                ImportFailureReason.DECODE_ERROR,
                "Failed to decode pixel data for \"${source.displayName}\".",
            )
            decodedBitmap = applyExifOrientation(sampled, exifOrientation)
        }

        val projectDir = File(File(context.filesDir, "excevo_projects"), projectId)
        val thumbDir = File(projectDir, "thumbs").apply { mkdirs() }
        val mediaDir = File(projectDir, "media").apply { mkdirs() }

        val thumbPath = saveScaledJpeg(decodedBitmap, File(thumbDir, "$assetId.jpg"), MAX_THUMB_DIM_PX, quality = 85)
        // Save an oriented, memory-capped preview plate if EXIF rotation was applied, image is very large, or URI wasn't persisted
        val needsCachedPlate = !source.uriPersisted ||
            source.cachedLocalPath != null ||
            exifOrientation != ExifInterface.ORIENTATION_NORMAL ||
            rawWidth > MAX_PREVIEW_DIM_PX ||
            rawHeight > MAX_PREVIEW_DIM_PX

        val previewPlatePath = if (needsCachedPlate) {
            saveScaledJpeg(decodedBitmap, File(mediaDir, "plate_${assetId}.jpg"), MAX_PREVIEW_DIM_PX, quality = 92)
                ?: source.cachedLocalPath
        } else {
            source.cachedLocalPath
        }

        decodedBitmap.recycle()

        return MediaAsset(
            id = assetId,
            sourceUri = source.uri.toString(),
            displayName = source.displayName,
            mediaType = MediaType.IMAGE,
            durationUs = TimelineMath.DEFAULT_IMAGE_DURATION_US,
            width = visualDims.first,
            height = visualDims.second,
            rotationDeg = rotationDeg,
            fps = 30f,
            hasAudio = false,
            fileSizeBytes = source.fileSizeBytes,
            thumbnailPath = thumbPath,
            mimeType = source.mimeType.ifBlank { boundsOpts.outMimeType.orEmpty() },
            cachedLocalPath = previewPlatePath,
            uriPersisted = source.uriPersisted,
            isAccessible = true,
        )
    }

    private fun readExifOrientation(context: Context, source: ResolvedMediaSource): Int {
        return runCatching {
            source.openReadableStream(context).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun exifToDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        var transformNeeded = true
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> transformNeeded = false
        }
        if (!transformNeeded) return bitmap
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    private fun saveScaledJpeg(source: Bitmap, outFile: File, maxDim: Int, quality: Int): String? = runCatching {
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

    fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
