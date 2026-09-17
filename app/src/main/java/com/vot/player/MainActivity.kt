package com.vot.player

import android.app.PictureInPictureParams
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
import com.vot.player.data.model.PlatformType
import com.vot.player.data.model.SubtitlesMode
import com.vot.player.data.model.TargetLanguage
import com.vot.player.data.model.UniversalVideoInfo
import com.vot.player.data.model.VoiceType
import com.vot.player.data.vot.VotApiClient
import com.vot.player.export.ExportState
import com.vot.player.export.VotExportManager
import com.vot.player.player.VotPlayerManager
import com.vot.player.ui.HistoryScreen
import com.vot.player.ui.PlayerScreen
import com.vot.player.ui.components.ExportProgressDialog
import com.vot.player.ui.theme.DarkBackground
import com.vot.player.ui.theme.VotPlayerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "com.vot.player.EXTRA_VIDEO_URL"
    }

    private lateinit var playerManager: VotPlayerManager
    private lateinit var historyDb: WatchHistoryDatabase
    private lateinit var exportManager: VotExportManager
    private val streamExtractor = MultiPlatformExtractor()
    private val votApiClient = VotApiClient()

    private var activeVideoUrl by mutableStateOf<String?>(null)
    private var currentVideoInfo by mutableStateOf<UniversalVideoInfo?>(null)
    private var statusMessage by mutableStateOf<String?>(null)
    private var isLoading by mutableStateOf(false)
    private var historyList by mutableStateOf<List<WatchHistoryItem>>(emptyList())

    private var selectedVoiceType by mutableStateOf(VoiceType.STANDARD)
    private var selectedSubtitles by mutableStateOf(SubtitlesMode.OFF)
    private var selectedLanguage by mutableStateOf(TargetLanguage.RUSSIAN)
    private var currentTranslatedAudioUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyDb = WatchHistoryDatabase.getInstance(this)
        exportManager = VotExportManager(this)

        playerManager = VotPlayerManager(
            context = this,
            scope = lifecycleScope,
            onPositionSaved = { videoId, posMs, durationMs ->
                lifecycleScope.launch {
                    historyDb.updatePosition(videoId, posMs, durationMs)
                }
            }
        )

        loadHistory()
        handleIntent(intent)

        setContent {
            VotPlayerTheme {
                val exportState by exportManager.exportState.collectAsState()
                var showExportDialog by remember { mutableStateOf(false) }

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
                        PlayerScreen(
                            playerManager = playerManager,
                            videoTitle = info.title,
                            videoAuthor = info.author,
                            statusMessage = statusMessage,
                            isLoading = isLoading,
                            selectedVoiceType = selectedVoiceType,
                            onVoiceTypeChange = { newVoice ->
                                selectedVoiceType = newVoice
                                activeVideoUrl?.let { loadVideoAndTranslate(it) }
                            },
                            selectedSubtitles = selectedSubtitles,
                            onSubtitlesChange = { newSubs ->
                                selectedSubtitles = newSubs
                                playerManager.setSubtitlesEnabled(newSubs == SubtitlesMode.RUSSIAN)
                            },
                            selectedLanguage = selectedLanguage,
                            onLanguageChange = { newLang ->
                                selectedLanguage = newLang
                                activeVideoUrl?.let { loadVideoAndTranslate(it) }
                            },
                            onEnterPiP = { enterPictureInPicture() },
                            onNavigateBack = {
                                playerManager.pause()
                                activeVideoUrl = null
                                loadHistory()
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        HistoryScreen(
                            historyItems = historyList,
                            onPlayUrl = { url, startPos ->
                                loadVideoAndTranslate(url, startPos)
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
                }
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
            loadVideoAndTranslate(url)
        }
    }

    private fun loadVideoAndTranslate(rawUrl: String, requestedStartPositionMs: Long = 0L) {
        activeVideoUrl = rawUrl
        isLoading = true
        statusMessage = "Resolving video stream..."

        lifecycleScope.launch {
            val streamResult = streamExtractor.extract(rawUrl)
            if (streamResult.isFailure) {
                isLoading = false
                statusMessage = "Error: ${streamResult.exceptionOrNull()?.message}"
                return@launch
            }

            val videoInfo = streamResult.getOrThrow()
            currentVideoInfo = videoInfo
            statusMessage = "Requesting AI voice-over translation..."

            // Check if we have an existing resume position from history if none explicitly given
            val resumePositionMs = if (requestedStartPositionMs > 0L) {
                requestedStartPositionMs
            } else {
                val existing = historyDb.getHistoryItem(videoInfo.id)
                existing?.lastPositionMs ?: 0L
            }

            // Request Translation from VOT API
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
                onProgress = { statusMessage = it }
            )

            // Request Subtitles if Russian mode
            val subtitlesResult = votApiClient.getSubtitles(
                videoUrl = votUrl,
                targetLang = selectedLanguage
            )
            val cues = subtitlesResult.getOrDefault(emptyList())

            isLoading = false
            statusMessage = null

            val translatedAudioUrl = votResult.getOrNull()?.url
            currentTranslatedAudioUrl = translatedAudioUrl

            // Save to database
            historyDb.saveOrUpdate(
                WatchHistoryItem(
                    videoId = videoInfo.id,
                    url = rawUrl,
                    title = videoInfo.title,
                    thumbnailUrl = videoInfo.thumbnailUrl ?: "",
                    lastPositionMs = resumePositionMs,
                    durationMs = videoInfo.durationSeconds * 1000L,
                    preferredVoice = selectedVoiceType.name.lowercase(),
                    originalVolume = playerManager.originalVolume.value,
                    voiceVolume = playerManager.voiceoverVolume.value
                )
            )
            loadHistory()

            playerManager.prepare(
                videoInfo = videoInfo,
                voiceoverAudioUrl = translatedAudioUrl,
                subtitles = cues,
                startPositionMs = resumePositionMs
            )
            playerManager.setSubtitlesEnabled(selectedSubtitles == SubtitlesMode.RUSSIAN)
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

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}
