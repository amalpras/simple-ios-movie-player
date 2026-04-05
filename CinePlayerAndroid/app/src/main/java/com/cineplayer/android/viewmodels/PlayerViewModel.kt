package com.cineplayer.android.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import com.cineplayer.android.data.AppSettings
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.SubtitleTrack
import com.cineplayer.android.services.MediaLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoFitMode { FIT, FILL, STRETCH }

data class AudioTrackInfo(val groupIndex: Int, val label: String)

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val showControls: Boolean = true,
    val currentSubtitleText: String = "",
    val isPipMode: Boolean = false,
    val showNextEpisodeCountdown: Boolean = false,
    val nextEpisodeCountdownSeconds: Int = 5,
    val error: String? = null,
    val fitMode: VideoFitMode = VideoFitMode.FIT,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val selectedAudioGroupIndex: Int = -1
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build()

    private var settings: AppSettings = AppSettings.getInstance(application)
    private var library: MediaLibrary = MediaLibrary.getInstance(application)
    private var currentItem: MediaItem? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var positionJob: Job? = null
    private var hideControlsJob: Job? = null
    private var countdownJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(state: Int) {
            _uiState.value = _uiState.value.copy(
                isBuffering = state == Player.STATE_BUFFERING,
                isLoading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            )
            if (state == Player.STATE_READY) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            if (state == Player.STATE_ENDED) onPlaybackEnded()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.value = _uiState.value.copy(error = error.message ?: "Playback error")
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audioGroups = tracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .mapIndexed { index, group ->
                    val format = group.getTrackFormat(0)
                    val lang = format.language ?: "Track ${index + 1}"
                    val label = java.util.Locale(lang).displayLanguage.ifBlank { lang }
                    AudioTrackInfo(groupIndex = index, label = label)
                }
            _uiState.value = _uiState.value.copy(audioTracks = audioGroups)
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    fun loadMedia(item: MediaItem, context: Context) {
        currentItem = item
        settings = AppSettings.getInstance(context)
        library = MediaLibrary.getInstance(context)

        val exoItem = androidx.media3.common.MediaItem.fromUri(
            android.net.Uri.parse(item.fileUri)
        )
        exoPlayer.apply {
            setMediaItem(exoItem)
            prepare()
            playWhenReady = true
            if (settings.resumePlayback && item.lastPlaybackPosition > 5000L) {
                seekTo(item.lastPlaybackPosition)
            }
            setPlaybackSpeed(settings.defaultPlaybackSpeed)
        }
        _uiState.value = PlayerUiState(
            isLoading = true,
            playbackSpeed = settings.defaultPlaybackSpeed
        )
        startPositionUpdates()
        showControls()
    }

    private fun onPlaybackEnded() {
        val item = currentItem ?: return
        val dur = _uiState.value.durationMs
        library.updatePlaybackPosition(item.id, dur, dur)
        if (settings.autoPlayNextEpisode) {
            getNextEpisode()?.let { next ->
                _uiState.value = _uiState.value.copy(
                    showNextEpisodeCountdown = true,
                    nextEpisodeCountdownSeconds = settings.countdownToNextEpisode
                )
                startNextEpisodeCountdown(next)
            }
        }
    }

    fun getNextEpisode(): MediaItem? = currentItem?.let { library.nextEpisode(it.id) }

    private fun startNextEpisodeCountdown(next: MediaItem) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var count = _uiState.value.nextEpisodeCountdownSeconds
            while (isActive && count > 0) {
                delay(1000L)
                count--
                _uiState.value = _uiState.value.copy(nextEpisodeCountdownSeconds = count)
            }
            if (isActive) {
                _uiState.value = _uiState.value.copy(showNextEpisodeCountdown = false)
                loadMedia(next, getApplication())
            }
        }
    }

    fun cancelNextEpisodeCountdown() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(showNextEpisodeCountdown = false)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun skipForward() {
        val skip = settings.skipForwardSeconds * 1000L
        exoPlayer.seekTo((exoPlayer.currentPosition + skip).coerceAtMost(
            exoPlayer.duration.coerceAtLeast(0L)
        ))
    }

    fun skipBackward() {
        val skip = settings.skipBackwardSeconds * 1000L
        exoPlayer.seekTo((exoPlayer.currentPosition - skip).coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    // MARK: - Aspect Ratio

    fun cycleAspectRatio() {
        val next = when (_uiState.value.fitMode) {
            VideoFitMode.FIT -> VideoFitMode.FILL
            VideoFitMode.FILL -> VideoFitMode.STRETCH
            VideoFitMode.STRETCH -> VideoFitMode.FIT
        }
        _uiState.value = _uiState.value.copy(fitMode = next)
    }

    // MARK: - Audio Track Selection

    fun selectAudioTrack(groupIndex: Int) {
        val tracks = exoPlayer.currentTracks
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groupIndex < audioGroups.size) {
            val group = audioGroups[groupIndex]
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                .build()
            _uiState.value = _uiState.value.copy(selectedAudioGroupIndex = groupIndex)
        }
    }

    // MARK: - Subtitles

    fun setSubtitleTrack(track: SubtitleTrack?) {
        currentItem?.let { item ->
            val idx = track?.let { item.subtitleTracks.indexOf(it).takeIf { i -> i >= 0 } }
            currentItem = item.copy(selectedSubtitleIndex = idx)
        }
    }

    fun showControls() {
        _uiState.value = _uiState.value.copy(showControls = true)
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(3500L)
            _uiState.value = _uiState.value.copy(showControls = false)
        }
    }

    fun setPipMode(inPip: Boolean) {
        _uiState.value = _uiState.value.copy(isPipMode = inPip)
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                val item = currentItem
                // Subtitle lookup with delay offset
                val subtitleText = if (item != null && item.selectedSubtitleIndex != null) {
                    val delayMs = settings.subtitleDelayMs
                    val adjustedPos = pos - delayMs
                    // Subtitle cues are parsed on-demand from the track file
                    "" // Actual cue lookup happens in PlayerScreen via SubtitleOverlayState
                } else ""
                _uiState.value = _uiState.value.copy(
                    currentPositionMs = pos,
                    durationMs = dur
                )
                item?.id?.let { id ->
                    if (pos > 0) library.updatePlaybackPosition(id, pos, dur)
                }
                delay(500L)
            }
        }
    }

    override fun onCleared() {
        positionJob?.cancel()
        hideControlsJob?.cancel()
        countdownJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onCleared()
    }
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build()

    private var settings: AppSettings = AppSettings.getInstance(application)
    private var library: MediaLibrary = MediaLibrary.getInstance(application)
    private var currentItem: MediaItem? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var positionJob: Job? = null
    private var hideControlsJob: Job? = null
    private var countdownJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(state: Int) {
            _uiState.value = _uiState.value.copy(
                isBuffering = state == Player.STATE_BUFFERING,
                isLoading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            )
            if (state == Player.STATE_READY) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            if (state == Player.STATE_ENDED) onPlaybackEnded()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.value = _uiState.value.copy(error = error.message ?: "Playback error")
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    fun loadMedia(item: MediaItem, context: Context) {
        currentItem = item
        settings = AppSettings.getInstance(context)
        library = MediaLibrary.getInstance(context)

        val exoItem = androidx.media3.common.MediaItem.fromUri(
            android.net.Uri.parse(item.fileUri)
        )
        exoPlayer.apply {
            setMediaItem(exoItem)
            prepare()
            playWhenReady = true
            if (settings.resumePlayback && item.lastPlaybackPosition > 5000L) {
                seekTo(item.lastPlaybackPosition)
            }
            setPlaybackSpeed(settings.defaultPlaybackSpeed)
        }
        _uiState.value = PlayerUiState(
            isLoading = true,
            playbackSpeed = settings.defaultPlaybackSpeed
        )
        startPositionUpdates()
        showControls()
    }

    private fun onPlaybackEnded() {
        val item = currentItem ?: return
        val dur = _uiState.value.durationMs
        library.updatePlaybackPosition(item.id, dur, dur)
        if (settings.autoPlayNextEpisode) {
            getNextEpisode()?.let { next ->
                _uiState.value = _uiState.value.copy(
                    showNextEpisodeCountdown = true,
                    nextEpisodeCountdownSeconds = settings.countdownToNextEpisode
                )
                startNextEpisodeCountdown(next)
            }
        }
    }

    fun getNextEpisode(): MediaItem? = currentItem?.let { library.nextEpisode(it.id) }

    private fun startNextEpisodeCountdown(next: MediaItem) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var count = _uiState.value.nextEpisodeCountdownSeconds
            while (isActive && count > 0) {
                delay(1000L)
                count--
                _uiState.value = _uiState.value.copy(nextEpisodeCountdownSeconds = count)
            }
            if (isActive) {
                _uiState.value = _uiState.value.copy(showNextEpisodeCountdown = false)
                loadMedia(next, getApplication())
            }
        }
    }

    fun cancelNextEpisodeCountdown() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(showNextEpisodeCountdown = false)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun skipForward() {
        val skip = settings.skipForwardSeconds * 1000L
        exoPlayer.seekTo((exoPlayer.currentPosition + skip).coerceAtMost(
            exoPlayer.duration.coerceAtLeast(0L)
        ))
    }

    fun skipBackward() {
        val skip = settings.skipBackwardSeconds * 1000L
        exoPlayer.seekTo((exoPlayer.currentPosition - skip).coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun setSubtitleTrack(track: SubtitleTrack?) {
        currentItem?.let { item ->
            val idx = track?.let { item.subtitleTracks.indexOf(it).takeIf { i -> i >= 0 } }
            currentItem = item.copy(selectedSubtitleIndex = idx)
        }
    }

    fun showControls() {
        _uiState.value = _uiState.value.copy(showControls = true)
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(3500L)
            _uiState.value = _uiState.value.copy(showControls = false)
        }
    }

    fun setPipMode(inPip: Boolean) {
        _uiState.value = _uiState.value.copy(isPipMode = inPip)
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(currentPositionMs = pos, durationMs = dur)
                currentItem?.id?.let { id ->
                    if (pos > 0) library.updatePlaybackPosition(id, pos, dur)
                }
                delay(1000L)
            }
        }
    }

    override fun onCleared() {
        positionJob?.cancel()
        hideControlsJob?.cancel()
        countdownJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onCleared()
    }
}
