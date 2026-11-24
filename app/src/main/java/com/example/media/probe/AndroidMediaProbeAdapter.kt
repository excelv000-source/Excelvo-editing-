package com.example.media.probe

import android.content.Context
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.ports.ImportFailureReason
import com.example.core.ports.MediaImportException
import com.example.core.ports.MediaProbePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AndroidMediaProbeAdapter(
    private val context: Context,
) : MediaProbePort {

    override suspend fun probeAndCacheThumbnail(
        sourceUri: String,
        projectId: String,
    ): Result<MediaAsset> = withContext(Dispatchers.IO) {
        try {
            val assetId = UUID.randomUUID().toString()
            val resolved = MediaUriHelper.resolveSource(
                context = context,
                sourceUri = sourceUri,
                projectId = projectId,
                assetId = assetId,
            )
            val asset = when (resolved.mediaType) {
                MediaType.IMAGE -> ImageImportEngine.probeAndPrepare(context, resolved, assetId, projectId)
                MediaType.VIDEO -> VideoImportEngine.probeAndPrepare(context, resolved, assetId, projectId)
                MediaType.AUDIO -> AudioImportEngine.probeAndPrepare(context, resolved, assetId)
            }
            Result.success(asset)
        } catch (e: MediaImportException) {
            Result.failure(e)
        } catch (se: SecurityException) {
            Result.failure(
                MediaImportException(
                    ImportFailureReason.PERMISSION_DENIED,
                    "Permission denied accessing selected media.",
                    se,
                )
            )
        } catch (t: Throwable) {
            Result.failure(
                MediaImportException(
                    ImportFailureReason.DECODE_ERROR,
                    t.localizedMessage ?: "Failed to import selected media.",
                    t,
                )
            )
        }
    }

    override suspend fun checkMediaAccessibility(asset: MediaAsset): Boolean = withContext(Dispatchers.IO) {
        MediaUriHelper.isMediaAccessible(
            context = context,
            sourceUri = asset.sourceUri,
            cachedLocalPath = asset.cachedLocalPath,
        )
    }
}
