package com.example.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.LibraryAssetItem
import com.example.core.model.LibraryItemType
import com.example.core.model.ProxyQuality
import com.example.core.model.StorageStats
import com.example.core.model.ThemeIntensity
import com.example.core.model.UserPreferences
import com.example.core.ports.ProjectStoragePort
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.presets.LocalPresetRepository
import com.example.data.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val preferences: UserPreferences = UserPreferences(),
    val projectCount: Int = 0,
    val totalClipCount: Int = 0,
    val savedPresetsCount: Int = 0,
    val libraryItems: List<LibraryAssetItem> = emptyList(),
    val storageStats: StorageStats = StorageStats(0L, 0L, 0L, 0L),
    val isClearingCache: Boolean = false,
    val isImportingAsset: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

class ProfileViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val storageManager: StorageManager,
    private val projectStorage: ProjectStoragePort,
    private val presetRepository: LocalPresetRepository,
) : ViewModel() {

    private val storageStatsFlow = MutableStateFlow(StorageStats(0L, 0L, 0L, 0L))
    private val isClearingCache = MutableStateFlow(false)
    private val isImportingAsset = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    init {
        refreshStorageStats()
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesRepository.preferences,
        projectStorage.observeProjectSummaries(),
        presetRepository.savedPresetIds,
        storageManager.observeLibraryItems(),
        storageStatsFlow,
        isClearingCache,
        isImportingAsset,
        statusMessage,
        errorMessage,
    ) { values ->
        val prefs = values[0] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val projects = values[1] as List<com.example.core.model.ProjectSummary>
        @Suppress("UNCHECKED_CAST")
        val savedIds = values[2] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val library = values[3] as List<LibraryAssetItem>
        val stats = values[4] as StorageStats
        val clearing = values[5] as Boolean
        val importing = values[6] as Boolean
        val status = values[7] as String?
        val error = values[8] as String?

        ProfileUiState(
            preferences = prefs,
            projectCount = projects.size,
            totalClipCount = projects.sumOf { it.clipCount },
            savedPresetsCount = savedIds.size,
            libraryItems = library,
            storageStats = stats,
            isClearingCache = clearing,
            isImportingAsset = importing,
            statusMessage = status,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    fun refreshStorageStats() {
        viewModelScope.launch {
            runCatching { storageManager.computeStorageStats() }
                .onSuccess { storageStatsFlow.value = it }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            isClearingCache.value = true
            runCatching { storageManager.clearCache() }
                .onSuccess { updated ->
                    storageStatsFlow.value = updated
                    statusMessage.value = "Temporary cache cleared"
                }
                .onFailure { err ->
                    errorMessage.value = err.localizedMessage ?: "Failed to clear cache."
                }
            isClearingCache.value = false
        }
    }

    fun setThemeIntensity(intensity: ThemeIntensity) {
        viewModelScope.launch {
            preferencesRepository.setThemeIntensity(intensity)
            statusMessage.value = "Theme switched to ${intensity.label}"
        }
    }

    fun setWorkspacePreset(preset: com.example.core.model.WorkspacePreset) {
        viewModelScope.launch {
            preferencesRepository.setWorkspacePreset(preset)
            statusMessage.value = "Default workspace set to ${preset.label}"
        }
    }

    fun setProxyQuality(quality: ProxyQuality) {
        viewModelScope.launch {
            preferencesRepository.setProxyQuality(quality)
        }
    }

    fun setHardwareDecode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHardwareDecode(enabled)
        }
    }

    fun setTimelineSnap(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTimelineSnap(enabled)
        }
    }

    fun setScrubCacheLimitMb(limitMb: Int) {
        viewModelScope.launch {
            preferencesRepository.setScrubCacheLimitMb(limitMb)
        }
    }

    fun importLibraryAsset(uriString: String, itemType: LibraryItemType) {
        viewModelScope.launch {
            isImportingAsset.value = true
            storageManager.importLibraryAsset(uriString, itemType)
                .onSuccess { item ->
                    storageStatsFlow.value = storageManager.computeStorageStats()
                    statusMessage.value = "Added \"${item.name}\" to EXCEVO Library"
                }
                .onFailure { err ->
                    errorMessage.value = err.localizedMessage ?: "Failed to import library item."
                }
            isImportingAsset.value = false
        }
    }

    fun deleteLibraryItem(item: LibraryAssetItem) {
        viewModelScope.launch {
            runCatching {
                storageManager.deleteLibraryItem(item)
                storageManager.computeStorageStats()
            }.onSuccess { stats ->
                storageStatsFlow.value = stats
                statusMessage.value = "Removed \"${item.name}\" from EXCEVO Library"
            }.onFailure { err ->
                errorMessage.value = err.localizedMessage ?: "Failed to remove item."
            }
        }
    }

    fun dismissMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    class Factory(
        private val preferencesRepository: UserPreferencesRepository,
        private val storageManager: StorageManager,
        private val projectStorage: ProjectStoragePort,
        private val presetRepository: LocalPresetRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(
                preferencesRepository = preferencesRepository,
                storageManager = storageManager,
                projectStorage = projectStorage,
                presetRepository = presetRepository,
            ) as T
        }
    }
}
