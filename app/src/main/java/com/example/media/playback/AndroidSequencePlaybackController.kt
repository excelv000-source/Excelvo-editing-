package com.example.media.playback

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.example.core.engine.TimelineEvaluator
import com.example.core.engine.TimelineMath
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.ports.GpuCapabilities
import com.example.core.ports.PlaybackStatus
import com.example.core.ports.SequencePlaybackPort
import com.example.media.gpu.ExcevoColorGlEffect
import com.example.media.gpu.ExcevoLiveRenderState
import com.example.media.gpu.ExcevoTransformMatrixEffect
import com.example.media.gpu.GpuCapabilityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class AndroidSequencePlaybackController(
    private val context: Context,
    hardwareAccelerationRequested: Boolean = true,
) : SequencePlaybackPort {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveRenderState = ExcevoLiveRenderState()

    override val gpuCapabilities: GpuCapabilities = GpuCapabilityChecker.inspect(
        context = context,
        hardwareAccelerationRequested = hardwareAccelerationRequested,
    )

    private val _status = MutableStateFlow(
        PlaybackStatus(isUsingGpuEffects = gpuCapabilities.useHardwareGpuPipeline)
    )
    override val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    private var currentProject: TimelineProject? = null
    private var preparedClipKey: String? = null
    private var preparedClipId: String? = null
    private var preparedAudioClipKey: String? = null
    private var gpuFallbackTriggered = !gpuCapabilities.useHardwareGpuPipeline
    private var attachedTextureView: TextureView? = null
    private var tickerJob: Job? = null
    private var resumeSeekUs: Long? = null

    private var exoPlayer: ExoPlayer? = null
    private var audioPlayer: ExoPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _status.update { it.copy(isBuffering = buffering) }
            if (playbackState == Player.STATE_ENDED && _status.value.isPlaying) {
                advancePastCurrentVideoClip()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!gpuFallbackTriggered) {
                gpuFallbackTriggered = true
                preparedClipKey = null
                _status.update {
                    it.copy(
                        isUsingGpuEffects = false,
                        isBuffering = false,
                        playbackError = "GPU shader fallback active: ${error.errorCodeName}",
                    )
                }
                val project = currentProject ?: return
                syncActiveClipAtPlayhead(project, _status.value.playheadUs, forceReload = true)
            } else {
                pause()
                _status.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        playbackError = error.localizedMessage ?: "Video decoder error (${error.errorCodeName})",
                    )
                }
            }
        }
    }

    private val audioListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _status.update {
                it.copy(
                    playbackError = error.localizedMessage ?: "Audio playback error (${error.errorCodeName})",
                )
            }
        }
    }

    fun attachTextureView(textureView: TextureView?) {
        if (attachedTextureView === textureView) return
        attachedTextureView = textureView
        if (textureView != null) {
            ensurePlayer()?.setVideoTextureView(textureView)
        } else {
            exoPlayer?.setVideoTextureView(null)
        }
    }

    private fun ensurePlayer(): ExoPlayer? {
        if (exoPlayer != null) return exoPlayer
        return runCatching {
            ExoPlayer.Builder(context)
                .build()
                .apply {
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    addListener(playerListener)
                    attachedTextureView?.let { setVideoTextureView(it) }
                }
        }.onSuccess { created ->
            exoPlayer = created
        }.onFailure { err ->
            _status.update {
                it.copy(playbackError = err.localizedMessage ?: "Media3 player unavailable on this device.")
            }
        }.getOrNull()
    }

    private fun ensureAudioPlayer(): ExoPlayer? {
        if (audioPlayer != null) return audioPlayer
        return runCatching {
            ExoPlayer.Builder(context)
                .build()
                .apply {
                    setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    addListener(audioListener)
                }
        }.onSuccess { created ->
            audioPlayer = created
        }.onFailure { err ->
            _status.update {
                it.copy(playbackError = err.localizedMessage ?: "Audio player unavailable on this device.")
            }
        }.getOrNull()
    }

    override fun syncSequence(project: TimelineProject, playheadUs: Long) {
        currentProject = project
        val clamped = playheadUs.coerceIn(0L, project.totalDurationUs().coerceAtLeast(0L))
        _status.update { it.copy(playheadUs = clamped) }
        syncActiveClipAtPlayhead(project, clamped, forceReload = false)
    }

    override fun updateLiveVisualState(clip: TimelineClip?, project: TimelineProject) {
        currentProject = project
        if (clip == null) return
        val atUs = _status.value.playheadUs
        liveRenderState.updateGrade(clip.colorGrade, clip.effectStack)
        val asset = project.assets[clip.assetId]
        val srcAspect = if (asset != null && asset.width > 0 && asset.height > 0) {
            asset.width.toFloat() / asset.height.toFloat()
        } else {
            project.effectiveAspectFloat()
        }
        liveRenderState.updateTransform(
            transform = TimelineEvaluator.evaluateTransformAt(clip, atUs),
            scaleMode = project.scaleMode,
            targetAspect = project.effectiveAspectFloat(),
            sourceAspect = srcAspect,
        )
    }

    override fun play() {
        val project = currentProject ?: return
        val totalUs = project.totalDurationUs()
        if (totalUs <= 0L) return
        val startUs = if (_status.value.playheadUs >= totalUs) 0L else _status.value.playheadUs
        _status.update { it.copy(playheadUs = startUs, isPlaying = true, playbackError = null) }
        syncActiveClipAtPlayhead(project, startUs, forceReload = false)
        startTicker()
    }

    override fun pause() {
        tickerJob?.cancel()
        tickerJob = null
        exoPlayer?.pause()
        audioPlayer?.pause()
        _status.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    override fun seekToTimelineUs(timelineUs: Long) {
        val project = currentProject ?: return
        val clamped = timelineUs.coerceIn(0L, project.totalDurationUs().coerceAtLeast(0L))
        _status.update { it.copy(playheadUs = clamped) }
        syncActiveClipAtPlayhead(project, clamped, forceReload = false)
        val activeClip = TimelineMath.findActiveClipAt(project.primaryVideoTrack(), clamped)
        if (activeClip != null && activeClip.mediaType == MediaType.VIDEO) {
            val offsetUs = ((clamped - activeClip.timelineStartUs) * activeClip.speedMultiplier).toLong()
                .coerceIn(0L, (activeClip.sourceEndUs - activeClip.sourceStartUs).coerceAtLeast(0L))
            exoPlayer?.seekTo(offsetUs / 1000L)
        }
        val audioTrack = project.tracks.firstOrNull { it.type == com.example.core.model.TrackType.AUDIO }
        val activeAudioClip = audioTrack?.let { TimelineMath.findActiveClipAt(it, clamped) }
        if (activeAudioClip != null) {
            val audioOffsetUs = ((clamped - activeAudioClip.timelineStartUs) * activeAudioClip.speedMultiplier).toLong()
                .coerceIn(0L, (activeAudioClip.sourceEndUs - activeAudioClip.sourceStartUs).coerceAtLeast(0L))
            audioPlayer?.seekTo(audioOffsetUs / 1000L)
        }
    }

    override fun stepFrames(deltaFrames: Int) {
        val project = currentProject ?: return
        pause()
        val nextUs = TimelineMath.stepFrameUs(
            currentUs = _status.value.playheadUs,
            deltaFrames = deltaFrames,
            fps = project.frameRate.fps,
            maxDurationUs = project.totalDurationUs(),
        )
        seekToTimelineUs(nextUs)
    }

    override fun onHostPause() {
        resumeSeekUs = _status.value.playheadUs
        pause()
        exoPlayer?.playWhenReady = false
        audioPlayer?.playWhenReady = false
    }

    override fun onHostResume() {
        val seekTarget = resumeSeekUs ?: return
        resumeSeekUs = null
        seekToTimelineUs(seekTarget)
    }

    override fun clearError() {
        _status.update { it.copy(playbackError = null) }
    }

    override fun release() {
        tickerJob?.cancel()
        tickerJob = null
        exoPlayer?.let { player ->
            player.removeListener(playerListener)
            player.setVideoTextureView(null)
            player.release()
        }
        exoPlayer = null
        audioPlayer?.let { player ->
            player.removeListener(audioListener)
            player.release()
        }
        audioPlayer = null
        attachedTextureView = null
        controllerScope.cancel()
    }

    private fun resolvePlayableUri(uriString: String, cachedLocalPath: String?): Uri {
        if (!cachedLocalPath.isNullOrBlank()) {
            val f = java.io.File(cachedLocalPath)
            if (f.exists() && f.canRead()) return Uri.fromFile(f)
        }
        return Uri.parse(uriString)
    }

    private fun syncActiveClipAtPlayhead(
        project: TimelineProject,
        playheadUs: Long,
        forceReload: Boolean,
    ) {
        val primaryTrack = project.primaryVideoTrack()
        val clip = TimelineMath.findActiveClipAt(primaryTrack, playheadUs)
        updateLiveVisualState(clip, project)

        if (clip == null || clip.mediaType != MediaType.VIDEO) {
            exoPlayer?.pause()
            _status.update { it.copy(activeVideoClipId = null, isBuffering = false) }
        } else {
            val asset = project.assets[clip.assetId]
            val uriString = asset?.sourceUri
            if (uriString.isNullOrBlank() || !asset.isAccessible) {
                exoPlayer?.pause()
                _status.update { it.copy(activeVideoClipId = null) }
            } else {
                val player = ensurePlayer()
                if (player != null) {
                    val clipKey = "${clip.id}|$uriString|${asset.cachedLocalPath}|${clip.sourceStartUs}|${clip.sourceEndUs}|$gpuFallbackTriggered"
                    if (forceReload || preparedClipKey != clipKey) {
                        preparedClipKey = clipKey
                        preparedClipId = clip.id

                        val clipping = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((clip.sourceStartUs / 1000L).coerceAtLeast(0L))
                            .setEndPositionMs((clip.sourceEndUs / 1000L).coerceAtLeast((clip.sourceStartUs / 1000L) + 40L))
                            .build()

                        val mediaItem = MediaItem.Builder()
                            .setUri(resolvePlayableUri(uriString, asset.cachedLocalPath))
                            .setClippingConfiguration(clipping)
                            .build()

                        val effects = if (!gpuFallbackTriggered) {
                            listOf(
                                ExcevoTransformMatrixEffect(liveRenderState),
                                ExcevoColorGlEffect(liveRenderState),
                            )
                        } else {
                            emptyList()
                        }

                        runCatching {
                            player.setVideoEffects(effects)
                            player.setMediaItem(mediaItem)
                            player.playbackParameters = PlaybackParameters(clip.speedMultiplier.coerceIn(0.25f, 4f))
                            player.volume = TimelineEvaluator.evaluateVolumeAt(clip, primaryTrack.isMuted, playheadUs).coerceIn(0f, 1f)
                            val initialOffsetMs = ((playheadUs - clip.timelineStartUs) * clip.speedMultiplier / 1000f)
                                .toLong()
                                .coerceAtLeast(0L)
                            player.seekTo(initialOffsetMs)
                            player.prepare()
                            player.playWhenReady = _status.value.isPlaying
                        }.onFailure { err ->
                            if (!gpuFallbackTriggered) {
                                gpuFallbackTriggered = true
                                preparedClipKey = null
                                syncActiveClipAtPlayhead(project, playheadUs, forceReload = true)
                            } else {
                                _status.update {
                                    it.copy(playbackError = err.localizedMessage ?: "Failed to prepare video clip.")
                                }
                            }
                        }
                    } else {
                        player.playbackParameters = PlaybackParameters(clip.speedMultiplier.coerceIn(0.25f, 4f))
                        player.volume = TimelineEvaluator.evaluateVolumeAt(clip, primaryTrack.isMuted, playheadUs).coerceIn(0f, 1f)
                        player.playWhenReady = _status.value.isPlaying
                    }

                    _status.update {
                        it.copy(
                            activeVideoClipId = clip.id,
                            isUsingGpuEffects = !gpuFallbackTriggered,
                        )
                    }
                }
            }
        }

        syncActiveAudioClipAtPlayhead(project, playheadUs)
    }

    private fun syncActiveAudioClipAtPlayhead(
        project: TimelineProject,
        playheadUs: Long,
    ) {
        val audioTrack = project.tracks.firstOrNull { it.type == com.example.core.model.TrackType.AUDIO }
        val audioClip = audioTrack?.let { TimelineMath.findActiveClipAt(it, playheadUs) }
        if (audioClip == null) {
            audioPlayer?.pause()
            return
        }
        val asset = project.assets[audioClip.assetId]
        val uriString = asset?.sourceUri
        if (uriString.isNullOrBlank() || !asset.isAccessible) {
            audioPlayer?.pause()
            return
        }

        val aPlayer = ensureAudioPlayer() ?: return
        val audioKey = "${audioClip.id}|$uriString|${asset.cachedLocalPath}|${audioClip.sourceStartUs}|${audioClip.sourceEndUs}"
        if (preparedAudioClipKey != audioKey) {
            preparedAudioClipKey = audioKey
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs((audioClip.sourceStartUs / 1000L).coerceAtLeast(0L))
                .setEndPositionMs((audioClip.sourceEndUs / 1000L).coerceAtLeast((audioClip.sourceStartUs / 1000L) + 40L))
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(resolvePlayableUri(uriString, asset.cachedLocalPath))
                .setClippingConfiguration(clipping)
                .build()
            runCatching {
                aPlayer.setMediaItem(mediaItem)
                aPlayer.playbackParameters = PlaybackParameters(audioClip.speedMultiplier.coerceIn(0.25f, 4f))
                aPlayer.volume = TimelineEvaluator.evaluateVolumeAt(audioClip, audioTrack.isMuted, playheadUs).coerceIn(0f, 1f)
                val initialOffsetMs = ((playheadUs - audioClip.timelineStartUs) * audioClip.speedMultiplier / 1000f)
                    .toLong()
                    .coerceAtLeast(0L)
                aPlayer.seekTo(initialOffsetMs)
                aPlayer.prepare()
                aPlayer.playWhenReady = _status.value.isPlaying
            }
        } else {
            aPlayer.playbackParameters = PlaybackParameters(audioClip.speedMultiplier.coerceIn(0.25f, 4f))
            aPlayer.volume = TimelineEvaluator.evaluateVolumeAt(audioClip, audioTrack.isMuted, playheadUs).coerceIn(0f, 1f)
            aPlayer.playWhenReady = _status.value.isPlaying
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = controllerScope.launch {
            while (isActive && _status.value.isPlaying) {
                val project = currentProject ?: break
                val totalUs = project.totalDurationUs()
                if (totalUs <= 0L) {
                    pause()
                    break
                }
                val stepUs = project.frameRate.frameDurationUs
                val delayMs = (stepUs / 1000L).coerceIn(16L, 42L)
                val currentUs = _status.value.playheadUs
                val activeClip = TimelineMath.findActiveClipAt(project.primaryVideoTrack(), currentUs)

                if (activeClip != null && activeClip.mediaType == MediaType.VIDEO && exoPlayer != null) {
                    val player = exoPlayer!!
                    if (player.isPlaying) {
                        val relTimelineUs = (player.currentPosition * 1000L / activeClip.speedMultiplier).toLong()
                        val syncedUs = (activeClip.timelineStartUs + relTimelineUs).coerceAtMost(activeClip.timelineEndUs)
                        _status.update { it.copy(playheadUs = syncedUs) }
                        updateLiveVisualState(activeClip, project)
                        player.volume = TimelineEvaluator.evaluateVolumeAt(activeClip, project.primaryVideoTrack().isMuted, syncedUs).coerceIn(0f, 1f)
                        syncActiveAudioClipAtPlayhead(project, syncedUs)
                        if (syncedUs >= totalUs) {
                            _status.update { it.copy(playheadUs = totalUs) }
                            pause()
                            break
                        } else if (syncedUs >= activeClip.timelineEndUs - 15_000L) {
                            syncActiveClipAtPlayhead(project, activeClip.timelineEndUs + 1_000L, forceReload = false)
                        }
                    }
                } else {
                    val nextUs = currentUs + stepUs
                    if (nextUs >= totalUs) {
                        _status.update { it.copy(playheadUs = totalUs) }
                        pause()
                        break
                    } else {
                        _status.update { it.copy(playheadUs = nextUs) }
                        syncActiveClipAtPlayhead(project, nextUs, forceReload = false)
                    }
                }
                delay(delayMs)
            }
        }
    }

    private fun advancePastCurrentVideoClip() {
        val project = currentProject ?: return
        val currentClip = project.primaryVideoTrack().clips.firstOrNull { it.id == preparedClipId } ?: return
        val totalUs = project.totalDurationUs()
        val nextStart = currentClip.timelineEndUs
        if (nextStart >= totalUs) {
            _status.update { it.copy(playheadUs = totalUs) }
            pause()
        } else {
            _status.update { it.copy(playheadUs = nextStart) }
            syncActiveClipAtPlayhead(project, nextStart, forceReload = false)
        }
    }
}
