package com.example.ui.editor

import android.view.TextureView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.command.AddAssetClipCommand
import com.example.core.command.AddTrackCommand
import com.example.core.command.ApplyPresetCommand
import com.example.core.command.CommandHistoryManager
import com.example.core.command.DeleteClipCommand
import com.example.core.command.DuplicateClipCommand
import com.example.core.command.EditCommand
import com.example.core.command.HistorySnapshot
import com.example.core.command.MoveClipOrderCommand
import com.example.core.command.MoveClipToTrackCommand
import com.example.core.command.RemoveKeyframeCommand
import com.example.core.command.RemoveTrackCommand
import com.example.core.command.ResetClipAllCommand
import com.example.core.command.SplitClipCommand
import com.example.core.command.ToggleGroupClipCommand
import com.example.core.command.ToggleTrackVisibilityCommand
import com.example.core.command.TrimClipCommand
import com.example.core.command.UpdateClipAudioCommand
import com.example.core.command.UpdateClipAudioEnvelopeCommand
import com.example.core.command.UpdateClipBlendCommand
import com.example.core.command.UpdateClipColorGradeCommand
import com.example.core.command.UpdateClipEffectStackCommand
import com.example.core.command.UpdateClipSpeedCommand
import com.example.core.command.UpdateClipTransformCommand
import com.example.core.command.UpdateKeyframeEasingCommand
import com.example.core.command.UpdateProjectSettingsCommand
import com.example.core.command.UpsertKeyframeCommand
import com.example.core.engine.TimelineEvaluator
import com.example.core.engine.TimelineMath
import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.BlendModeType
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.Keyframe
import com.example.core.model.PresetCategory
import com.example.core.model.PresetDefinition
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TrackType
import com.example.core.model.TransformSpec
import com.example.core.model.VisualEffectType
import com.example.core.model.WorkspacePreset
import com.example.core.ports.GpuCapabilities
import com.example.core.ports.MediaImportProgress
import com.example.core.ports.MediaProbePort
import com.example.core.ports.ProjectStoragePort
import com.example.core.ports.SequencePlaybackPort
import com.example.data.media.MediaLibraryRepository
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.presets.LocalPresetRepository
import com.example.media.playback.AndroidSequencePlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class StudioToolTab(val label: String) {
    EDIT("Edit"),
    TRANSFORM("Motion"),
    EFFECTS("Effects"),
    ADJUST("Adjust"),
    BLEND("Blend"),
    AUDIO("Audio"),
    FILTERS("Filters"),
    PRESETS("Presets");

    companion object {
        fun orderedForWorkspace(workspace: WorkspacePreset): List<StudioToolTab> = when (workspace) {
            WorkspacePreset.EXCEVO_STUDIO -> listOf(
                EDIT, TRANSFORM, EFFECTS, ADJUST, BLEND, AUDIO, FILTERS, PRESETS
            )
            WorkspacePreset.ALIGHT_MOTION_INSPIRED -> listOf(
                TRANSFORM, BLEND, EFFECTS, EDIT, ADJUST, FILTERS, AUDIO, PRESETS
            )
            WorkspacePreset.CAPCUT_INSPIRED -> listOf(
                EDIT, AUDIO, FILTERS, ADJUST, EFFECTS, TRANSFORM, BLEND, PRESETS
            )
            WorkspacePreset.NOVA_INSPIRED -> listOf(
                PRESETS, EFFECTS, TRANSFORM, ADJUST, FILTERS, BLEND, AUDIO, EDIT
            )
        }
    }
}

enum class ColorAdjustParam(val label: String, val range: ClosedFloatingPointRange<Float>) {
    BRIGHTNESS("Brightness", -1f..1f),
    CONTRAST("Contrast", -1f..1f),
    SATURATION("Saturation", -1f..1f),
    EXPOSURE("Exposure", -1f..1f),
    TEMPERATURE("Temperature", -1f..1f),
    TINT("Tint", -1f..1f),
    HIGHLIGHTS("Highlights", -1f..1f),
    SHADOWS("Shadows", -1f..1f),
    SHARPNESS("Sharpness", 0f..1f),
}

enum class VisualEffectParam(val label: String, val range: ClosedFloatingPointRange<Float>) {
    BLUR("Blur", 0f..1f),
    GLOW("Glow", 0f..1f),
    VIGNETTE("Vignette", 0f..1f),
    GRAYSCALE("Grayscale", 0f..1f),
    SEPIA("Sepia", 0f..1f),
}

enum class LayerMenuPanel(val title: String) {
    MOVE_TRANSFORM("Move & Transform"),
    EFFECTS("Effects"),
    COLOR_FILL("Color & Fill"),
    BLENDING("Blending"),
    OPACITY("Opacity"),
    KEYFRAMES("Keyframes"),
    PRESETS("Presets"),
}

data class StudioUiState(
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: MediaImportProgress? = null,
    val project: TimelineProject? = null,
    val playheadUs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val selectedClipId: String? = null,
    val isLayerMenuVisible: Boolean = false,
    val focusedLayerPanel: LayerMenuPanel? = null,
    val pxPerSecond: Float = 56f,
    val showSafeGuides: Boolean = true,
    val isCleanPreview: Boolean = false,
    val activeToolTab: StudioToolTab = StudioToolTab.EDIT,
    val workspacePreset: WorkspacePreset = WorkspacePreset.EXCEVO_STUDIO,
    val gpuCapabilities: GpuCapabilities = GpuCapabilities(),
    val isUsingGpuEffects: Boolean = false,
    val playbackError: String? = null,
    val presetsCatalog: List<PresetDefinition> = emptyList(),
    val liveGradeOverride: ColorGradeSpec? = null,
    val liveTransformOverride: TransformSpec? = null,
    val liveEffectStackOverride: List<ClipEffectInstance>? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lastSavedAtMs: Long? = null,
    val missingAssetNames: List<String> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val selectedClip: TimelineClip?
        get() = project?.allClips()?.firstOrNull { it.id == selectedClipId }?.let { clip ->
            clip.copy(
                colorGrade = liveGradeOverride ?: clip.colorGrade,
                transform = liveTransformOverride ?: clip.transform,
                effectStack = liveEffectStackOverride ?: clip.effectStack,
            )
        }

    val activePrimaryClip: TimelineClip?
        get() = project?.let { proj ->
            val track = proj.primaryVideoTrack()
            if (track.isHidden) null
            else TimelineMath.findActiveClipAt(track, playheadUs)?.let { clip ->
                if (clip.id == selectedClipId) {
                    clip.copy(
                        colorGrade = liveGradeOverride ?: clip.colorGrade,
                        transform = liveTransformOverride ?: clip.transform,
                        effectStack = liveEffectStackOverride ?: clip.effectStack,
                    )
                } else {
                    clip
                }
            }
        }

    val activeOverlayClips: List<TimelineClip>
        get() = project?.tracks
            ?.filter { it.type == TrackType.OVERLAY || it.type == TrackType.TEXT }
            ?.mapNotNull { track ->
                if (track.isHidden) null
                else TimelineMath.findActiveClipAt(track, playheadUs)?.let { clip ->
                    if (clip.id == selectedClipId) {
                        clip.copy(
                            colorGrade = liveGradeOverride ?: clip.colorGrade,
                            transform = liveTransformOverride ?: clip.transform,
                            effectStack = liveEffectStackOverride ?: clip.effectStack,
                        )
                    } else {
                        clip
                    }
                }
            }
            .orEmpty()
}

class StudioViewModel(
    private val projectId: String,
    private val projectStorage: ProjectStoragePort,
    private val mediaProbe: MediaProbePort,
    private val presetRepository: LocalPresetRepository? = null,
    private val playbackPort: SequencePlaybackPort? = null,
    val mediaLibraryRepository: MediaLibraryRepository? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
) : ViewModel() {

    private var historyManager: CommandHistoryManager? = null
    private var playbackJob: Job? = null
    private val inFlightImportUris = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(
        StudioUiState(
            gpuCapabilities = playbackPort?.gpuCapabilities ?: GpuCapabilities(),
            isUsingGpuEffects = playbackPort?.gpuCapabilities?.useHardwareGpuPipeline ?: false,
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        loadProject()
        observePlaybackStatus()
        observePresetsCatalog()
        observeWorkspacePreference()
    }

    private fun observeWorkspacePreference() {
        val prefs = userPreferencesRepository ?: return
        viewModelScope.launch {
            prefs.preferences.collectLatest { userPrefs ->
                _uiState.update { it.copy(workspacePreset = userPrefs.workspacePreset) }
            }
        }
    }

    private fun observePlaybackStatus() {
        val port = playbackPort ?: return
        viewModelScope.launch {
            port.status.collectLatest { st ->
                _uiState.update { state ->
                    state.copy(
                        playheadUs = st.playheadUs,
                        isPlaying = st.isPlaying,
                        isBuffering = st.isBuffering,
                        isUsingGpuEffects = st.isUsingGpuEffects,
                        playbackError = st.playbackError,
                    )
                }
            }
        }
    }

    private fun observePresetsCatalog() {
        val repo = presetRepository ?: return
        viewModelScope.launch {
            repo.allPresets.collectLatest { list ->
                _uiState.update { it.copy(presetsCatalog = list) }
            }
        }
    }

    private fun loadProject() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val loaded = projectStorage.loadProject(projectId)
            if (loaded == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Project could not be found on local storage.",
                    )
                }
                return@launch
            }
            val missing = mutableListOf<String>()
            val checkedAssets = loaded.assets.mapValues { (_, asset) ->
                val ok = runCatching { mediaProbe.checkMediaAccessibility(asset) }.getOrDefault(true)
                if (!ok) missing.add(asset.displayName)
                asset.copy(isAccessible = ok)
            }
            val verifiedProject = if (checkedAssets != loaded.assets) {
                loaded.copy(assets = checkedAssets)
            } else {
                loaded
            }
            val manager = CommandHistoryManager(verifiedProject)
            historyManager = manager
            val snap = manager.snapshot()
            val firstClipId = snap.project.primaryVideoTrack().clips.firstOrNull()?.id
                ?: snap.project.allClips().firstOrNull()?.id
            _uiState.update {
                it.copy(
                    isLoading = false,
                    project = snap.project,
                    canUndo = snap.canUndo,
                    canRedo = snap.canRedo,
                    selectedClipId = firstClipId,
                    lastSavedAtMs = snap.project.updatedAtMs,
                    missingAssetNames = missing,
                )
            }
            playbackPort?.syncSequence(snap.project, 0L)
        }
    }

    fun attachVideoTextureView(textureView: TextureView?) {
        (playbackPort as? AndroidSequencePlaybackController)?.attachTextureView(textureView)
    }

    fun selectToolTab(tab: StudioToolTab) {
        _uiState.update { it.copy(activeToolTab = tab, isCleanPreview = false) }
    }

    fun setWorkspacePreset(preset: WorkspacePreset) {
        _uiState.update {
            it.copy(
                workspacePreset = preset,
                statusMessage = "Workspace: ${preset.label}",
            )
        }
        viewModelScope.launch {
            userPreferencesRepository?.setWorkspacePreset(preset)
        }
    }

    fun forceSaveProject() {
        val project = _uiState.value.project ?: return
        val now = System.currentTimeMillis()
        val toSave = project.copy(updatedAtMs = now)
        viewModelScope.launch {
            runCatching {
                projectStorage.saveProject(toSave)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        project = toSave,
                        lastSavedAtMs = now,
                        statusMessage = "Sequence saved to local storage",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Failed to save sequence.")
                }
            }
        }
    }

    fun toggleCleanPreview() {
        _uiState.update { it.copy(isCleanPreview = !it.isCleanPreview) }
    }

    fun importMediaUris(uris: List<String>) {
        val filtered = uris.distinct().filter { it.isNotBlank() && it !in inFlightImportUris }
        if (filtered.isEmpty()) return
        inFlightImportUris.addAll(filtered)
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isImporting = true,
                        importProgress = MediaImportProgress(0, filtered.size, "Preparing media..."),
                        errorMessage = null,
                    )
                }
                var addedCount = 0
                var lastNewClipId: String? = null
                val errors = mutableListOf<String>()

                filtered.forEachIndexed { idx, uri ->
                    _uiState.update {
                        it.copy(
                            importProgress = MediaImportProgress(
                                currentIndex = idx + 1,
                                totalCount = filtered.size,
                                currentLabel = uri.substringAfterLast('/').substringAfterLast(':'),
                            )
                        )
                    }
                    val result = mediaProbe.probeAndCacheThumbnail(uri, projectId)
                    result.onSuccess { asset ->
                        mediaLibraryRepository?.recordImportedAsset(asset)
                        val newClipId = UUID.randomUUID().toString()
                        executeCommand(AddAssetClipCommand(asset = asset, clipId = newClipId))
                        lastNewClipId = newClipId
                        addedCount++
                    }.onFailure { err ->
                        errors.add(err.localizedMessage ?: "Failed to probe selected media.")
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        isImporting = false,
                        importProgress = null,
                        selectedClipId = lastNewClipId ?: state.selectedClipId,
                        statusMessage = if (addedCount > 0) {
                            "Imported $addedCount ${if (addedCount == 1) "clip" else "clips"}"
                        } else {
                            state.statusMessage
                        },
                        errorMessage = errors.firstOrNull(),
                    )
                }
            } finally {
                inFlightImportUris.removeAll(filtered.toSet())
            }
        }
    }

    fun dismissMissingAssetsBanner() {
        _uiState.update { it.copy(missingAssetNames = emptyList()) }
    }

    // --- Playback & Fixed-Center Playhead Navigation ---

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val project = _uiState.value.project ?: return
        val totalUs = project.totalDurationUs()
        if (totalUs <= 0L) {
            _uiState.update { it.copy(statusMessage = "Import a clip to preview sequence timing.") }
            return
        }
        if (playbackPort != null) {
            playbackPort.syncSequence(project, _uiState.value.playheadUs)
            playbackPort.play()
            return
        }
        if (_uiState.value.playheadUs >= totalUs) {
            _uiState.update { it.copy(playheadUs = 0L) }
        }
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }
        val stepUs = project.frameRate.frameDurationUs
        val delayMs = (stepUs / 1000L).coerceAtLeast(16L)

        playbackJob = viewModelScope.launch {
            while (isActive) {
                delay(delayMs)
                val current = _uiState.value.playheadUs
                val next = current + stepUs
                if (next >= totalUs) {
                    _uiState.update { it.copy(playheadUs = totalUs, isPlaying = false) }
                    break
                } else {
                    _uiState.update { it.copy(playheadUs = next) }
                }
            }
        }
    }

    fun pausePlayback() {
        playbackPort?.pause()
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekToUs(targetUs: Long) {
        pausePlayback()
        val maxUs = _uiState.value.project?.totalDurationUs() ?: 0L
        val clamped = targetUs.coerceIn(0L, maxUs)
        playbackPort?.seekToTimelineUs(clamped)
        _uiState.update { it.copy(playheadUs = clamped) }
        _uiState.value.let { st ->
            st.project?.let { playbackPort?.updateLiveVisualState(st.activePrimaryClip, it) }
        }
    }

    fun jumpToPreviousBoundary() {
        val project = _uiState.value.project ?: return
        val target = TimelineEvaluator.findPreviousBoundaryUs(project, _uiState.value.playheadUs)
        seekToUs(target)
    }

    fun jumpToNextBoundary() {
        val project = _uiState.value.project ?: return
        val target = TimelineEvaluator.findNextBoundaryUs(project, _uiState.value.playheadUs)
        seekToUs(target)
    }

    fun stepFrame(deltaFrames: Int) {
        pausePlayback()
        val project = _uiState.value.project ?: return
        if (playbackPort != null) {
            playbackPort.stepFrames(deltaFrames)
            return
        }
        val nextUs = TimelineMath.stepFrameUs(
            currentUs = _uiState.value.playheadUs,
            deltaFrames = deltaFrames,
            fps = project.frameRate.fps,
            maxDurationUs = project.totalDurationUs(),
        )
        _uiState.update { it.copy(playheadUs = nextUs) }
    }

    fun selectClip(clipId: String?) {
        _uiState.update {
            it.copy(
                selectedClipId = clipId,
                liveGradeOverride = null,
                liveTransformOverride = null,
                liveEffectStackOverride = null,
            )
        }
        val state = _uiState.value
        val project = state.project ?: return
        playbackPort?.updateLiveVisualState(state.activePrimaryClip, project)
    }

    fun openLayerMenuForClip(clipId: String) {
        _uiState.update {
            it.copy(
                selectedClipId = clipId,
                isLayerMenuVisible = true,
                focusedLayerPanel = null,
                isCleanPreview = false,
                liveGradeOverride = null,
                liveTransformOverride = null,
                liveEffectStackOverride = null,
            )
        }
        val state = _uiState.value
        val project = state.project ?: return
        playbackPort?.updateLiveVisualState(state.activePrimaryClip, project)
    }

    fun openLayerEditingPanel(panel: LayerMenuPanel) {
        if (_uiState.value.selectedClipId == null) return
        _uiState.update {
            it.copy(
                isLayerMenuVisible = false,
                focusedLayerPanel = panel,
                isCleanPreview = false,
            )
        }
    }

    fun navigateBackFromLayerPanel() {
        _uiState.update { state ->
            if (state.focusedLayerPanel != null) {
                state.copy(
                    focusedLayerPanel = null,
                    isLayerMenuVisible = state.selectedClipId != null,
                )
            } else {
                state.copy(isLayerMenuVisible = false)
            }
        }
    }

    fun closeLayerMenu() {
        _uiState.update {
            it.copy(
                isLayerMenuVisible = false,
                focusedLayerPanel = null,
            )
        }
    }

    fun setZoom(pxPerSecond: Float) {
        _uiState.update { it.copy(pxPerSecond = pxPerSecond.coerceIn(24f, 220f)) }
    }

    fun toggleSafeGuides() {
        _uiState.update { it.copy(showSafeGuides = !it.showSafeGuides) }
    }

    // --- Real-Time Color Adjustments, Filters & Stackable Visual Effects ---

    fun previewColorGrade(grade: ColorGradeSpec) {
        val state = _uiState.value
        val clip = state.selectedClip ?: return
        val project = state.project ?: return
        _uiState.update { it.copy(liveGradeOverride = grade) }
        playbackPort?.updateLiveVisualState(clip.copy(colorGrade = grade), project)
    }

    fun commitColorGrade(grade: ColorGradeSpec, label: String = "Update color & FX") {
        val clip = _uiState.value.selectedClip ?: return
        _uiState.update { it.copy(liveGradeOverride = null) }
        executeCommand(
            UpdateClipColorGradeCommand(
                clipId = clip.id,
                colorGrade = grade,
                description = label,
            )
        )
    }

    fun resetSingleColorParam(param: ColorAdjustParam) {
        val clip = _uiState.value.selectedClip ?: return
        val g = clip.colorGrade
        val updated = when (param) {
            ColorAdjustParam.BRIGHTNESS -> g.copy(brightness = 0f)
            ColorAdjustParam.CONTRAST -> g.copy(contrast = 0f)
            ColorAdjustParam.SATURATION -> g.copy(saturation = 0f)
            ColorAdjustParam.EXPOSURE -> g.copy(exposure = 0f)
            ColorAdjustParam.TEMPERATURE -> g.copy(temperature = 0f)
            ColorAdjustParam.TINT -> g.copy(tint = 0f)
            ColorAdjustParam.HIGHLIGHTS -> g.copy(highlights = 0f)
            ColorAdjustParam.SHADOWS -> g.copy(shadows = 0f)
            ColorAdjustParam.SHARPNESS -> g.copy(sharpness = 0f)
        }
        commitColorGrade(updated, "Reset ${param.label}")
    }

    fun resetSingleEffectParam(param: VisualEffectParam) {
        val clip = _uiState.value.selectedClip ?: return
        val g = clip.colorGrade
        val updated = when (param) {
            VisualEffectParam.BLUR -> g.copy(blurRadius = 0f)
            VisualEffectParam.GLOW -> g.copy(glow = 0f)
            VisualEffectParam.VIGNETTE -> g.copy(vignette = 0f)
            VisualEffectParam.GRAYSCALE -> g.copy(grayscale = 0f)
            VisualEffectParam.SEPIA -> g.copy(sepia = 0f)
        }
        commitColorGrade(updated, "Reset ${param.label}")
    }

    fun resetAllAdjustments() {
        val clip = _uiState.value.selectedClip ?: return
        val updated = clip.colorGrade.copy(
            exposure = 0f,
            brightness = 0f,
            contrast = 0f,
            saturation = 0f,
            temperature = 0f,
            tint = 0f,
            highlights = 0f,
            shadows = 0f,
            sharpness = 0f,
        )
        commitColorGrade(updated, "Reset all adjustments")
        _uiState.update { it.copy(statusMessage = "Adjustments reset") }
    }

    fun resetVisualEffects() {
        val clip = _uiState.value.selectedClip ?: return
        val updatedGrade = clip.colorGrade.copy(
            blurRadius = 0f,
            glow = 0f,
            vignette = 0f,
            grayscale = 0f,
            sepia = 0f,
            sharpness = 0f,
        )
        _uiState.update { it.copy(liveGradeOverride = null, liveEffectStackOverride = null) }
        executeCommand(
            UpdateClipEffectStackCommand(
                clipId = clip.id,
                effectStack = emptyList(),
                colorGrade = updatedGrade,
                description = "Reset visual effects",
            )
        )
        _uiState.update { it.copy(statusMessage = "Visual effects reset") }
    }

    fun addEffectToStack(type: VisualEffectType) {
        val clip = _uiState.value.selectedClip ?: return
        val newFx = ClipEffectInstance(
            id = "fx_${UUID.randomUUID().toString().take(8)}",
            type = type,
            enabled = true,
            intensity = type.defaultIntensity,
        )
        val updated = clip.effectStack + newFx
        executeCommand(
            UpdateClipEffectStackCommand(
                clipId = clip.id,
                effectStack = updated,
                description = "Add ${type.label}",
            )
        )
    }

    fun toggleEffectEnabled(effectId: String) {
        val clip = _uiState.value.selectedClip ?: return
        val updated = clip.effectStack.map { if (it.id == effectId) it.copy(enabled = !it.enabled) else it }
        executeCommand(UpdateClipEffectStackCommand(clipId = clip.id, effectStack = updated, description = "Toggle effect"))
    }

    fun moveEffectInStack(effectId: String, delta: Int) {
        val clip = _uiState.value.selectedClip ?: return
        val idx = clip.effectStack.indexOfFirst { it.id == effectId }
        if (idx == -1) return
        val target = (idx + delta).coerceIn(0, clip.effectStack.lastIndex)
        if (target == idx) return
        val mutable = clip.effectStack.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(target, item)
        executeCommand(UpdateClipEffectStackCommand(clipId = clip.id, effectStack = mutable, description = "Reorder effect"))
    }

    fun removeEffectFromStack(effectId: String) {
        val clip = _uiState.value.selectedClip ?: return
        val updated = clip.effectStack.filterNot { it.id == effectId }
        executeCommand(UpdateClipEffectStackCommand(clipId = clip.id, effectStack = updated, description = "Remove effect"))
    }

    fun previewEffectStack(stack: List<ClipEffectInstance>) {
        val state = _uiState.value
        val clip = state.selectedClip ?: return
        val project = state.project ?: return
        _uiState.update { it.copy(liveEffectStackOverride = stack) }
        playbackPort?.updateLiveVisualState(clip.copy(effectStack = stack), project)
    }

    fun commitEffectStack(stack: List<ClipEffectInstance>, label: String = "Update effect intensity") {
        val clip = _uiState.value.selectedClip ?: return
        _uiState.update { it.copy(liveEffectStackOverride = null) }
        executeCommand(UpdateClipEffectStackCommand(clipId = clip.id, effectStack = stack, description = label))
    }

    fun applyColorFilter(filterId: String, intensity: Float = 1f) {
        val clip = _uiState.value.selectedClip ?: return
        val updated = clip.colorGrade.copy(
            filterPresetId = if (filterId == "original") null else filterId,
            filterIntensity = intensity.coerceIn(0f, 1f),
        )
        commitColorGrade(updated, "Apply filter $filterId")
    }

    // --- Real-Time Transform, Keyframes, Crop, Flip, Speed & Blend ---

    fun previewTransform(transform: TransformSpec) {
        val state = _uiState.value
        val clip = state.selectedClip ?: return
        val project = state.project ?: return
        _uiState.update { it.copy(liveTransformOverride = transform) }
        playbackPort?.updateLiveVisualState(clip.copy(transform = transform), project)
    }

    fun commitTransform(transform: TransformSpec, label: String = "Transform clip") {
        val clip = _uiState.value.selectedClip ?: return
        _uiState.update { it.copy(liveTransformOverride = null) }
        executeCommand(
            UpdateClipTransformCommand(
                clipId = clip.id,
                transform = transform,
                description = label,
            )
        )
    }

    fun rotateClip90Deg() {
        val clip = _uiState.value.selectedClip ?: return
        val nextRot = ((clip.transform.rotationDeg + 90f) % 360f).let {
            if (it > 180f) it - 360f else it
        }
        commitTransform(clip.transform.copy(rotationDeg = nextRot), "Rotate 90°")
    }

    fun resetTransform() {
        val clip = _uiState.value.selectedClip ?: return
        commitTransform(TransformSpec(opacity = clip.transform.opacity), "Reset transform")
        _uiState.update { it.copy(statusMessage = "Motion & transform reset") }
    }

    fun setClipBlend(blendMode: BlendModeType, opacity: Float) {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(UpdateClipBlendCommand(clip.id, blendMode, opacity))
    }

    fun setClipSpeed(speedMultiplier: Float) {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(UpdateClipSpeedCommand(clip.id, speedMultiplier))
    }

    fun toggleKeyframeAtPlayhead(property: AnimatableProperty, value: Float) {
        val clip = _uiState.value.selectedClip ?: return
        val localOffsetUs = (_uiState.value.playheadUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
        val existing = TimelineEvaluator.findKeyframeNear(clip, property, localOffsetUs)
        if (existing != null) {
            executeCommand(RemoveKeyframeCommand(clip.id, property, existing.id))
        } else {
            executeCommand(
                UpsertKeyframeCommand(
                    clipId = clip.id,
                    property = property,
                    keyframe = Keyframe(
                        id = "kf_${UUID.randomUUID().toString().take(8)}",
                        timeOffsetUs = localOffsetUs,
                        value = value,
                        easing = EasingType.EASE_IN_OUT,
                    ),
                )
            )
        }
    }

    fun jumpToAdjacentKeyframe(property: AnimatableProperty, forward: Boolean) {
        val clip = _uiState.value.selectedClip ?: return
        val localNow = (_uiState.value.playheadUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
        val sorted = clip.keyframes[property].orEmpty().sortedBy { it.timeOffsetUs }
        val targetKf = if (forward) {
            sorted.firstOrNull { it.timeOffsetUs > localNow + 20_000L }
        } else {
            sorted.lastOrNull { it.timeOffsetUs < (localNow - 20_000L).coerceAtLeast(0L) }
        }
        if (targetKf != null) {
            seekToUs(clip.timelineStartUs + targetKf.timeOffsetUs)
        } else {
            _uiState.update { it.copy(statusMessage = "No ${if (forward) "next" else "previous"} ${property.label} keyframe") }
        }
    }

    fun setKeyframeEasing(property: AnimatableProperty, keyframeId: String, easing: EasingType) {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(UpdateKeyframeEasingCommand(clip.id, property, keyframeId, easing))
    }

    fun removeKeyframe(property: AnimatableProperty, keyframeId: String) {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(RemoveKeyframeCommand(clip.id, property, keyframeId))
    }

    fun setCanvasScaleMode(scaleMode: CanvasScaleMode) {
        val project = _uiState.value.project ?: return
        if (project.scaleMode == scaleMode) return
        executeCommand(
            UpdateProjectSettingsCommand(
                name = project.name,
                aspectRatio = project.aspectRatio,
                frameRate = project.frameRate,
                targetResolution = project.targetResolution,
                scaleMode = scaleMode,
                backgroundId = project.backgroundId,
            )
        )
    }

    // --- Audio Studio Controls ---

    fun setClipAudio(volume: Float, isMuted: Boolean) {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(
            UpdateClipAudioCommand(
                clipId = clip.id,
                volume = volume,
                isMuted = isMuted,
            )
        )
    }

    fun setClipAudioEnvelope(volume: Float, isMuted: Boolean, fadeInUs: Long, fadeOutUs: Long) {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(
            UpdateClipAudioEnvelopeCommand(
                clipId = clip.id,
                volume = volume,
                isMuted = isMuted,
                fadeInUs = fadeInUs,
                fadeOutUs = fadeOutUs,
            )
        )
    }

    // --- In-Studio Preset Apply & Save Custom Preset ---

    fun applyPresetToSelectedClip(preset: PresetDefinition, intensity: Float = 1f) {
        val clip = _uiState.value.selectedClip
        if (clip == null) {
            _uiState.update { it.copy(statusMessage = "Select a clip on the timeline first.") }
            return
        }
        executeCommand(
            ApplyPresetCommand(
                preset = preset,
                targetClipId = clip.id,
                intensity = intensity,
            )
        )
        _uiState.update { it.copy(statusMessage = "Applied \"${preset.title}\" (${(intensity * 100).toInt()}%)") }
    }

    fun saveSelectedClipAsCustomPreset(title: String, category: PresetCategory) {
        val clip = _uiState.value.selectedClip ?: return
        val repo = presetRepository ?: return
        viewModelScope.launch {
            repo.saveCustomPresetFromClip(title = title, category = category, clip = clip)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(statusMessage = "Saved custom preset \"${created.title}\" to Presets & EXCEVO Library")
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Failed to save custom preset.")
                    }
                }
        }
    }

    // --- Timeline Operations (Split, Trim, Move, Duplicate, Multi-Track) ---

    fun splitSelectedClipAtPlayhead() {
        pausePlayback()
        val clip = _uiState.value.selectedClip
        if (clip == null) {
            _uiState.update { it.copy(statusMessage = "Select a clip on the timeline first.") }
            return
        }
        val playhead = _uiState.value.playheadUs
        val minSplit = clip.timelineStartUs + TimelineMath.MIN_CLIP_DURATION_US
        val maxSplit = clip.timelineEndUs - TimelineMath.MIN_CLIP_DURATION_US
        if (playhead !in minSplit..maxSplit) {
            _uiState.update {
                it.copy(statusMessage = "Move playhead inside the selected clip (at least 0.1s from edges) to split.")
            }
            return
        }
        val rightClipId = UUID.randomUUID().toString()
        executeCommand(
            SplitClipCommand(
                clipId = clip.id,
                splitAtTimelineUs = playhead,
                secondClipId = rightClipId,
            )
        )
        _uiState.update { it.copy(selectedClipId = rightClipId, statusMessage = "Clip split at playhead") }
    }

    fun duplicateSelectedClip() {
        pausePlayback()
        val clip = _uiState.value.selectedClip ?: return
        val copyId = UUID.randomUUID().toString()
        executeCommand(DuplicateClipCommand(clipId = clip.id, newClipId = copyId))
        _uiState.update { it.copy(selectedClipId = copyId, statusMessage = "Clip duplicated") }
    }

    fun toggleGroupSelectedClip() {
        val clip = _uiState.value.selectedClip ?: return
        val wasGrouped = clip.groupId != null
        val newGroupId = "grp_${UUID.randomUUID().toString().take(6)}"
        executeCommand(
            ToggleGroupClipCommand(
                clipId = clip.id,
                playheadUs = _uiState.value.playheadUs,
                newGroupId = newGroupId,
            )
        )
        _uiState.update {
            it.copy(statusMessage = if (wasGrouped) "Layer ungrouped" else "Layer grouped")
        }
    }

    fun addTrack(trackType: TrackType) {
        val project = _uiState.value.project ?: return
        val count = project.tracks.count { it.type == trackType } + 1
        val name = when (trackType) {
            TrackType.OVERLAY -> "Overlay $count"
            TrackType.TEXT -> "Text $count"
            TrackType.ADJUSTMENT -> "Adjust $count"
            TrackType.AUDIO -> "Audio $count"
            TrackType.VIDEO_PRIMARY -> "Main Video $count"
        }
        val id = "track_${trackType.name.lowercase()}_${UUID.randomUUID().toString().take(6)}"
        executeCommand(AddTrackCommand(trackId = id, trackType = trackType, name = name))
        _uiState.update { it.copy(statusMessage = "Added track $name") }
    }

    fun removeTrack(trackId: String) {
        executeCommand(RemoveTrackCommand(trackId))
        _uiState.update { it.copy(statusMessage = "Track removed") }
    }

    fun toggleTrackVisibility(trackId: String) {
        executeCommand(ToggleTrackVisibilityCommand(trackId))
    }

    fun resetSelectedClipAll() {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(ResetClipAllCommand(clip.id))
        _uiState.update { it.copy(statusMessage = "Layer reset to defaults") }
    }

    fun moveSelectedClipToTrack(targetTrackId: String) {
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(MoveClipToTrackCommand(clip.id, targetTrackId))
        _uiState.update { it.copy(statusMessage = "Moved clip to track") }
    }

    fun trimSelectedClip(newSourceStartUs: Long, newSourceEndUs: Long) {
        trimClipById(_uiState.value.selectedClipId, newSourceStartUs, newSourceEndUs)
    }

    fun trimClipById(clipId: String?, newSourceStartUs: Long, newSourceEndUs: Long) {
        if (clipId == null) return
        pausePlayback()
        executeCommand(
            TrimClipCommand(
                clipId = clipId,
                newSourceStartUs = newSourceStartUs,
                newSourceEndUs = newSourceEndUs,
            )
        )
        val maxUs = _uiState.value.project?.totalDurationUs() ?: 0L
        _uiState.update {
            it.copy(
                playheadUs = it.playheadUs.coerceIn(0L, maxUs),
                statusMessage = "Clip trimmed",
            )
        }
    }

    fun moveSelectedClip(deltaIndex: Int) {
        pausePlayback()
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(MoveClipOrderCommand(clipId = clip.id, deltaIndex = deltaIndex))
    }

    fun deleteSelectedClip() {
        pausePlayback()
        val clip = _uiState.value.selectedClip ?: return
        executeCommand(DeleteClipCommand(clipId = clip.id))
        val updatedProject = _uiState.value.project
        val maxUs = updatedProject?.totalDurationUs() ?: 0L
        val nextSelection = updatedProject?.primaryVideoTrack()?.clips?.firstOrNull()?.id
            ?: updatedProject?.allClips()?.firstOrNull()?.id
        _uiState.update {
            it.copy(
                selectedClipId = nextSelection,
                isLayerMenuVisible = false,
                focusedLayerPanel = null,
                playheadUs = it.playheadUs.coerceIn(0L, maxUs),
                statusMessage = "Clip removed (Undo available)",
            )
        }
    }

    fun updateProjectSettings(
        name: String,
        aspectRatio: AspectRatioPreset,
        frameRate: FrameRatePreset,
        targetResolution: ExportResolutionPreset? = null,
        scaleMode: CanvasScaleMode? = null,
        backgroundId: String? = null,
        customWidthPx: Int? = null,
        customHeightPx: Int? = null,
        keepCustomResolution: Boolean = true,
    ) {
        executeCommand(
            UpdateProjectSettingsCommand(
                name = name,
                aspectRatio = aspectRatio,
                frameRate = frameRate,
                targetResolution = targetResolution,
                scaleMode = scaleMode,
                backgroundId = backgroundId,
                customWidthPx = customWidthPx,
                customHeightPx = customHeightPx,
                keepCustomResolution = keepCustomResolution,
            )
        )
        _uiState.update { it.copy(statusMessage = "Sequence settings updated") }
    }

    fun undo() {
        pausePlayback()
        val manager = historyManager ?: return
        _uiState.update {
            it.copy(liveGradeOverride = null, liveTransformOverride = null, liveEffectStackOverride = null)
        }
        val snap = manager.undo()
        applySnapshotAndSave(snap)
    }

    fun redo() {
        pausePlayback()
        val manager = historyManager ?: return
        _uiState.update {
            it.copy(liveGradeOverride = null, liveTransformOverride = null, liveEffectStackOverride = null)
        }
        val snap = manager.redo()
        applySnapshotAndSave(snap)
    }

    fun onHostPause() {
        playbackPort?.onHostPause()
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun onHostResume() {
        playbackPort?.onHostResume()
    }

    fun dismissMessages() {
        playbackPort?.clearError()
        _uiState.update { it.copy(statusMessage = null, errorMessage = null, playbackError = null) }
    }

    private fun executeCommand(command: EditCommand) {
        val manager = historyManager ?: return
        val snap = manager.execute(command, System.currentTimeMillis())
        applySnapshotAndSave(snap)
    }

    private fun applySnapshotAndSave(snap: HistorySnapshot) {
        val maxUs = snap.project.totalDurationUs()
        val clampedPlayhead = _uiState.value.playheadUs.coerceIn(0L, maxUs)
        _uiState.update { state ->
            val currentSelectionExists = snap.project.allClips().any { it.id == state.selectedClipId }
            state.copy(
                project = snap.project,
                canUndo = snap.canUndo,
                canRedo = snap.canRedo,
                playheadUs = clampedPlayhead,
                liveGradeOverride = null,
                liveTransformOverride = null,
                liveEffectStackOverride = null,
                selectedClipId = if (currentSelectionExists) {
                    state.selectedClipId
                } else {
                    snap.project.primaryVideoTrack().clips.firstOrNull()?.id
                        ?: snap.project.allClips().firstOrNull()?.id
                },
            )
        }
        playbackPort?.syncSequence(snap.project, clampedPlayhead)
        viewModelScope.launch {
            runCatching {
                projectStorage.saveProject(snap.project)
            }.onSuccess {
                _uiState.update { it.copy(lastSavedAtMs = snap.project.updatedAtMs) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Failed to autosave sequence.")
                }
            }
        }
    }

    override fun onCleared() {
        playbackJob?.cancel()
        playbackPort?.release()
        super.onCleared()
    }

    class Factory(
        private val projectId: String,
        private val projectStorage: ProjectStoragePort,
        private val mediaProbe: MediaProbePort,
        private val presetRepository: LocalPresetRepository? = null,
        private val playbackPort: SequencePlaybackPort? = null,
        private val mediaLibraryRepository: MediaLibraryRepository? = null,
        private val userPreferencesRepository: UserPreferencesRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(
                projectId = projectId,
                projectStorage = projectStorage,
                mediaProbe = mediaProbe,
                presetRepository = presetRepository,
                playbackPort = playbackPort,
                mediaLibraryRepository = mediaLibraryRepository,
                userPreferencesRepository = userPreferencesRepository,
            ) as T
        }
    }
}
