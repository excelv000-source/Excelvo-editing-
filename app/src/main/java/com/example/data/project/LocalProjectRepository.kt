package com.example.data.project

import android.content.Context
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.ProjectSummary
import com.example.core.model.TimelineProject
import com.example.core.ports.ProjectStoragePort
import com.example.data.db.ProjectDao
import com.example.data.db.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LocalProjectRepository(
    context: Context,
    private val projectDao: ProjectDao,
) : ProjectStoragePort {

    private val projectsRootDir: File = File(context.filesDir, "excevo_projects").apply {
        if (!exists()) mkdirs()
    }

    override fun observeProjectSummaries(): Flow<List<ProjectSummary>> =
        projectDao.observeAll().map { entities ->
            entities.map { entity ->
                ProjectSummary(
                    id = entity.id,
                    name = entity.name,
                    aspectRatio = AspectRatioPreset.fromId(entity.aspectRatioId),
                    frameRate = FrameRatePreset.fromFps(entity.frameRateFps),
                    durationUs = entity.durationUs,
                    clipCount = entity.clipCount,
                    thumbnailPath = entity.thumbnailPath,
                    updatedAtMs = entity.updatedAtMs,
                )
            }
        }

    override suspend fun createProject(
        name: String,
        aspectRatio: AspectRatioPreset,
        frameRate: FrameRatePreset,
        targetResolution: ExportResolutionPreset,
        scaleMode: com.example.core.model.CanvasScaleMode,
        backgroundId: String,
        customWidthPx: Int?,
        customHeightPx: Int?,
    ): TimelineProject = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val cleanName = name.trim().ifEmpty { "Untitled Sequence" }
        val project = TimelineProject(
            id = id,
            name = cleanName,
            aspectRatio = aspectRatio,
            frameRate = frameRate,
            targetResolution = targetResolution,
            scaleMode = scaleMode,
            backgroundId = backgroundId,
            customWidthPx = customWidthPx?.takeIf { it >= 120 },
            customHeightPx = customHeightPx?.takeIf { it >= 120 },
            tracks = TimelineProject.defaultTracks(),
            assets = emptyMap(),
            createdAtMs = now,
            updatedAtMs = now,
        )
        saveProject(project)
        project
    }

    override suspend fun loadProject(projectId: String): TimelineProject? = withContext(Dispatchers.IO) {
        val entity = projectDao.getById(projectId)
        val docFile = if (entity != null) {
            File(entity.documentPath)
        } else {
            projectJsonFile(projectId)
        }
        if (!docFile.exists()) return@withContext null
        runCatching {
            ProjectJsonCodec.decode(docFile.readText())
        }.getOrNull()
    }

    override suspend fun saveProject(project: TimelineProject) = withContext(Dispatchers.IO) {
        val docFile = projectJsonFile(project.id)
        docFile.parentFile?.mkdirs()
        val tmpFile = File(docFile.parentFile, "project.json.tmp")
        tmpFile.writeText(ProjectJsonCodec.encode(project))
        if (!tmpFile.renameTo(docFile)) {
            docFile.writeText(tmpFile.readText())
            tmpFile.delete()
        }
        projectDao.upsert(
            ProjectEntity(
                id = project.id,
                name = project.name,
                aspectRatioId = project.aspectRatio.id,
                frameRateFps = project.frameRate.fps,
                durationUs = project.totalDurationUs(),
                clipCount = project.clipCount(),
                thumbnailPath = project.primaryThumbnailPath(),
                documentPath = docFile.absolutePath,
                createdAtMs = project.createdAtMs,
                updatedAtMs = project.updatedAtMs,
            )
        )
    }

    override suspend fun renameProject(projectId: String, newName: String) = withContext(Dispatchers.IO) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val loaded = loadProject(projectId)
        if (loaded != null) {
            saveProject(loaded.copy(name = cleanName, updatedAtMs = now))
        } else {
            projectDao.rename(projectId, cleanName, now)
        }
    }

    override suspend fun duplicateProject(projectId: String): TimelineProject? = withContext(Dispatchers.IO) {
        val source = loadProject(projectId) ?: return@withContext null
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val duplicated = source.copy(
            id = newId,
            name = "${source.name} Copy",
            createdAtMs = now,
            updatedAtMs = now,
        )
        saveProject(duplicated)
        duplicated
    }

    override suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.deleteById(projectId)
        val folder = File(projectsRootDir, projectId)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }

    private fun projectJsonFile(projectId: String): File =
        File(File(projectsRootDir, projectId), "project.json")
}
