package com.vot.player

import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.vot.player.data.db.WatchHistoryDatabase
import com.vot.player.data.db.WatchHistoryItem
import com.vot.player.data.extractor.MultiPlatformExtractor
import com.vot.player.data.model.*
import com.vot.player.data.pref.PlayerMode
import com.vot.player.data.pref.PlayerPreferences
import com.vot.player.data.sponsorblock.SponsorBlockClient
import com.vot.player.data.update.AppUpdateInfo
import com.vot.player.data.update.UpdateChecker
import com.vot.player.data.update.UpdateDownloadState
import com.vot.player.data.vot.VotApiClient
import com.vot.player.data.youtube.YouTubeStreamExtractor
import com.vot.player.export.ExportState
import com.vot.player.export.VotExportManager
import com.vot.player.player.VotPlayerManager
import com.vot.player.ui.HistoryScreen
import com.vot.player.ui.PlayerScreen
import com.vot.player.ui.YouTubeWebScreen
import com.vot.player.ui.components.ExportProgressDialog
import com.vot.player.ui.components.PlayerModeDialog
import com.vot.player.ui.components.SettingsDialog
import com.vot.player.ui.components.UpdateDownloadDialog
import android.content.pm.ActivityInfo
import android.widget.Toast
import com.vot.player.ui.theme.DarkBackground
import com.vot.player.ui.theme.VotPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "com.vot.player.EXTRA_VIDEO_URL"
    }

    private lateinit var playerManager: VotPlayerManager
    private lateinit var historyDb: WatchHistoryDatabase
    private lateinit var exportManager: VotExportManager
    private lateinit var prefs: PlayerPreferences

    private val streamExtractor = MultiPlatformExtractor()
    private val votApiClient = VotApiClient()
    private val sponsorBlockClient = SponsorBlockClient()
    private val updateChecker = UpdateChecker()

    private var activeVideoUrl by mutableStateOf<String?>(null)
    private var currentVideoInfo by mutableStateOf<UniversalVideoInfo?>(null)
    private var statusMessage by mutableStateOf<String?>(null)
    private var isLoading by mutableStateOf(false)
    private var historyList by mutableStateOf<List<WatchHistoryItem>>(emptyList())

    private var selectedVoiceType by mutableStateOf(VoiceType.STANDARD)
    private var selectedVoiceGender by mutableStateOf(VoiceGender.AUTO)
    private var selectedVoiceActor by mutableStateOf(VoiceActor.AUTO)
    private var selectedSubtitles by mutableStateOf(SubtitlesMode.OFF)
    private var selectedLanguage by mutableStateOf(TargetLanguage.RUSSIAN)
    private var isSponsorBlockEnabled by mutableStateOf(true)

    private var isLiveVoiceAvailable by mutableStateOf(true)
    private var hasSubtitles by mutableStateOf(false)
    private var isCustomVoiceSupported by mutableStateOf(false)

    private var currentTranslatedAudioUrl by mutableStateOf<String?>(null)
    private var currentPlayingMode by mutableStateOf(PlayerMode.NATIVE_PLAYER)
    private var preferredPlayerMode by mutableStateOf(PlayerMode.ASK_EVERY_TIME)
    private var pendingOpenUrl by mutableStateOf<String?>(null)
    private var pendingOpenPositionMs by mutableStateOf(0L)
    private var currentStartPositionMs by mutableStateOf(0L)
    private var showModeDialog by mutableStateOf(false)
    private var showSettingsFromHome by mutableStateOf(false)
    private var appUpdateInfo by mutableStateOf<AppUpdateInfo?>(null)
    private var detectedClipboardUrl by mutableStateOf<String?>(null)
    private var dismissedClipboardUrl by mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        checkClipboard()
    }

    private fun checkClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (clipText.isNotBlank() && clipText != dismissedClipboardUrl && clipText != activeVideoUrl) {
                if (YouTubeStreamExtractor.extractVideoId(clipText) != null) {
                    detectedClipboardUrl = clipText
                }
            }
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PlayerPreferences.getInstance(this)
        historyDb = WatchHistoryDatabase.getInstance(this)
        exportManager = VotExportManager(this)

        // Load preferences
        isSponsorBlockEnabled = prefs.isSponsorBlockEnabled
        selectedVoiceGender = VoiceGender.values().firstOrNull { it.code == prefs.preferredVoiceGender } ?: VoiceGender.AUTO
        selectedVoiceActor = VoiceActor.values().firstOrNull { it.voiceId == prefs.preferredVoiceActor } ?: VoiceActor.AUTO
        preferredPlayerMode = prefs.preferredPlayerMode
        selectedVoiceType = prefs.preferredVoiceType
        selectedSubtitles = prefs.preferredSubtitlesMode

        playerManager = VotPlayerManager(
            context = this,
            scope = lifecycleScope,
            onPositionSaved = { videoId, posMs, durationMs ->
                lifecycleScope.launch {
                    historyDb.updatePosition(videoId, posMs, durationMs)
                }
            }
        )
        playerManager.setSponsorBlockEnabled(isSponsorBlockEnabled)
        playerManager.onSponsorSkipped = { category ->
            Toast.makeText(this@MainActivity, "SponsorBlock: Skipped $category", Toast.LENGTH_SHORT).show()
        }

        loadHistory()
        checkUpdates()
        handleIntent(intent)

        setContent {
            VotPlayerTheme {
                val exportState by exportManager.exportState.collectAsState()
                var showExportDialog by remember { mutableStateOf(false) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var updateDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
                val managerHasSubtitles by playerManager.hasSubtitles.collectAsState()
                val effectiveHasSubtitles = hasSubtitles || managerHasSubtitles

                LaunchedEffect(exportState) {
                    if (exportState !is ExportState.Idle) {
                        showExportDialog = true
                    }
                }

                Scaffold(
                    containerColor = DarkBackground,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    if (activeVideoUrl != null && currentVideoInfo != null) {
                        val info = currentVideoInfo!!

                        if (currentPlayingMode == PlayerMode.YOUTUBE_WEB) {
                            // Mode 2: YouTube Web View with comments
                            val webUrl = if (info.platform == PlatformType.YOUTUBE) {
                                "https://m.youtube.com/watch?v=${info.id}"
                            } else info.rawUrl

                            YouTubeWebScreen(
                                videoUrl = webUrl,
                                startPositionMs = currentStartPositionMs,
                                playerManager = playerManager,
                                statusMessage = statusMessage,
                                currentTranslatedAudioUrl = currentTranslatedAudioUrl,
                                selectedVoiceType = selectedVoiceType,
                                onVoiceTypeChange = { newVoice ->
                                    selectedVoiceType = newVoice
                                    prefs.preferredVoiceType = newVoice
                                    activeVideoUrl?.let { openYouTubeWebView(it, playerManager.currentPositionMs.value) }
                                },
                                selectedSubtitles = selectedSubtitles,
                                onSubtitlesChange = { newSubs ->
                                    selectedSubtitles = newSubs
                                    prefs.preferredSubtitlesMode = newSubs
                                    playerManager.setSubtitlesEnabled(newSubs != SubtitlesMode.OFF)
                                },
                                selectedLanguage = selectedLanguage,
                                onLanguageChange = { newLang ->
                                    selectedLanguage = newLang
                                    activeVideoUrl?.let { openYouTubeWebView(it, playerManager.currentPositionMs.value) }
                                },
                                onSwitchToNativePlayer = {
                                    activeVideoUrl?.let { openNativePlayer(it, playerManager.currentPositionMs.value) }
                                },
                                onVideoUrlChanged = { newUrl ->
                                    val newId = YouTubeStreamExtractor.extractVideoId(newUrl)
                                    if (!newId.isNullOrEmpty() && newId != currentVideoInfo?.id) {
                                        saveCurrentPlaybackPosition()
                                        openYouTubeWebView(newUrl, 0L)
                                    }
                                },
                                onNavigateBack = {
                                    saveCurrentPlaybackPosition()
                                    playerManager.pause()
                                    playerManager.stopVideo()
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    activeVideoUrl = null
                                    currentVideoInfo = null
                                    lifecycleScope.launch {
                                        loadHistory()
                                    }
                                },
                                isLiveVoiceAvailable = isLiveVoiceAvailable,
                                hasSubtitles = effectiveHasSubtitles,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Mode 1: Native Player
                            PlayerScreen(
                                playerManager = playerManager,
                                videoTitle = info.title,
                                videoAuthor = info.author,
                                thumbnailUrl = info.thumbnailUrl,
                                embeddedUrl = if (info.streamUrl.startsWith("http")) info.streamUrl else null,
                                statusMessage = statusMessage,
                                isLoading = isLoading,
                                selectedVoiceType = selectedVoiceType,
                                onVoiceTypeChange = { newVoice ->
                                    selectedVoiceType = newVoice
                                    prefs.preferredVoiceType = newVoice
                                    activeVideoUrl?.let { openNativePlayer(it, playerManager.currentPositionMs.value) }
                                },
                                selectedVoiceGender = selectedVoiceGender,
                                onVoiceGenderChange = { gender ->
                                    selectedVoiceGender = gender
                                    prefs.preferredVoiceGender = gender.code
                                    activeVideoUrl?.let { openNativePlayer(it, playerManager.currentPositionMs.value) }
                                },
                                selectedVoiceActor = selectedVoiceActor,
                                onVoiceActorChange = { actor ->
                                    selectedVoiceActor = actor
                                    prefs.preferredVoiceActor = actor.voiceId
                                    activeVideoUrl?.let { openNativePlayer(it, playerManager.currentPositionMs.value) }
                                },
                                selectedSubtitles = selectedSubtitles,
                                onSubtitlesChange = { newSubs ->
                                    selectedSubtitles = newSubs
                                    prefs.preferredSubtitlesMode = newSubs
                                    playerManager.setSubtitlesEnabled(newSubs != SubtitlesMode.OFF)
                                },
                                selectedLanguage = selectedLanguage,
                                onLanguageChange = { newLang ->
                                    selectedLanguage = newLang
                                    activeVideoUrl?.let { openNativePlayer(it, playerManager.currentPositionMs.value) }
                                },
                                isSponsorBlockEnabled = isSponsorBlockEnabled,
                                onSponsorBlockChange = { enabled ->
                                    isSponsorBlockEnabled = enabled
                                    prefs.isSponsorBlockEnabled = enabled
                                    playerManager.setSponsorBlockEnabled(enabled)
                                },
                                preferredPlayerMode = preferredPlayerMode,
                                onPlayerModeChange = { mode ->
                                    preferredPlayerMode = mode
                                    prefs.preferredPlayerMode = mode
                                    if (mode == PlayerMode.YOUTUBE_WEB) {
                                        val curPos = playerManager.currentPositionMs.value
                                        activeVideoUrl?.let { openYouTubeWebView(it, curPos) }
                                    }
                                },
                                onEnterPiP = { enterPictureInPicture() },
                                onNavigateBack = {
                                    saveCurrentPlaybackPosition()
                                    playerManager.pause()
                                    playerManager.stopVideo()
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    activeVideoUrl = null
                                    currentVideoInfo = null
                                    lifecycleScope.launch {
                                        loadHistory()
                                    }
                                },
                                onExportVideo = {
                                    lifecycleScope.launch {
                                        showExportDialog = true
                                        exportManager.exportVideo(
                                            videoUrl = info.streamUrl,
                                            audioUrl = currentTranslatedAudioUrl,
                                            title = info.title
                                        )
                                    }
                                },
                                onExportAudio = {
                                    if (!currentTranslatedAudioUrl.isNullOrEmpty()) {
                                        lifecycleScope.launch {
                                            showExportDialog = true
                                            exportManager.exportAudioOnly(
                                                audioUrl = currentTranslatedAudioUrl!!,
                                                title = info.title,
                                                author = info.author
                                            )
                                        }
                                    }
                                },
                                isLiveVoiceAvailable = isLiveVoiceAvailable,
                                hasSubtitles = effectiveHasSubtitles,
                                isCustomVoiceSupported = isCustomVoiceSupported,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Home & Watch History Screen
                        HistoryScreen(
                            historyItems = historyList,
                            updateInfo = appUpdateInfo,
                            detectedClipboardUrl = detectedClipboardUrl,
                            onDismissClipboard = {
                                dismissedClipboardUrl = detectedClipboardUrl
                                detectedClipboardUrl = null
                            },
                            onDownloadUpdate = { updateInfo ->
                                showUpdateDialog = true
                                updateDownloadState = UpdateDownloadState.Idle
                                lifecycleScope.launch {
                                    updateChecker.downloadApk(this@MainActivity, updateInfo) { state ->
                                        updateDownloadState = state
                                    }
                                }
                            },
                            onOpenSettings = { showSettingsFromHome = true },
                            onPlayUrl = { url, startPos ->
                                detectedClipboardUrl = null
                                onUrlTriggered(url, startPos)
                            },
                            onDeleteItem = { videoId ->
                                lifecycleScope.launch {
                                    historyDb.deleteItem(videoId)
                                    loadHistory()
                                }
                            },
                            onClearAll = {
                                lifecycleScope.launch {
                                    historyDb.clearAll()
                                    loadHistory()
                                }
                            }
                        )
                    }

                    // Mode Choice Dialog (Native Player vs YouTube Web View)
                    if (showModeDialog && pendingOpenUrl != null) {
                        PlayerModeDialog(
                            onSelectMode = { mode, rememberChoice ->
                                if (rememberChoice) {
                                    preferredPlayerMode = mode
                                    prefs.preferredPlayerMode = mode
                                }
                                val url = pendingOpenUrl!!
                                val pos = pendingOpenPositionMs
                                showModeDialog = false
                                pendingOpenUrl = null
                                pendingOpenPositionMs = 0L
                                if (mode == PlayerMode.YOUTUBE_WEB) {
                                    openYouTubeWebView(url, pos)
                                } else {
                                    openNativePlayer(url, pos)
                                }
                            },
                            onDismiss = {
                                showModeDialog = false
                                pendingOpenUrl = null
                                pendingOpenPositionMs = 0L
                            }
                        )
                    }

                    // Home Settings Dialog
                    if (showSettingsFromHome) {
                        SettingsDialog(
                            selectedVoiceType = selectedVoiceType,
                            onVoiceTypeChange = { 
                                selectedVoiceType = it 
                                prefs.preferredVoiceType = it
                            },
                            selectedVoiceGender = selectedVoiceGender,
                            onVoiceGenderChange = { gender ->
                                selectedVoiceGender = gender
                                prefs.preferredVoiceGender = gender.code
                            },
                            selectedVoiceActor = selectedVoiceActor,
                            onVoiceActorChange = { actor ->
                                selectedVoiceActor = actor
                                prefs.preferredVoiceActor = actor.voiceId
                            },
                            selectedSubtitles = selectedSubtitles,
                            onSubtitlesChange = { 
                                selectedSubtitles = it
                                prefs.preferredSubtitlesMode = it
                                playerManager.setSubtitlesEnabled(it != SubtitlesMode.OFF)
                            },
                            selectedLanguage = selectedLanguage,
                            onLanguageChange = { selectedLanguage = it },
                            currentSpeed = playerManager.playbackSpeed.value,
                            onSpeedChange = { playerManager.setPlaybackSpeed(it) },
                            isSponsorBlockEnabled = isSponsorBlockEnabled,
                            onSponsorBlockChange = { enabled ->
                                isSponsorBlockEnabled = enabled
                                prefs.isSponsorBlockEnabled = enabled
                                playerManager.setSponsorBlockEnabled(enabled)
                            },
                            isAudioOnly = playerManager.isAudioOnly.value,
                            onToggleAudioOnly = { playerManager.toggleAudioOnly() },
                            preferredPlayerMode = preferredPlayerMode,
                            onPlayerModeChange = { mode ->
                                preferredPlayerMode = mode
                                prefs.preferredPlayerMode = mode
                            },
                            isLiveVoiceAvailable = isLiveVoiceAvailable,
                            hasSubtitles = effectiveHasSubtitles,
                            isCustomVoiceSupported = isCustomVoiceSupported,
                            onDismiss = { showSettingsFromHome = false }
                        )
                    }

                    // Export / Download Dialog
                    if (showExportDialog && exportState !is ExportState.Idle) {
                        ExportProgressDialog(
                            exportState = exportState,
                            onDismiss = {
                                showExportDialog = false
                                if (exportState is ExportState.Success || exportState is ExportState.Error) {
                                    exportManager.reset()
                                }
                            }
                        )
                    }

                    // In-App Update Dialog
                    if (showUpdateDialog && appUpdateInfo != null) {
                        UpdateDownloadDialog(
                            updateInfo = appUpdateInfo!!,
                            downloadState = updateDownloadState,
                            onInstall = { apkFile -> updateChecker.installApk(this@MainActivity, apkFile) },
                            onOpenBrowser = { updateChecker.openReleaseInBrowser(this@MainActivity, appUpdateInfo!!) },
                            onDismiss = {
                                showUpdateDialog = false
                                updateDownloadState = UpdateDownloadState.Idle
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkUpdates() {
        lifecycleScope.launch {
            val res = updateChecker.checkForUpdates()
            if (res.isSuccess) {
                appUpdateInfo = res.getOrNull()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            historyList = historyDb.getAllHistory()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_VIDEO_URL)
        if (url != null) {
            onUrlTriggered(url)
        }
    }

    private fun onUrlTriggered(url: String, requestedStartPositionMs: Long = 0L) {
        val mode = preferredPlayerMode
        if (mode == PlayerMode.ASK_EVERY_TIME) {
            pendingOpenUrl = url
            pendingOpenPositionMs = requestedStartPositionMs
            showModeDialog = true
        } else if (mode == PlayerMode.YOUTUBE_WEB) {
            openYouTubeWebView(url, requestedStartPositionMs)
        } else {
            openNativePlayer(url, requestedStartPositionMs)
        }
    }

    private fun openYouTubeWebView(rawUrl: String, requestedStartPositionMs: Long = 0L) {
        playerManager.stopVideo()
        currentStartPositionMs = requestedStartPositionMs
        val videoId = YouTubeStreamExtractor.extractVideoId(rawUrl) ?: ""
        val startSec = requestedStartPositionMs / 1000L
        val webUrl = if (videoId.isNotEmpty()) {
            if (startSec > 0) "https://m.youtube.com/watch?v=$videoId&t=${startSec}s"
            else "https://m.youtube.com/watch?v=$videoId"
        } else {
            rawUrl
        }
        if (requestedStartPositionMs > 0L) {
            playerManager.syncWebPosition(requestedStartPositionMs, 0L)
        }

        val info = UniversalVideoInfo(
            id = videoId.ifEmpty { "yt_${rawUrl.hashCode()}" },
            rawUrl = rawUrl,
            title = "YouTube Video",
            author = "YouTube",
            durationSeconds = 0L,
            streamUrl = webUrl,
            thumbnailUrl = if (videoId.isNotEmpty()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null,
            platform = PlatformType.YOUTUBE
        )

        currentPlayingMode = PlayerMode.YOUTUBE_WEB
        currentVideoInfo = info
        activeVideoUrl = rawUrl
        isLoading = true
        statusMessage = "Loading translation..."

        lifecycleScope.launch {
            val preferredVoiceParam = selectedVoiceActor.voiceId.ifEmpty { selectedVoiceGender.code }
            val votUrl = if (videoId.isNotEmpty()) "https://www.youtube.com/watch?v=$videoId" else rawUrl

            if (isSponsorBlockEnabled && videoId.isNotEmpty()) {
                launch {
                    val segments = sponsorBlockClient.getSkipSegments(videoId)
                    playerManager.setSponsorSegments(segments)
                }
            }

            val votResult = votApiClient.translateVideo(
                videoUrl = votUrl,
                durationSeconds = 0.0,
                targetLang = selectedLanguage,
                voiceType = selectedVoiceType,
                preferredVoice = preferredVoiceParam,
                onProgress = { statusMessage = it }
            )

            val subtitlesResult = votApiClient.getSubtitles(
                videoUrl = votUrl,
                targetLang = selectedLanguage
            )
            val cues = subtitlesResult.getOrDefault(emptyList())
            hasSubtitles = cues.isNotEmpty()

            val audioUrl = votResult.getOrNull()?.url
            currentTranslatedAudioUrl = audioUrl
            val votObj = votResult.getOrNull()
            if (votObj != null) {
                if (votObj.message?.contains("обычная озвучка") == true) {
                    isLiveVoiceAvailable = false
                    if (selectedVoiceType == VoiceType.LIVE_VOICE) {
                        selectedVoiceType = VoiceType.STANDARD
                        prefs.preferredVoiceType = VoiceType.STANDARD
                    }
                } else {
                    isLiveVoiceAvailable = true
                }
            }
            isCustomVoiceSupported = false
            isLoading = false
            if (votResult.isSuccess) {
                statusMessage = "Translation active (Russian)"
                launch {
                    delay(2500L)
                    if (statusMessage == "Translation active (Russian)") {
                        statusMessage = null
                    }
                }
            } else {
                statusMessage = votResult.exceptionOrNull()?.message ?: "Translation unavailable"
            }

            playerManager.setSubtitles(cues)
            playerManager.setSubtitlesEnabled(selectedSubtitles != SubtitlesMode.OFF)

            if (!audioUrl.isNullOrEmpty()) {
                playerManager.setVoiceoverAudio(audioUrl)
            }

            historyDb.saveOrUpdate(
                WatchHistoryItem(
                    videoId = info.id,
                    url = rawUrl,
                    title = info.title,
                    thumbnailUrl = info.thumbnailUrl ?: "",
                    lastPositionMs = requestedStartPositionMs,
                    durationMs = 0L,
                    preferredVoice = selectedVoiceType.name.lowercase(),
                    originalVolume = playerManager.originalVolume.value,
                    voiceVolume = playerManager.voiceoverVolume.value
                )
            )
            loadHistory()
        }
    }

    private fun openNativePlayer(rawUrl: String, requestedStartPositionMs: Long = 0L) {
        currentPlayingMode = PlayerMode.NATIVE_PLAYER
        activeVideoUrl = rawUrl
        isLoading = true
        statusMessage = "Resolving video stream..."

        val videoId = YouTubeStreamExtractor.extractVideoId(rawUrl)
        if (videoId != null) {
            currentVideoInfo = UniversalVideoInfo(
                id = videoId,
                rawUrl = rawUrl,
                title = "YouTube Video",
                author = "YouTube",
                durationSeconds = 0L,
                streamUrl = "",
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                platform = PlatformType.YOUTUBE
            )
        }

        lifecycleScope.launch {
            val streamResult = streamExtractor.extract(rawUrl)
            if (streamResult.isFailure) {
                playerManager.stopVideo()
                if (videoId != null) {
                    val webUrl = "https://m.youtube.com/watch?v=$videoId"
                    val fallbackInfo = UniversalVideoInfo(
                        id = videoId,
                        rawUrl = rawUrl,
                        title = "YouTube Video",
                        author = "YouTube",
                        durationSeconds = 0L,
                        streamUrl = webUrl,
                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        platform = PlatformType.YOUTUBE
                    )
                    currentVideoInfo = fallbackInfo
                    statusMessage = "Requesting Russian voice-over translation..."

                    if (isSponsorBlockEnabled) {
                        launch {
                            val segments = sponsorBlockClient.getSkipSegments(videoId)
                            playerManager.setSponsorSegments(segments)
                        }
                    }

                    val preferredVoiceParam = selectedVoiceActor.voiceId.ifEmpty { selectedVoiceGender.code }
                    val votUrl = "https://www.youtube.com/watch?v=$videoId"

                    val votResult = votApiClient.translateVideo(
                        videoUrl = votUrl,
                        durationSeconds = 0.0,
                        targetLang = selectedLanguage,
                        voiceType = selectedVoiceType,
                        preferredVoice = preferredVoiceParam,
                        onProgress = { statusMessage = it }
                    )

                    val subtitlesResult = votApiClient.getSubtitles(
                        videoUrl = votUrl,
                        targetLang = selectedLanguage
                    )
                    val cues = subtitlesResult.getOrDefault(emptyList())
                    hasSubtitles = cues.isNotEmpty()

                    val resumePositionMs = if (requestedStartPositionMs > 0L) {
                        requestedStartPositionMs
                    } else {
                        val existing = historyDb.getHistoryItem(fallbackInfo.id)
                        val lastPos = existing?.lastPositionMs ?: 0L
                        val dur = existing?.durationMs ?: 0L
                        if (dur > 0L && (lastPos >= dur || (dur - lastPos) < 10_000L)) 0L else lastPos
                    }
                    if (resumePositionMs > 0L) {
                        playerManager.syncWebPosition(resumePositionMs, 0L)
                    }

                    isLoading = false
                    val translatedAudioUrl = votResult.getOrNull()?.url
                    currentTranslatedAudioUrl = translatedAudioUrl
                    val votObj = votResult.getOrNull()
                    if (votObj != null) {
                        if (votObj.message?.contains("обычная озвучка") == true) {
                            isLiveVoiceAvailable = false
                            if (selectedVoiceType == VoiceType.LIVE_VOICE) {
                                selectedVoiceType = VoiceType.STANDARD
                                prefs.preferredVoiceType = VoiceType.STANDARD
                            }
                        } else {
                            isLiveVoiceAvailable = true
                        }
                    }
                    isCustomVoiceSupported = false
                    if (votResult.isSuccess) {
                        statusMessage = "Translation active (Russian)"
                        launch {
                            delay(2500L)
                            if (statusMessage == "Translation active (Russian)") {
                                statusMessage = null
                            }
                        }
                    } else {
                        statusMessage = votResult.exceptionOrNull()?.message ?: "Translation unavailable"
                    }

                    playerManager.setSubtitles(cues)
                    playerManager.setSubtitlesEnabled(selectedSubtitles != SubtitlesMode.OFF)

                    if (!translatedAudioUrl.isNullOrEmpty()) {
                        playerManager.setVoiceoverAudio(translatedAudioUrl)
                    }

                    historyDb.saveOrUpdate(
                        WatchHistoryItem(
                            videoId = fallbackInfo.id,
                            url = rawUrl,
                            title = fallbackInfo.title,
                            thumbnailUrl = fallbackInfo.thumbnailUrl ?: "",
                            lastPositionMs = resumePositionMs,
                            durationMs = 0L,
                            preferredVoice = selectedVoiceType.name.lowercase(),
                            originalVolume = playerManager.originalVolume.value,
                            voiceVolume = playerManager.voiceoverVolume.value
                        )
                    )
                    loadHistory()
                    return@launch
                }
                isLoading = false
                statusMessage = "Error: ${streamResult.exceptionOrNull()?.message}"
                return@launch
            }

            val videoInfo = streamResult.getOrThrow()
            currentVideoInfo = videoInfo
            statusMessage = "Requesting AI voice-over translation..."

            if (isSponsorBlockEnabled && videoInfo.platform == PlatformType.YOUTUBE) {
                launch {
                    val segments = sponsorBlockClient.getSkipSegments(videoInfo.id)
                    playerManager.setSponsorSegments(segments)
                }
            }

            val resumePositionMs = if (requestedStartPositionMs > 0L) {
                requestedStartPositionMs
            } else {
                val existing = historyDb.getHistoryItem(videoInfo.id)
                val lastPos = existing?.lastPositionMs ?: 0L
                val dur = existing?.durationMs ?: (videoInfo.durationSeconds * 1000L)
                if (dur > 0L && (lastPos >= dur || (dur - lastPos) < 10_000L)) 0L else lastPos
            }

            // Start native video playback immediately with ExoPlayer
            playerManager.prepare(
                videoInfo = videoInfo,
                voiceoverAudioUrl = null,
                subtitles = emptyList(),
                startPositionMs = resumePositionMs
            )

            val preferredVoiceParam = selectedVoiceActor.voiceId.ifEmpty { selectedVoiceGender.code }
            val votUrl = if (videoInfo.platform == PlatformType.YOUTUBE) {
                "https://www.youtube.com/watch?v=${videoInfo.id}"
            } else {
                videoInfo.rawUrl
            }

            val votResult = votApiClient.translateVideo(
                videoUrl = votUrl,
                durationSeconds = videoInfo.durationSeconds.toDouble(),
                targetLang = selectedLanguage,
                voiceType = selectedVoiceType,
                preferredVoice = preferredVoiceParam,
                onProgress = { statusMessage = it }
            )

            val subtitlesResult = votApiClient.getSubtitles(
                videoUrl = votUrl,
                targetLang = selectedLanguage
            )
            val cues = subtitlesResult.getOrDefault(emptyList())
            hasSubtitles = cues.isNotEmpty()

            val translatedAudioUrl = votResult.getOrNull()?.url
            currentTranslatedAudioUrl = translatedAudioUrl
            val votObj = votResult.getOrNull()
            if (votObj != null) {
                if (votObj.message?.contains("обычная озвучка") == true) {
                    isLiveVoiceAvailable = false
                    if (selectedVoiceType == VoiceType.LIVE_VOICE) {
                        selectedVoiceType = VoiceType.STANDARD
                        prefs.preferredVoiceType = VoiceType.STANDARD
                    }
                } else {
                    isLiveVoiceAvailable = true
                }
            }
            isCustomVoiceSupported = false
            isLoading = false
            if (votResult.isSuccess) {
                statusMessage = "Translation active (Russian)"
                launch {
                    delay(2500L)
                    if (statusMessage == "Translation active (Russian)") {
                        statusMessage = null
                    }
                }
            } else {
                statusMessage = votResult.exceptionOrNull()?.message ?: "Translation unavailable"
            }

            val currentPos = playerManager.currentPositionMs.value.takeIf { it > 0L } ?: resumePositionMs
            historyDb.saveOrUpdate(
                WatchHistoryItem(
                    videoId = videoInfo.id,
                    url = rawUrl,
                    title = videoInfo.title,
                    thumbnailUrl = videoInfo.thumbnailUrl ?: "",
                    lastPositionMs = currentPos,
                    durationMs = videoInfo.durationSeconds * 1000L,
                    preferredVoice = selectedVoiceType.name.lowercase(),
                    originalVolume = playerManager.originalVolume.value,
                    voiceVolume = playerManager.voiceoverVolume.value
                )
            )
            loadHistory()

            if (!translatedAudioUrl.isNullOrEmpty()) {
                playerManager.setVoiceoverAudio(translatedAudioUrl)
            }
            playerManager.setSubtitles(cues)
            playerManager.setSubtitlesEnabled(selectedSubtitles != SubtitlesMode.OFF)
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (activeVideoUrl != null && playerManager.isPlaying.value) {
            enterPictureInPicture()
        }
    }

    private fun saveCurrentPlaybackPosition() {
        val info = currentVideoInfo ?: return
        val pos = playerManager.currentPositionMs.value.takeIf { it > 0L }
            ?: playerManager.videoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = playerManager.durationMs.value.takeIf { it > 0L }
            ?: playerManager.videoPlayer.duration.coerceAtLeast(0L)
        if (pos > 0L) {
            lifecycleScope.launch {
                historyDb.updatePosition(info.id, pos, dur)
                loadHistory()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentPlaybackPosition()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentPlaybackPosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentPlaybackPosition()
        playerManager.release()
    }
}
