package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.engine.PresetSchemaValidator
import com.example.core.model.LibraryAssetItem
import com.example.core.model.LibraryItemType
import com.example.core.model.StorageStats
import com.example.data.db.LibraryDao
import com.example.data.db.LibraryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class StorageManager(
    private val context: Context,
    private val libraryDao: LibraryDao,
) {
    private val projectsDir: File = File(context.filesDir, "excevo_projects").apply { mkdirs() }
    private val libraryDir: File = File(context.filesDir, "excevo_library").apply { mkdirs() }

    fun observeLibraryItems(): Flow<List<LibraryAssetItem>> =
        libraryDao.observeAll().map { rows ->
            rows.map { row ->
                LibraryAssetItem(
                    id = row.id,
                    itemType = runCatching { LibraryItemType.valueOf(row.itemType) }.getOrDefault(LibraryItemType.OVERLAY),
                    name = row.name,
                    localPath = row.localPath,
                    sizeBytes = row.sizeBytes,
                    createdAtMs = row.createdAtMs,
                )
            }
        }

    suspend fun computeStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        var projectDocsBytes = 0L
        var thumbsBytes = 0L
        projectsDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                if (file.parentFile?.name == "thumbs") {
                    thumbsBytes += file.length()
                } else {
                    projectDocsBytes += file.length()
                }
            }
        }
        val libraryBytes = directorySizeBytes(libraryDir)
        val cacheBytes = directorySizeBytes(context.cacheDir)
        StorageStats(
            projectFilesBytes = projectDocsBytes,
            thumbnailBytes = thumbsBytes,
            libraryBytes = libraryBytes,
            cacheBytes = cacheBytes,
        )
    }

    suspend fun clearCache(): StorageStats = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
        computeStorageStats()
    }

    suspend fun importLibraryAsset(uriString: String, itemType: LibraryItemType): Result<LibraryAssetItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val displayName = queryFileName(uri)
                if (itemType == LibraryItemType.PRESET) {
                    val fileErr = PresetSchemaValidator.validateFileName(displayName)
                    if (fileErr != null) error(fileErr)
                } else {
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("dex", "jar", "so", "apk", "sh", "exe", "js")) {
                        error("Blocked unsupported or executable file type .$ext.")
                    }
                }
                val id = UUID.randomUUID().toString()
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val destFile = File(libraryDir, "${id}_$safeName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to open selected file.")

                val now = System.currentTimeMillis()
                val entity = LibraryItemEntity(
                    id = id,
                    itemType = itemType.name,
                    name = displayName,
                    localPath = destFile.absolutePath,
                    sizeBytes = destFile.length(),
                    createdAtMs = now,
                )
                libraryDao.insert(entity)
                LibraryAssetItem(
                    id = id,
                    itemType = itemType,
                    name = displayName,
                    localPath = destFile.absolutePath,
                    sizeBytes = destFile.length(),
                    createdAtMs = now,
                )
            }
        }

    suspend fun saveGeneratedPresetToLibrary(
        presetId: String,
        title: String,
        rawJson: String,
    ): Result<LibraryAssetItem> = withContext(Dispatchers.IO) {
        runCatching {
            val safeTitle = title.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { presetId }
            val fileName = "${safeTitle}.excevo.json"
            val destFile = File(libraryDir, "preset_${presetId}_$fileName")
            destFile.writeText(rawJson)
            val now = System.currentTimeMillis()
            val entity = LibraryItemEntity(
                id = "preset_lib_$presetId",
                itemType = LibraryItemType.PRESET.name,
                name = fileName,
                localPath = destFile.absolutePath,
                sizeBytes = destFile.length(),
                createdAtMs = now,
            )
            libraryDao.insert(entity)
            LibraryAssetItem(
                id = entity.id,
                itemType = LibraryItemType.PRESET,
                name = entity.name,
                localPath = entity.localPath,
                sizeBytes = entity.sizeBytes,
                createdAtMs = now,
            )
        }
    }

    suspend fun deleteLibraryItem(item: LibraryAssetItem) = withContext(Dispatchers.IO) {
        libraryDao.deleteById(item.id)
        File(item.localPath).takeIf { it.exists() }?.delete()
        Unit
    }

    private fun queryFileName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "library_asset"
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx) ?: name
                    }
                }
        }
        return name
    }

    private fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format("%.2f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }
    }
}
