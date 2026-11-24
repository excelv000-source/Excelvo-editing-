package com.example.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.data.db.MediaFolderEntity
import com.example.data.db.RecentMediaDao
import com.example.data.db.RecentMediaEntity
import com.example.media.probe.MediaUriHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class RecentMediaItem(
    val sourceUri: String,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val sampleRateHz: Int,
    val channelCount: Int,
    val fileSizeBytes: Long,
    val thumbnailPath: String?,
    val cachedLocalPath: String?,
    val lastImportedAtMs: Long,
)

data class GrantedFolderItem(
    val treeUri: String,
    val displayName: String,
    val addedAtMs: Long,
)

data class DeviceFolderMediaItem(
    val uriString: String,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

class MediaLibraryRepository(
    private val context: Context,
    private val recentMediaDao: RecentMediaDao,
) {
    fun observeRecentMedia(): Flow<List<RecentMediaItem>> =
        recentMediaDao.observeRecentMedia().map { rows ->
            rows.map { row ->
                RecentMediaItem(
                    sourceUri = row.sourceUri,
                    displayName = row.displayName,
                    mediaType = runCatching { MediaType.valueOf(row.mediaType) }.getOrDefault(MediaType.IMAGE),
                    mimeType = row.mimeType,
                    durationUs = row.durationUs,
                    width = row.width,
                    height = row.height,
                    fps = row.fps,
                    sampleRateHz = row.sampleRateHz,
                    channelCount = row.channelCount,
                    fileSizeBytes = row.fileSizeBytes,
                    thumbnailPath = row.thumbnailPath,
                    cachedLocalPath = row.cachedLocalPath,
                    lastImportedAtMs = row.lastImportedAtMs,
                )
            }
        }

    fun observeGrantedFolders(): Flow<List<GrantedFolderItem>> =
        recentMediaDao.observeFolders().map { rows ->
            rows.map { row ->
                GrantedFolderItem(
                    treeUri = row.treeUri,
                    displayName = row.displayName,
                    addedAtMs = row.addedAtMs,
                )
            }
        }

    suspend fun recordImportedAsset(asset: MediaAsset) = withContext(Dispatchers.IO) {
        recentMediaDao.upsertRecent(
            RecentMediaEntity(
                sourceUri = asset.sourceUri,
                displayName = asset.displayName,
                mediaType = asset.mediaType.name,
                mimeType = asset.mimeType,
                durationUs = asset.durationUs,
                width = asset.width,
                height = asset.height,
                fps = asset.fps,
                sampleRateHz = asset.sampleRateHz,
                channelCount = asset.channelCount,
                fileSizeBytes = asset.fileSizeBytes,
                thumbnailPath = asset.thumbnailPath,
                cachedLocalPath = asset.cachedLocalPath,
                lastImportedAtMs = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeRecentMedia(sourceUri: String) = withContext(Dispatchers.IO) {
        recentMediaDao.deleteRecent(sourceUri)
    }

    suspend fun addGrantedFolder(treeUriString: String): Result<GrantedFolderItem> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = Uri.parse(treeUriString)
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val folderName = queryTreeFolderName(treeUri)
            val now = System.currentTimeMillis()
            val entity = MediaFolderEntity(
                treeUri = treeUriString,
                displayName = folderName,
                addedAtMs = now,
            )
            recentMediaDao.upsertFolder(entity)
            GrantedFolderItem(treeUriString, folderName, now)
        }
    }

    suspend fun removeGrantedFolder(treeUriString: String) = withContext(Dispatchers.IO) {
        recentMediaDao.deleteFolder(treeUriString)
    }

    suspend fun listMediaInFolder(treeUriString: String): Result<List<DeviceFolderMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = Uri.parse(treeUriString)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val results = mutableListOf<DeviceFolderMediaItem>()

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val mediaType = MediaUriHelper.classifyMediaType(mime, name) ?: continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val modified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                    results.add(
                        DeviceFolderMediaItem(
                            uriString = docUri.toString(),
                            displayName = name.ifBlank { docId },
                            mediaType = mediaType,
                            mimeType = mime,
                            sizeBytes = size,
                            lastModifiedMs = modified,
                        )
                    )
                }
            }
            results.sortedByDescending { it.lastModifiedMs }
        }
    }

    private fun queryTreeFolderName(treeUri: Uri): String {
        val docUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        }.getOrNull()
        if (docUri != null) {
            runCatching {
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val name = if (idx >= 0) cursor.getString(idx) else null
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?.ifBlank { "Device Folder" } ?: "Device Folder"
    }
}
