package com.vot.player.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import com.vot.player.MainActivity
import com.vot.player.data.model.SubtitleCue
import com.vot.player.data.model.UniversalVideoInfo
import com.vot.player.data.model.VideoQuality
import com.vot.player.service.VotMediaService
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
    private val scope: CoroutineScope,
    private val onPositionSaved: ((videoId: String, positionMs: Long, durationMs: Long) -> Unit)? = null
) {
    val videoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    val voiceoverPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private var mediaSession: MediaSession? = null

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

    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities.asStateFlow()

    private val _selectedQuality = MutableStateFlow<VideoQuality?>(null)
    val selectedQuality: StateFlow<VideoQuality?> = _selectedQuality.asStateFlow()

    private var currentVideoInfo: UniversalVideoInfo? = null
    private var currentVoiceoverAudioUrl: String? = null
    private var subtitleCues: List<SubtitleCue> = emptyList()
    private var isSubtitlesEnabled: Boolean = false
    private var syncJob: Job? = null
    private var lastNonZeroOriginalVolume: Float = 0.20f
    private val dataSourceFactory = DefaultDataSource.Factory(context)

    init {
        videoPlayer.volume = _originalVolume.value
        voiceoverPlayer.volume = _voiceoverVolume.value

        setupMediaSession()

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

    private fun setupMediaSession() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            mediaSession = MediaSession.Builder(context, videoPlayer)
                .setSessionActivity(pendingIntent)
                .setId("VotPlayerSession")
                .build()

            VotMediaService.activeSession = mediaSession

            val serviceIntent = Intent(context, VotMediaService::class.java)
            context.startService(serviceIntent)
        } catch (_: Exception) {
            // Service startup fallback
        }
    }

    fun prepare(
        videoInfo: UniversalVideoInfo,
        voiceoverAudioUrl: String?,
        subtitles: List<SubtitleCue> = emptyList(),
        startPositionMs: Long = 0L
    ) {
        currentVideoInfo = videoInfo
        currentVoiceoverAudioUrl = voiceoverAudioUrl
        subtitleCues = subtitles
        _availableQualities.value = videoInfo.availableQualities

        val defaultQuality = videoInfo.availableQualities.firstOrNull()
        _selectedQuality.value = defaultQuality

        setupVideoSource(
            videoUrl = defaultQuality?.videoUrl ?: videoInfo.streamUrl,
            audioUrl = defaultQuality?.audioUrl ?: videoInfo.directAudioUrl,
            metadata = MediaMetadata.Builder()
                .setTitle(videoInfo.title)
                .setArtist(videoInfo.author)
                .apply {
                    if (!videoInfo.thumbnailUrl.isNullOrEmpty()) {
                        setArtworkUri(Uri.parse(videoInfo.thumbnailUrl))
                    }
                }
                .build()
        )

        if (!voiceoverAudioUrl.isNullOrEmpty()) {
            val audioItem = MediaItem.fromUri(voiceoverAudioUrl)
            voiceoverPlayer.setMediaItem(audioItem)
            voiceoverPlayer.prepare()
        }

        if (startPositionMs > 0L) {
            seekTo(startPositionMs)
        }

        videoPlayer.playWhenReady = true
        voiceoverPlayer.playWhenReady = true
    }

    private fun setupVideoSource(videoUrl: String, audioUrl: String?, metadata: MediaMetadata) {
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaMetadata(metadata)
            .build()

        if (!audioUrl.isNullOrEmpty()) {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            val audioItem = MediaItem.fromUri(audioUrl)
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(audioItem)
            val mergedSource = MergingMediaSource(videoSource, audioSource)
            videoPlayer.setMediaSource(mergedSource)
        } else {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            videoPlayer.setMediaSource(videoSource)
        }
        videoPlayer.prepare()
    }

    fun changeQuality(quality: VideoQuality) {
        val currentPos = videoPlayer.currentPosition
        val wasPlaying = videoPlayer.isPlaying
        _selectedQuality.value = quality

        val info = currentVideoInfo ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(info.title)
            .setArtist(info.author)
            .apply {
                if (!info.thumbnailUrl.isNullOrEmpty()) {
                    setArtworkUri(Uri.parse(info.thumbnailUrl))
                }
            }
            .build()

        setupVideoSource(
            videoUrl = quality.videoUrl,
            audioUrl = quality.audioUrl ?: info.directAudioUrl,
            metadata = metadata
        )
        seekTo(currentPos)
        if (wasPlaying) {
            play()
        }
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
            var saveCounter = 0
            while (isActive) {
                if (videoPlayer.playbackState == Player.STATE_READY) {
                    val vPos = videoPlayer.currentPosition
                    _currentPositionMs.value = vPos
                    updateSubtitles(vPos)

                    // Resync voiceover if drifted by more than 80ms
                    if (voiceoverPlayer.mediaItemCount > 0 && voiceoverPlayer.playbackState == Player.STATE_READY) {
                        val aPos = voiceoverPlayer.currentPosition
                        val delta = abs(vPos - aPos)
                        if (delta > 80) {
                            voiceoverPlayer.seekTo(vPos)
                        }
                    }

                    // Save playback position to database periodically (every ~3 seconds)
                    saveCounter++
                    if (saveCounter >= 12) {
                        saveCounter = 0
                        currentVideoInfo?.let { info ->
                            onPositionSaved?.invoke(info.id, vPos, videoPlayer.duration)
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    fun release() {
        syncJob?.cancel()
        currentVideoInfo?.let { info ->
            onPositionSaved?.invoke(info.id, videoPlayer.currentPosition, videoPlayer.duration)
        }
        VotMediaService.activeSession = null
        mediaSession?.release()
        mediaSession = null
        videoPlayer.release()
        voiceoverPlayer.release()
    }
}
