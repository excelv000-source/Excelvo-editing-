package com.example.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.engine.PresetValidationResult
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.ProjectSummary
import com.example.core.ports.ProjectStoragePort
import com.example.data.presets.LocalPresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PresetTab(val label: String) {
    ALL("All"),
    SHAKES("Shakes"),
    TRANSITIONS("Transitions"),
    EFFECTS("Effects"),
    ONE_FRAME("1 Frames"),
    CC("CCs"),
    TEXT("Text"),
    MOTION("Motion"),
    SAVED("Saved"),
    COMMUNITY("Community");

    companion object {
        val categoryCards: List<PresetTab> = listOf(
            SHAKES,
            TRANSITIONS,
            EFFECTS,
            ONE_FRAME,
            CC,
            TEXT,
            MOTION,
            SAVED,
            COMMUNITY,
        )
    }
}

data class PresetSectionUi(
    val tab: PresetTab,
    val title: String,
    val subtitle: String,
    val presets: List<PresetDefinition>,
    val isCommunitySection: Boolean = false,
)

data class PresetsUiState(
    val activeTab: PresetTab = PresetTab.ALL,
    val searchQuery: String = "",
    val presets: List<PresetDefinition> = emptyList(),
    val sections: List<PresetSectionUi> = emptyList(),
    val categoryCounts: Map<PresetTab, Int> = emptyMap(),
    val savedIds: Set<String> = emptySet(),
    val projects: List<ProjectSummary> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

class PresetsViewModel(
    private val presetRepository: LocalPresetRepository,
    private val projectStorage: ProjectStoragePort,
) : ViewModel() {

    private val activeTab = MutableStateFlow(PresetTab.ALL)
    private val searchQuery = MutableStateFlow("")
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PresetsUiState> = combine(
        activeTab,
        searchQuery,
        presetRepository.allPresets,
        presetRepository.savedPresetIds,
        projectStorage.observeProjectSummaries(),
        statusMessage,
        errorMessage,
    ) { values ->
        val tab = values[0] as PresetTab
        val query = values[1] as String
        @Suppress("UNCHECKED_CAST")
        val all = values[2] as List<PresetDefinition>
        @Suppress("UNCHECKED_CAST")
        val saved = values[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val projects = values[4] as List<ProjectSummary>
        val status = values[5] as String?
        val error = values[6] as String?

        val counts = mapOf(
            PresetTab.SHAKES to all.count { it.category == PresetCategory.SHAKES },
            PresetTab.TRANSITIONS to all.count { it.category == PresetCategory.TRANSITIONS },
            PresetTab.EFFECTS to all.count { it.category == PresetCategory.EFFECTS },
            PresetTab.ONE_FRAME to all.count { it.category == PresetCategory.ONE_FRAME },
            PresetTab.CC to all.count { it.category == PresetCategory.CC || it.category == PresetCategory.FILTERS },
            PresetTab.TEXT to all.count { it.category == PresetCategory.TEXT_ANIMATIONS },
            PresetTab.MOTION to all.count { it.category == PresetCategory.MOTION },
            PresetTab.SAVED to all.count { it.id in saved },
            PresetTab.COMMUNITY to all.count { !it.isBuiltIn },
        )

        val queryFiltered = all.filter { preset ->
            query.isBlank() ||
                preset.title.contains(query, ignoreCase = true) ||
                preset.description.contains(query, ignoreCase = true) ||
                preset.category.label.contains(query, ignoreCase = true) ||
                preset.tags.any { it.contains(query, ignoreCase = true) }
        }

        fun itemsForTab(targetTab: PresetTab): List<PresetDefinition> = when (targetTab) {
            PresetTab.ALL -> queryFiltered
            PresetTab.SHAKES -> queryFiltered.filter { it.category == PresetCategory.SHAKES }
            PresetTab.TRANSITIONS -> queryFiltered.filter { it.category == PresetCategory.TRANSITIONS }
            PresetTab.EFFECTS -> queryFiltered.filter { it.category == PresetCategory.EFFECTS }
            PresetTab.ONE_FRAME -> queryFiltered.filter { it.category == PresetCategory.ONE_FRAME }
            PresetTab.CC -> queryFiltered.filter { it.category == PresetCategory.CC || it.category == PresetCategory.FILTERS }
            PresetTab.TEXT -> queryFiltered.filter { it.category == PresetCategory.TEXT_ANIMATIONS }
            PresetTab.MOTION -> queryFiltered.filter { it.category == PresetCategory.MOTION }
            PresetTab.SAVED -> queryFiltered.filter { it.id in saved }
            PresetTab.COMMUNITY -> queryFiltered.filter { !it.isBuiltIn }
        }

        val sections = buildList {
            if (tab == PresetTab.ALL) {
                val savedItems = itemsForTab(PresetTab.SAVED)
                if (savedItems.isNotEmpty()) {
                    add(
                        PresetSectionUi(
                            tab = PresetTab.SAVED,
                            title = "Saved",
                            subtitle = "Your bookmarked studio presets",
                            presets = savedItems,
                        )
                    )
                }
                val orderedCategories = listOf(
                    Triple(PresetTab.SHAKES, "Shakes", "Kinetic beat impulses & organic drift"),
                    Triple(PresetTab.TRANSITIONS, "Transitions", "Whip slides, luma blends & prism cuts"),
                    Triple(PresetTab.EFFECTS, "Effects", "Prism pulses, halation & edge energy"),
                    Triple(PresetTab.ONE_FRAME, "1 Frames", "Single-frame beat flashes & kicks"),
                    Triple(PresetTab.CC, "CCs", "Editorial LUT-style color corrections"),
                    Triple(PresetTab.TEXT, "Text", "Kinetic typography & slate reveals"),
                    Triple(PresetTab.MOTION, "Motion", "Keyframe dolly pushes, 2.5D drift & settles"),
                )
                orderedCategories.forEach { (catTab, title, sub) ->
                    val items = itemsForTab(catTab)
                    if (items.isNotEmpty() || query.isBlank()) {
                        add(PresetSectionUi(tab = catTab, title = title, subtitle = sub, presets = items))
                    }
                }
                val communityItems = itemsForTab(PresetTab.COMMUNITY)
                add(
                    PresetSectionUi(
                        tab = PresetTab.COMMUNITY,
                        title = "From the Community",
                        subtitle = "100% free creator packs & local .excevo.json imports",
                        presets = communityItems,
                        isCommunitySection = true,
                    )
                )
            } else {
                val selectedItems = itemsForTab(tab)
                val title = if (tab == PresetTab.COMMUNITY) "From the Community" else tab.label
                add(
                    PresetSectionUi(
                        tab = tab,
                        title = title,
                        subtitle = if (tab == PresetTab.COMMUNITY) {
                            "100% free community ecosystem · Zero payments"
                        } else {
                            "${selectedItems.size} curated presets"
                        },
                        presets = selectedItems,
                        isCommunitySection = tab == PresetTab.COMMUNITY,
                    )
                )
            }
        }

        PresetsUiState(
            activeTab = tab,
            searchQuery = query,
            presets = itemsForTab(tab),
            sections = sections,
            categoryCounts = counts,
            savedIds = saved,
            projects = projects,
            statusMessage = status,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PresetsUiState(),
    )

    fun selectTab(tab: PresetTab) {
        activeTab.value = if (activeTab.value == tab && tab != PresetTab.ALL) {
            PresetTab.ALL
        } else {
            tab
        }
    }

    fun selectTabExplicit(tab: PresetTab) {
        activeTab.value = tab
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleSaved(presetId: String) {
        viewModelScope.launch {
            val isNowSaved = presetRepository.toggleSaved(presetId)
            statusMessage.value = if (isNowSaved) "Preset saved to Saved section" else "Removed from Saved"
        }
    }

    fun downloadCommunityPreset(preset: PresetDefinition) {
        viewModelScope.launch {
            presetRepository.downloadFreeCommunityPreset(preset)
                .onSuccess { message -> statusMessage.value = message }
                .onFailure { err -> errorMessage.value = err.localizedMessage ?: "Download failed." }
        }
    }

    fun applyPresetToProject(
        preset: PresetDefinition,
        projectId: String,
        intensity: Float = 1f,
    ) {
        viewModelScope.launch {
            presetRepository.applyPresetToProject(preset, projectId, intensity)
                .onSuccess { count ->
                    statusMessage.value = "Applied \"${preset.title}\" (${(intensity * 100).toInt()}%) to $count clip(s)"
                }
                .onFailure { err ->
                    errorMessage.value = err.localizedMessage ?: "Failed to apply preset."
                }
        }
    }

    fun validateAndImportPresetJson(rawJson: String, onSuccess: () -> Unit) {
        when (val result = presetRepository.validateAndAddLocalPreset(rawJson)) {
            is PresetValidationResult.Valid -> {
                statusMessage.value = "Validated & loaded \"${result.preset.title}\" (${result.preset.category.label})"
                onSuccess()
            }
            is PresetValidationResult.Invalid -> {
                errorMessage.value = result.reason
            }
        }
    }

    fun dismissMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    class Factory(
        private val presetRepository: LocalPresetRepository,
        private val projectStorage: ProjectStoragePort,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PresetsViewModel(presetRepository, projectStorage) as T
        }
    }
}
