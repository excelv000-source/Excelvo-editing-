package com.example.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.command.AddAssetClipCommand
import com.example.core.engine.SequenceDiagnosticsEngine
import com.example.core.model.AspectRatioPreset
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.ProjectSummary
import com.example.core.model.SequenceDiagnosticReport
import com.example.core.ports.MediaProbePort
import com.example.core.ports.ProjectStoragePort
import com.example.data.media.GrantedFolderItem
import com.example.data.media.MediaLibraryRepository
import com.example.data.media.RecentMediaItem
import com.example.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class ProjectSortOption(val label: String) {
    LAST_EDITED("Last Edited"),
    NAME_ASC("Name A–Z"),
    DURATION_DESC("Longest First"),
}

enum class ProjectViewMode {
    GRID,
    LIST,
}

data class ProjectHubUiState(
    val allProjects: List<ProjectSummary> = emptyList(),
    val filteredProjects: List<ProjectSummary> = emptyList(),
    val recentProjects: List<ProjectSummary> = emptyList(),
    val searchQuery: String = "",
    val sortOption: ProjectSortOption = ProjectSortOption.LAST_EDITED,
    val aspectFilter: AspectRatioPreset? = null,
    val viewMode: ProjectViewMode = ProjectViewMode.GRID,
    val isLoading: Boolean = true,
    val isCreatingComposition: Boolean = false,
    val errorMessage: String? = null,
    val diagnosticReport: SequenceDiagnosticReport? = null,
    val recentMedia: List<RecentMediaItem> = emptyList(),
    val grantedFolders: List<GrantedFolderItem> = emptyList(),
)

class ProjectHubViewModel(
    private val projectStorage: ProjectStoragePort,
    private val preferencesRepository: UserPreferencesRepository? = null,
    private val mediaProbe: MediaProbePort? = null,
    val mediaLibraryRepository: MediaLibraryRepository? = null,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(ProjectSortOption.LAST_EDITED)
    private val aspectFilter = MutableStateFlow<AspectRatioPreset?>(null)
    private val viewMode = MutableStateFlow(ProjectViewMode.GRID)
    private val isCreatingComposition = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val diagnosticReport = MutableStateFlow<SequenceDiagnosticReport?>(null)

    private val rawProjects = projectStorage.observeProjectSummaries()
        .catch { err ->
            errorMessage.value = err.localizedMessage ?: "Failed to load projects."
            emit(emptyList())
        }

    private val recentMediaFlow = mediaLibraryRepository?.observeRecentMedia()
        ?: flowOf(emptyList())
    private val grantedFoldersFlow = mediaLibraryRepository?.observeGrantedFolders()
        ?: flowOf(emptyList())

    val uiState: StateFlow<ProjectHubUiState> = combine(
        rawProjects,
        searchQuery,
        sortOption,
        aspectFilter,
        viewMode,
        isCreatingComposition,
        errorMessage,
        diagnosticReport,
        recentMediaFlow,
        grantedFoldersFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val projects = values[0] as List<ProjectSummary>
        val query = values[1] as String
        val sort = values[2] as ProjectSortOption
        val aspect = values[3] as AspectRatioPreset?
        val mode = values[4] as ProjectViewMode
        val creating = values[5] as Boolean
        val error = values[6] as String?
        val report = values[7] as SequenceDiagnosticReport?
        @Suppress("UNCHECKED_CAST")
        val recentMedia = values[8] as List<RecentMediaItem>
        @Suppress("UNCHECKED_CAST")
        val folders = values[9] as List<GrantedFolderItem>

        val filtered = projects
            .filter { item ->
                val matchesQuery = query.isBlank() || item.name.contains(query.trim(), ignoreCase = true)
                val matchesAspect = aspect == null || item.aspectRatio == aspect
                matchesQuery && matchesAspect
            }
            .let { list ->
                when (sort) {
                    ProjectSortOption.LAST_EDITED -> list.sortedByDescending { it.updatedAtMs }
                    ProjectSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                    ProjectSortOption.DURATION_DESC -> list.sortedByDescending { it.durationUs }
                }
            }

        ProjectHubUiState(
            allProjects = projects,
            filteredProjects = filtered,
            recentProjects = projects.sortedByDescending { it.updatedAtMs }.take(6),
            searchQuery = query,
            sortOption = sort,
            aspectFilter = aspect,
            viewMode = mode,
            isLoading = false,
            isCreatingComposition = creating,
            errorMessage = error,
            diagnosticReport = report,
            recentMedia = recentMedia,
            grantedFolders = folders,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectHubUiState(),
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSortOption(option: ProjectSortOption) {
        sortOption.value = option
    }

    fun setAspectFilter(aspect: AspectRatioPreset?) {
        aspectFilter.value = aspect
    }

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == ProjectViewMode.GRID) ProjectViewMode.LIST else ProjectViewMode.GRID
    }

    fun createProject(
        name: String,
        aspectRatio: AspectRatioPreset,
        frameRate: FrameRatePreset,
        targetResolution: ExportResolutionPreset = ExportResolutionPreset.FHD_1080P,
        scaleMode: com.example.core.model.CanvasScaleMode = com.example.core.model.CanvasScaleMode.FIT,
        backgroundId: String = com.example.core.model.CanvasBackgroundSpec.PURE_BLACK.id,
        customWidthPx: Int? = null,
        customHeightPx: Int? = null,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            isCreatingComposition.value = true
            runCatching {
                projectStorage.createProject(
                    name = name,
                    aspectRatio = aspectRatio,
                    frameRate = frameRate,
                    targetResolution = targetResolution,
                    scaleMode = scaleMode,
                    backgroundId = backgroundId,
                    customWidthPx = customWidthPx,
                    customHeightPx = customHeightPx,
                )
            }.onSuccess { created ->
                isCreatingComposition.value = false
                onCreated(created.id)
            }.onFailure { throwable ->
                isCreatingComposition.value = false
                errorMessage.value = throwable.localizedMessage ?: "Failed to create project."
            }
        }
    }

    fun createBlankProject(onCreated: (String) -> Unit) {
        createProject(
            name = "Blank Sequence",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_30,
            targetResolution = ExportResolutionPreset.FHD_1080P,
            onCreated = onCreated,
        )
    }

    fun createProjectWithMedia(
        uriStrings: List<String>,
        defaultTitle: String,
        aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_9_16,
        frameRate: FrameRatePreset = FrameRatePreset.FPS_30,
        targetResolution: ExportResolutionPreset = ExportResolutionPreset.FHD_1080P,
        scaleMode: com.example.core.model.CanvasScaleMode = com.example.core.model.CanvasScaleMode.FIT,
        backgroundId: String = com.example.core.model.CanvasBackgroundSpec.PURE_BLACK.id,
        customWidthPx: Int? = null,
        customHeightPx: Int? = null,
        onCreated: (String) -> Unit,
    ) {
        if (uriStrings.isEmpty()) return
        val probe = mediaProbe
        if (probe == null) {
            errorMessage.value = "Media probe adapter is unavailable."
            return
        }
        viewModelScope.launch {
            isCreatingComposition.value = true
            runCatching {
                var project = projectStorage.createProject(
                    name = defaultTitle,
                    aspectRatio = aspectRatio,
                    frameRate = frameRate,
                    targetResolution = targetResolution,
                    scaleMode = scaleMode,
                    backgroundId = backgroundId,
                    customWidthPx = customWidthPx,
                    customHeightPx = customHeightPx,
                )
                var importedCount = 0
                var firstFailure: Throwable? = null
                uriStrings.distinct().forEach { uri ->
                    val assetResult = probe.probeAndCacheThumbnail(uri, project.id)
                    val asset = assetResult.getOrNull()
                    if (asset != null) {
                        mediaLibraryRepository?.recordImportedAsset(asset)
                        val cmd = AddAssetClipCommand(
                            asset = asset,
                            clipId = UUID.randomUUID().toString(),
                        )
                        project = cmd.apply(project, System.currentTimeMillis())
                        importedCount++
                    } else if (firstFailure == null) {
                        firstFailure = assetResult.exceptionOrNull()
                    }
                }
                if (importedCount == 0) {
                    projectStorage.deleteProject(project.id)
                    throw firstFailure ?: IllegalStateException("Selected media could not be opened.")
                }
                projectStorage.saveProject(project)
                project.id
            }.onSuccess { createdId ->
                isCreatingComposition.value = false
                onCreated(createdId)
            }.onFailure { throwable ->
                isCreatingComposition.value = false
                errorMessage.value = throwable.localizedMessage ?: "Failed to import media into new sequence."
            }
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            runCatching {
                projectStorage.renameProject(projectId, newName)
            }.onFailure { throwable ->
                errorMessage.value = throwable.localizedMessage ?: "Failed to rename project."
            }
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            runCatching {
                projectStorage.duplicateProject(projectId)
            }.onFailure { throwable ->
                errorMessage.value = throwable.localizedMessage ?: "Failed to duplicate project."
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            runCatching {
                projectStorage.deleteProject(projectId)
            }.onFailure { throwable ->
                errorMessage.value = throwable.localizedMessage ?: "Failed to delete project."
            }
        }
    }

    fun analyzeProjectSequence(projectId: String) {
        viewModelScope.launch {
            runCatching {
                val project = projectStorage.loadProject(projectId)
                    ?: error("Selected project could not be loaded.")
                SequenceDiagnosticsEngine.analyze(project)
            }.onSuccess { report ->
                diagnosticReport.value = report
                preferencesRepository?.incrementAiUsage()
            }.onFailure { err ->
                errorMessage.value = err.localizedMessage ?: "Sequence analysis failed."
            }
        }
    }

    fun dismissDiagnosticReport() {
        diagnosticReport.value = null
    }

    fun dismissError() {
        errorMessage.value = null
    }

    class Factory(
        private val projectStorage: ProjectStoragePort,
        private val preferencesRepository: UserPreferencesRepository? = null,
        private val mediaProbe: MediaProbePort? = null,
        private val mediaLibraryRepository: MediaLibraryRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectHubViewModel(
                projectStorage = projectStorage,
                preferencesRepository = preferencesRepository,
                mediaProbe = mediaProbe,
                mediaLibraryRepository = mediaLibraryRepository,
            ) as T
        }
    }
}
