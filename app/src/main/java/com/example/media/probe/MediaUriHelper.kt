package com.example.media.probe

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.model.MediaType
import com.example.core.ports.ImportFailureReason
import com.example.core.ports.MediaImportException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream

data class ResolvedMediaSource(
    val uri: Uri,
    val displayName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val mediaType: MediaType,
    val uriPersisted: Boolean,
    val cachedLocalPath: String? = null,
) {
    fun openReadableStream(context: Context): InputStream {
        cachedLocalPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file.inputStream()
            }
        }
        return MediaUriHelper.openInputStreamOrThrow(context, uri, displayName)
    }

    fun effectiveFilePathOrUri(context: Context): Uri {
        cachedLocalPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return Uri.fromFile(file)
            }
        }
        return uri
    }
}

object MediaUriHelper {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
    private val videoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp", "avi")
    private val audioExtensions = setOf("mp3", "wav", "aac", "m4a", "ogg", "oga", "flac", "opus")

    fun resolveSource(
        context: Context,
        sourceUri: String,
        projectId: String,
        assetId: String,
    ): ResolvedMediaSource {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull()
            ?: throw MediaImportException(
                ImportFailureReason.FILE_NOT_FOUND,
                "Invalid media URI.",
            )

        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path
            val file = if (!path.isNullOrBlank()) File(path) else null
            if (file == null || !file.exists() || !file.isFile) {
                throw MediaImportException(
                    ImportFailureReason.FILE_NOT_FOUND,
                    "Selected media file does not exist or was moved.",
                )
            }
            if (!file.canRead()) {
                throw MediaImportException(
                    ImportFailureReason.PERMISSION_DENIED,
                    "Permission denied reading ${file.name}.",
                )
            }
        }

        val uriPersisted = tryTakePersistablePermission(context, uri)
        val (displayName, queriedSize) = queryDisplayMeta(context, uri)

        // If a content:// URI cannot persist read permission (e.g. transient Photo Picker / Share grant),
        // copy safely into managed app storage ONCE so one-shot providers succeed and reopening remains reliable.
        val cachedLocalPath = if (uri.scheme == ContentResolver.SCHEME_CONTENT && !uriPersisted) {
            copyTransientUriToManagedCache(
                context = context,
                uri = uri,
                projectId = projectId,
                assetId = assetId,
                displayName = displayName,
            )
        } else {
            null
        }

        val headerBytes = if (cachedLocalPath != null) {
            readHeaderBytesFromFile(File(cachedLocalPath), displayName)
        } else {
            readHeaderBytes(context, uri, displayName)
        }

        val rawMime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val sniffedMime = sniffMimeType(headerBytes, displayName)
        val resolvedMime = when {
            rawMime.isNotBlank() && rawMime != "application/octet-stream" && rawMime != "*/*" -> rawMime
            sniffedMime.isNotBlank() -> sniffedMime
            else -> ""
        }

        val mediaType = classifyMediaType(resolvedMime, displayName)
            ?: throw MediaImportException(
                ImportFailureReason.UNSUPPORTED_FORMAT,
                "Unsupported file format for \"$displayName\". Supported: JPG, PNG, WebP, MP4, MOV, WebM, MP3, WAV, AAC, M4A, OGG, FLAC.",
            )

        val actualSize = when {
            queriedSize > 0L -> queriedSize
            cachedLocalPath != null -> File(cachedLocalPath).length()
            uri.scheme == ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).length() } ?: 0L
            else -> 0L
        }

        return ResolvedMediaSource(
            uri = uri,
            displayName = displayName,
            fileSizeBytes = actualSize,
            mimeType = resolvedMime,
            mediaType = mediaType,
            uriPersisted = uriPersisted || uri.scheme == ContentResolver.SCHEME_FILE,
            cachedLocalPath = cachedLocalPath,
        )
    }

    fun isMediaAccessible(
        context: Context,
        sourceUri: String,
        cachedLocalPath: String?,
    ): Boolean {
        if (!cachedLocalPath.isNullOrBlank()) {
            val cachedFile = File(cachedLocalPath)
            if (cachedFile.exists() && cachedFile.canRead() && cachedFile.length() > 0L) {
                return true
            }
        }
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return false
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val f = uri.path?.let { File(it) } ?: return false
            return f.exists() && f.canRead() && f.length() > 0L
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length != 0L
            } ?: context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun openInputStreamOrThrow(context: Context, uri: Uri, displayName: String): InputStream {
        return try {
            context.contentResolver.openInputStream(uri)
                ?: throw MediaImportException(
                    ImportFailureReason.FILE_NOT_FOUND,
                    "Unable to open \"$displayName\". The file may have been moved or deleted.",
                )
        } catch (se: SecurityException) {
            throw MediaImportException(
                ImportFailureReason.PERMISSION_DENIED,
                "Permission denied accessing \"$displayName\". Please re-select it from the picker.",
                se,
            )
        } catch (fnf: FileNotFoundException) {
            throw MediaImportException(
                ImportFailureReason.FILE_NOT_FOUND,
                "\"$displayName\" could not be found.",
                fnf,
            )
        } catch (e: MediaImportException) {
            throw e
        } catch (t: Throwable) {
            throw MediaImportException(
                ImportFailureReason.DECODE_ERROR,
                "Failed to read \"$displayName\": ${t.localizedMessage ?: "I/O error"}",
                t,
            )
        }
    }

    private fun tryTakePersistablePermission(context: Context, uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        return runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        }.getOrDefault(false)
    }

    private fun copyTransientUriToManagedCache(
        context: Context,
        uri: Uri,
        projectId: String,
        assetId: String,
        displayName: String,
    ): String? {
        val mediaDir = File(File(context.filesDir, "excevo_projects"), "$projectId/media").apply {
            mkdirs()
        }
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "media_item" }
        val targetFile = File(mediaDir, "${assetId}_$safeName")
        return runCatching {
            openInputStreamOrThrow(context, uri, displayName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            if (targetFile.length() > 0L) targetFile.absolutePath else {
                targetFile.delete()
                null
            }
        }.getOrNull()
    }

    private fun readHeaderBytesFromFile(file: File, displayName: String): ByteArray {
        return file.inputStream().use { input ->
            val buf = ByteArray(32)
            val read = input.read(buf)
            if (read <= 0) {
                throw MediaImportException(
                    ImportFailureReason.CORRUPTED_MEDIA,
                    "\"$displayName\" is empty (0 bytes).",
                )
            }
            if (read < buf.size) buf.copyOf(read) else buf
        }
    }

    private fun readHeaderBytes(context: Context, uri: Uri, displayName: String): ByteArray {
        return openInputStreamOrThrow(context, uri, displayName).use { input ->
            val buf = ByteArray(32)
            val read = input.read(buf)
            if (read <= 0) {
                throw MediaImportException(
                    ImportFailureReason.CORRUPTED_MEDIA,
                    "\"$displayName\" is empty (0 bytes).",
                )
            }
            if (read < buf.size) buf.copyOf(read) else buf
        }
    }

    fun queryDisplayMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Media Clip"
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        val queried = cursor.getString(nameIdx)
                        if (!queried.isNullOrBlank()) name = queried
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }

    fun sniffMimeType(bytes: ByteArray, fileName: String): String {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) {
            val tag = String(bytes, 8, 4, Charsets.US_ASCII)
            if (tag == "WEBP") return "image/webp"
            if (tag == "WAVE") return "audio/wav"
        }
        if (bytes.size >= 6) {
            val head6 = String(bytes, 0, 6, Charsets.US_ASCII)
            if (head6 == "GIF87a" || head6 == "GIF89a") return "image/gif"
        }
        if (bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
            return "image/bmp"
        }
        if (bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) {
            val brand = String(bytes, 8, 4, Charsets.US_ASCII).lowercase()
            return when {
                brand.startsWith("hei") || brand.startsWith("hev") || brand == "mif1" || brand == "avif" -> "image/heic"
                brand.startsWith("m4a") -> "audio/mp4"
                brand.startsWith("qt") -> "video/quicktime"
                else -> "video/mp4"
            }
        }
        if (bytes.size >= 4) {
            val head4 = String(bytes, 0, 4, Charsets.US_ASCII)
            if (head4 == "OggS") return "audio/ogg"
            if (head4 == "fLaC") return "audio/flac"
            if (head4.startsWith("ID3")) return "audio/mpeg"
            if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
            ) {
                return if (fileName.lowercase().endsWith(".mkv")) "video/x-matroska" else "video/webm"
            }
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte()) {
            val second = bytes[1].toInt() and 0xFF
            if ((second and 0xF6) == 0xF0) return "audio/aac"
            if ((second and 0xE0) == 0xE0) return "audio/mpeg"
        }

        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "ogg", "oga", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> ""
        }
    }

    fun classifyMediaType(mimeType: String, fileName: String): MediaType? {
        val lowerMime = mimeType.lowercase()
        if (lowerMime.startsWith("image/")) return MediaType.IMAGE
        if (lowerMime.startsWith("video/")) return MediaType.VIDEO
        if (lowerMime.startsWith("audio/")) return MediaType.AUDIO

        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in imageExtensions -> MediaType.IMAGE
            ext in videoExtensions -> MediaType.VIDEO
            ext in audioExtensions -> MediaType.AUDIO
            else -> null
        }
    }
}
