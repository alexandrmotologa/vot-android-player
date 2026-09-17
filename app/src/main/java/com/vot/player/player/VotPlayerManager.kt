package com.vot.player.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vot.player.data.model.SubtitleCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class VotPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val videoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    val voiceoverPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _originalVolume = MutableStateFlow(0.20f)
    val originalVolume: StateFlow<Float> = _originalVolume.asStateFlow()

    private val _voiceoverVolume = MutableStateFlow(1.0f)
    val voiceoverVolume: StateFlow<Float> = _voiceoverVolume.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentSubtitle = MutableStateFlow<String?>(null)
    val currentSubtitle: StateFlow<String?> = _currentSubtitle.asStateFlow()

    private var subtitleCues: List<SubtitleCue> = emptyList()
    private var isSubtitlesEnabled: Boolean = false
    private var syncJob: Job? = null
    private var lastNonZeroOriginalVolume: Float = 0.20f

    init {
        videoPlayer.volume = _originalVolume.value
        voiceoverPlayer.volume = _voiceoverVolume.value

        videoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    if (!voiceoverPlayer.isPlaying && voiceoverPlayer.mediaItemCount > 0) {
                        voiceoverPlayer.play()
                    }
                } else {
                    if (voiceoverPlayer.isPlaying) {
                        voiceoverPlayer.pause()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = videoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_BUFFERING) {
                    voiceoverPlayer.pause()
                }
            }
        })

        startSyncLoop()
    }

    fun prepare(videoStreamUrl: String, voiceoverAudioUrl: String?, subtitles: List<SubtitleCue> = emptyList()) {
        subtitleCues = subtitles

        val videoItem = MediaItem.fromUri(videoStreamUrl)
        videoPlayer.setMediaItem(videoItem)
        videoPlayer.prepare()

        if (!voiceoverAudioUrl.isNullOrEmpty()) {
            val audioItem = MediaItem.fromUri(voiceoverAudioUrl)
            voiceoverPlayer.setMediaItem(audioItem)
            voiceoverPlayer.prepare()
        }

        videoPlayer.playWhenReady = true
        voiceoverPlayer.playWhenReady = true
    }

    fun play() {
        videoPlayer.play()
        if (voiceoverPlayer.mediaItemCount > 0) {
            voiceoverPlayer.play()
        }
    }

    fun pause() {
        videoPlayer.pause()
        voiceoverPlayer.pause()
    }

    fun togglePlayPause() {
        if (videoPlayer.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, videoPlayer.duration.coerceAtLeast(0L))
        videoPlayer.seekTo(target)
        if (voiceoverPlayer.mediaItemCount > 0) {
            voiceoverPlayer.seekTo(target)
        }
        _currentPositionMs.value = target
        updateSubtitles(target)
    }

    fun seekRelative(deltaMs: Long) {
        seekTo(videoPlayer.currentPosition + deltaMs)
    }

    fun setOriginalVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _originalVolume.value = clamped
        videoPlayer.volume = clamped
        if (clamped > 0f) {
            lastNonZeroOriginalVolume = clamped
        }
    }

    fun toggleOriginalMute() {
        if (_originalVolume.value > 0f) {
            setOriginalVolume(0f)
        } else {
            setOriginalVolume(lastNonZeroOriginalVolume.coerceAtLeast(0.20f))
        }
    }

    fun setVoiceoverVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _voiceoverVolume.value = clamped
        voiceoverPlayer.volume = clamped
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val params = PlaybackParameters(speed)
        videoPlayer.playbackParameters = params
        voiceoverPlayer.playbackParameters = params
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        isSubtitlesEnabled = enabled
        if (!enabled) {
            _currentSubtitle.value = null
        } else {
            updateSubtitles(videoPlayer.currentPosition)
        }
    }

    private fun updateSubtitles(posMs: Long) {
        if (!isSubtitlesEnabled || subtitleCues.isEmpty()) {
            if (_currentSubtitle.value != null) _currentSubtitle.value = null
            return
        }
        val match = subtitleCues.firstOrNull { posMs in it.startTimeMs..it.endTimeMs }
        _currentSubtitle.value = match?.text
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (videoPlayer.playbackState == Player.STATE_READY) {
                    val vPos = videoPlayer.currentPosition
                    _currentPositionMs.value = vPos
                    updateSubtitles(vPos)

                    // Drift check between master video and voiceover track
                    if (voiceoverPlayer.mediaItemCount > 0 && voiceoverPlayer.playbackState == Player.STATE_READY) {
                        val aPos = voiceoverPlayer.currentPosition
                        val delta = abs(vPos - aPos)
                        if (delta > 80) { // If drifted by more than 80ms, resync voiceover
                            voiceoverPlayer.seekTo(vPos)
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    fun release() {
        syncJob?.cancel()
        videoPlayer.release()
        voiceoverPlayer.release()
    }
}
