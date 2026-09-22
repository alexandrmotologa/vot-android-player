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
import androidx.media3.common.C
import androidx.media3.session.MediaSession
import com.vot.player.MainActivity
import com.vot.player.data.model.SubtitleCue
import com.vot.player.data.model.SubtitlesMode
import com.vot.player.data.model.UniversalVideoInfo
import com.vot.player.data.model.VideoQuality
import com.vot.player.data.sponsorblock.SponsorSegment
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

    private val _hasSubtitles = MutableStateFlow(false)
    val hasSubtitles: StateFlow<Boolean> = _hasSubtitles.asStateFlow()

    private val _hasRussianSubtitles = MutableStateFlow(false)
    val hasRussianSubtitles: StateFlow<Boolean> = _hasRussianSubtitles.asStateFlow()

    private val _hasRomanianSubtitles = MutableStateFlow(false)
    val hasRomanianSubtitles: StateFlow<Boolean> = _hasRomanianSubtitles.asStateFlow()

    private val _hasEnglishSubtitles = MutableStateFlow(false)
    val hasEnglishSubtitles: StateFlow<Boolean> = _hasEnglishSubtitles.asStateFlow()

    private val _subtitlesMode = MutableStateFlow(SubtitlesMode.OFF)
    val subtitlesMode: StateFlow<SubtitlesMode> = _subtitlesMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentSubtitle = MutableStateFlow<String?>(null)
    val currentSubtitle: StateFlow<String?> = _currentSubtitle.asStateFlow()

    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities.asStateFlow()

    private val _selectedQuality = MutableStateFlow<VideoQuality?>(null)
    val selectedQuality: StateFlow<VideoQuality?> = _selectedQuality.asStateFlow()

    private val _isAudioOnly = MutableStateFlow(false)
    val isAudioOnly: StateFlow<Boolean> = _isAudioOnly.asStateFlow()

    private var sponsorSegments: List<SponsorSegment> = emptyList()
    private var isSponsorBlockEnabled: Boolean = true
    var onSponsorSkipped: ((category: String) -> Unit)? = null

    private var currentVideoInfo: UniversalVideoInfo? = null
    private var currentVoiceoverAudioUrl: String? = null
    private var russianSubtitleCues: List<SubtitleCue> = emptyList()
    private var romanianSubtitleCues: List<SubtitleCue> = emptyList()
    private var englishSubtitleCues: List<SubtitleCue> = emptyList()
    private var subtitleCues: List<SubtitleCue> = emptyList()
    private var currentSubtitlesMode: SubtitlesMode = SubtitlesMode.OFF
    private var isSubtitlesEnabled: Boolean = false
    private var syncJob: Job? = null
    private var lastNonZeroOriginalVolume: Float = 0.20f
    private val upstreamDataSourceFactory = DefaultDataSource.Factory(context)
    private val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
        .setCache(VotMediaCache.getCache(context))
        .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
        .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    interface WebVideoController {
        fun play()
        fun pause()
        fun seekTo(positionMs: Long)
        fun setVolume(volume: Float)
        fun setPlaybackSpeed(speed: Float)
    }

    var webVideoController: WebVideoController? = null

    init {
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        videoPlayer.setAudioAttributes(audioAttributes, true)

        val speechAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        voiceoverPlayer.setAudioAttributes(speechAttributes, false)

        videoPlayer.volume = _originalVolume.value
        voiceoverPlayer.volume = _voiceoverVolume.value

        videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()

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
        _hasSubtitles.value = subtitles.isNotEmpty()
        _availableQualities.value = videoInfo.availableQualities
        _currentPositionMs.value = startPositionMs
        if (videoInfo.durationSeconds > 0L) {
            _durationMs.value = videoInfo.durationSeconds * 1000L
        } else {
            _durationMs.value = 0L
        }

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

        videoPlayer.volume = _originalVolume.value

        if (!voiceoverAudioUrl.isNullOrEmpty()) {
            val audioItem = MediaItem.fromUri(voiceoverAudioUrl)
            val audioSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(audioItem)
            voiceoverPlayer.setMediaSource(audioSource)
            voiceoverPlayer.prepare()
        }
        if (startPositionMs > 0L) {
            _currentPositionMs.value = startPositionMs
            videoPlayer.seekTo(startPositionMs)
            if (voiceoverPlayer.mediaItemCount > 0) {
                voiceoverPlayer.seekTo(startPositionMs)
            }
        }

        videoPlayer.playWhenReady = true
        voiceoverPlayer.playWhenReady = true
    }

    fun setVoiceoverAudio(audioUrl: String?, startPlaying: Boolean = true, isInitialStart: Boolean = false) {
        currentVoiceoverAudioUrl = audioUrl
        if (!audioUrl.isNullOrEmpty()) {
            val audioItem = MediaItem.fromUri(audioUrl)
            val audioSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(audioItem)
            voiceoverPlayer.setMediaSource(audioSource)
            voiceoverPlayer.volume = _voiceoverVolume.value
            voiceoverPlayer.prepare()

            val currentPos = if (videoPlayer.mediaItemCount > 0 && videoPlayer.currentPosition > 0L) {
                videoPlayer.currentPosition
            } else {
                _currentPositionMs.value
            }

            // If initial start (video opened from 0:00) and video ran ahead < 8 seconds while translation was loading:
            // Sync both back to 0:00 so user never misses the start of speech!
            if (isInitialStart && currentPos < 8000L) {
                seekTo(0L)
            } else if (currentPos > 0L) {
                voiceoverPlayer.seekTo(currentPos)
            }

            if (startPlaying || _isPlaying.value) {
                voiceoverPlayer.playWhenReady = true
                voiceoverPlayer.play()
                _isPlaying.value = true
            }
        } else {
            voiceoverPlayer.stop()
            voiceoverPlayer.clearMediaItems()
        }
    }

    fun syncWebPosition(webPositionMs: Long, durationMs: Long = 0L) {
        _currentPositionMs.value = webPositionMs
        if (durationMs > 0L) {
            _durationMs.value = durationMs
        }
        updateSubtitles(webPositionMs)
        if (voiceoverPlayer.mediaItemCount > 0 && voiceoverPlayer.playbackState == Player.STATE_READY) {
            val aPos = voiceoverPlayer.currentPosition
            val delta = webPositionMs - aPos
            val absDelta = kotlin.math.abs(delta)
            val baseSpeed = _playbackSpeed.value
            if (absDelta > 1500L) {
                voiceoverPlayer.seekTo(webPositionMs)
                if (voiceoverPlayer.playbackParameters.speed != baseSpeed) {
                    voiceoverPlayer.playbackParameters = PlaybackParameters(baseSpeed)
                }
            } else if (absDelta > 250L) {
                val adjustedSpeed = if (delta > 0) (baseSpeed * 1.04f).coerceAtMost(2.0f) else (baseSpeed * 0.96f).coerceAtLeast(0.25f)
                if (voiceoverPlayer.playbackParameters.speed != adjustedSpeed) {
                    voiceoverPlayer.playbackParameters = PlaybackParameters(adjustedSpeed)
                }
            } else {
                if (voiceoverPlayer.playbackParameters.speed != baseSpeed) {
                    voiceoverPlayer.playbackParameters = PlaybackParameters(baseSpeed)
                }
            }
            if (!_isPlaying.value && voiceoverPlayer.isPlaying) {
                voiceoverPlayer.pause()
            }
        }
    }

    fun syncWebSeek(webPositionMs: Long) {
        _currentPositionMs.value = webPositionMs
        updateSubtitles(webPositionMs)
        if (voiceoverPlayer.mediaItemCount > 0) {
            voiceoverPlayer.seekTo(webPositionMs)
        }
    }

    private fun setupVideoSource(videoUrl: String, audioUrl: String?, metadata: MediaMetadata) {
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaMetadata(metadata)
            .build()

        if (!audioUrl.isNullOrEmpty()) {
            val videoSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem)
            val audioItem = MediaItem.fromUri(audioUrl)
            val audioSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(audioItem)
            val mergedSource = MergingMediaSource(videoSource, audioSource)
            videoPlayer.setMediaSource(mergedSource)
        } else {
            val videoSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(mediaItem)
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
        if (wasPlaying) play()
    }

    fun stopVideo() {
        val pos = if (videoPlayer.mediaItemCount > 0 && videoPlayer.currentPosition > 0L) {
            videoPlayer.currentPosition
        } else {
            _currentPositionMs.value
        }
        currentVideoInfo?.let { info ->
            if (pos > 0L) {
                val dur = if (_durationMs.value > 0L) _durationMs.value else videoPlayer.duration.coerceAtLeast(0L)
                onPositionSaved?.invoke(info.id, pos, dur)
            }
        }
        videoPlayer.stop()
        videoPlayer.clearMediaItems()
        voiceoverPlayer.stop()
        voiceoverPlayer.clearMediaItems()
        russianSubtitleCues = emptyList()
        romanianSubtitleCues = emptyList()
        englishSubtitleCues = emptyList()
        subtitleCues = emptyList()
        _hasRussianSubtitles.value = false
        _hasRomanianSubtitles.value = false
        _hasEnglishSubtitles.value = false
        _hasSubtitles.value = false
        _currentSubtitle.value = null
        setPlaybackSpeed(1.0f)
    }

    fun play() {
        _isPlaying.value = true
        if (videoPlayer.mediaItemCount > 0) {
            videoPlayer.play()
        }
        webVideoController?.play()
        if (voiceoverPlayer.mediaItemCount > 0) {
            voiceoverPlayer.playWhenReady = true
            voiceoverPlayer.play()
        }
    }

    fun pause() {
        _isPlaying.value = false
        if (videoPlayer.mediaItemCount > 0) {
            videoPlayer.pause()
        }
        webVideoController?.pause()
        if (voiceoverPlayer.mediaItemCount > 0) {
            voiceoverPlayer.pause()
        }
        val pos = if (videoPlayer.mediaItemCount > 0 && videoPlayer.currentPosition > 0L) {
            videoPlayer.currentPosition
        } else {
            _currentPositionMs.value
        }
        _currentPositionMs.value = pos
        currentVideoInfo?.let { info ->
            val dur = if (_durationMs.value > 0L) _durationMs.value else videoPlayer.duration.coerceAtLeast(0L)
            onPositionSaved?.invoke(info.id, pos, dur)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val maxDuration = _durationMs.value.takeIf { it > 0L }
            ?: videoPlayer.duration.takeIf { it > 0L }
            ?: voiceoverPlayer.duration.takeIf { it > 0L }
            ?: Long.MAX_VALUE
        val target = positionMs.coerceIn(0L, maxDuration)
        if (videoPlayer.mediaItemCount > 0) {
            videoPlayer.seekTo(target)
        }
        webVideoController?.seekTo(target)
        if (voiceoverPlayer.mediaItemCount > 0) {
            voiceoverPlayer.seekTo(target)
        }
        _currentPositionMs.value = target
        updateSubtitles(target)
    }

    fun seekRelative(deltaMs: Long) {
        val currentPos = if (webVideoController != null) {
            _currentPositionMs.value
        } else if (videoPlayer.mediaItemCount > 0 && videoPlayer.currentPosition > 0L) {
            videoPlayer.currentPosition
        } else {
            _currentPositionMs.value
        }
        val maxDuration = _durationMs.value.takeIf { it > 0L }
            ?: videoPlayer.duration.takeIf { it > 0L }
            ?: voiceoverPlayer.duration.takeIf { it > 0L }
            ?: Long.MAX_VALUE
        val target = (currentPos + deltaMs).coerceIn(0L, maxDuration)
        seekTo(target)
    }

    fun setOriginalVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _originalVolume.value = clamped
        videoPlayer.volume = clamped
        webVideoController?.setVolume(clamped)
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
        val clamped = speed.coerceIn(0.25f, 2.0f)
        _playbackSpeed.value = clamped
        val params = PlaybackParameters(clamped)
        videoPlayer.playbackParameters = params
        voiceoverPlayer.playbackParameters = params
        webVideoController?.setPlaybackSpeed(clamped)
    }

    fun setMultiSubtitles(ruCues: List<SubtitleCue>, roCues: List<SubtitleCue>, enCues: List<SubtitleCue>) {
        russianSubtitleCues = ruCues
        romanianSubtitleCues = roCues
        englishSubtitleCues = enCues
        subtitleCues = ruCues.ifEmpty { roCues }.ifEmpty { enCues }
        _hasRussianSubtitles.value = ruCues.isNotEmpty()
        _hasRomanianSubtitles.value = roCues.isNotEmpty()
        _hasEnglishSubtitles.value = enCues.isNotEmpty()
        _hasSubtitles.value = ruCues.isNotEmpty() || roCues.isNotEmpty() || enCues.isNotEmpty()
        updateSubtitles(_currentPositionMs.value)
    }

    fun setDualSubtitles(ruCues: List<SubtitleCue>, enCues: List<SubtitleCue>) {
        setMultiSubtitles(ruCues, emptyList(), enCues)
    }

    fun setSubtitles(cues: List<SubtitleCue>) {
        russianSubtitleCues = cues
        subtitleCues = cues
        _hasRussianSubtitles.value = cues.isNotEmpty()
        _hasSubtitles.value = cues.isNotEmpty() || romanianSubtitleCues.isNotEmpty() || englishSubtitleCues.isNotEmpty()
        updateSubtitles(_currentPositionMs.value)
    }

    fun setSubtitlesMode(mode: SubtitlesMode) {
        currentSubtitlesMode = mode
        isSubtitlesEnabled = (mode != SubtitlesMode.OFF)
        _subtitlesMode.value = mode
        updateSubtitles(_currentPositionMs.value)
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        isSubtitlesEnabled = enabled
        if (!enabled) {
            currentSubtitlesMode = SubtitlesMode.OFF
        } else if (currentSubtitlesMode == SubtitlesMode.OFF) {
            currentSubtitlesMode = when {
                _hasRussianSubtitles.value -> SubtitlesMode.RUSSIAN
                _hasRomanianSubtitles.value -> SubtitlesMode.ROMANIAN
                _hasEnglishSubtitles.value -> SubtitlesMode.ENGLISH
                else -> SubtitlesMode.OFF
            }
        }
        _subtitlesMode.value = currentSubtitlesMode
        updateSubtitles(_currentPositionMs.value)
    }

    private fun updateSubtitles(posMs: Long) {
        if (!isSubtitlesEnabled || currentSubtitlesMode == SubtitlesMode.OFF) {
            if (_currentSubtitle.value != null) _currentSubtitle.value = null
            return
        }
        val activeCues = when (currentSubtitlesMode) {
            SubtitlesMode.OFF -> emptyList()
            SubtitlesMode.RUSSIAN -> russianSubtitleCues.ifEmpty { subtitleCues }
            SubtitlesMode.ROMANIAN -> romanianSubtitleCues.ifEmpty { subtitleCues }
            SubtitlesMode.ENGLISH -> englishSubtitleCues
        }
        if (activeCues.isEmpty()) {
            if (_currentSubtitle.value != null) _currentSubtitle.value = null
            return
        }
        val match = activeCues.firstOrNull { posMs in it.startTimeMs..it.endTimeMs }
        _currentSubtitle.value = match?.text
    }

    fun setSponsorSegments(segments: List<SponsorSegment>) {
        sponsorSegments = segments
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        isSponsorBlockEnabled = enabled
    }

    fun toggleAudioOnly() {
        _isAudioOnly.value = !_isAudioOnly.value
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

                    // Auto-skip SponsorBlock segments
                    if (isSponsorBlockEnabled && sponsorSegments.isNotEmpty()) {
                        val segment = sponsorSegments.firstOrNull { vPos in it.startTimeMs..(it.endTimeMs - 500L) }
                        if (segment != null) {
                            seekTo(segment.endTimeMs)
                            onSponsorSkipped?.invoke(segment.category)
                        }
                    }

                    // Intelligent smooth voiceover drift synchronization (prevents choppy/clipped words)
                    if (voiceoverPlayer.mediaItemCount > 0 && voiceoverPlayer.playbackState == Player.STATE_READY) {
                        val aPos = voiceoverPlayer.currentPosition
                        val delta = vPos - aPos
                        val absDelta = abs(delta)
                        val baseSpeed = _playbackSpeed.value

                        if (absDelta > 1200L) {
                            // Large desync or seek jump: seek directly to video position
                            voiceoverPlayer.seekTo(vPos)
                            if (voiceoverPlayer.playbackParameters.speed != baseSpeed) {
                                voiceoverPlayer.playbackParameters = PlaybackParameters(baseSpeed)
                            }
                        } else if (absDelta > 200L) {
                            // Mild drift (200ms - 1200ms): gently adjust playback speed to converge smoothly
                            // without flushing audio buffers or cutting off spoken words
                            val adjustedSpeed = if (delta > 0) {
                                (baseSpeed * 1.04f).coerceAtMost(2.0f)
                            } else {
                                (baseSpeed * 0.96f).coerceAtLeast(0.25f)
                            }
                            if (voiceoverPlayer.playbackParameters.speed != adjustedSpeed) {
                                voiceoverPlayer.playbackParameters = PlaybackParameters(adjustedSpeed)
                            }
                        } else {
                            // Within acceptable tolerance (<= 200ms): keep exact matching speed
                            if (voiceoverPlayer.playbackParameters.speed != baseSpeed) {
                                voiceoverPlayer.playbackParameters = PlaybackParameters(baseSpeed)
                            }
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
