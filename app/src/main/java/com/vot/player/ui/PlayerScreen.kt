package com.vot.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vot.player.data.model.*
import com.vot.player.data.pref.PlayerMode
import com.vot.player.data.pref.TranslationTriggerMode
import com.vot.player.player.VotPlayerManager
import com.vot.player.ui.components.DualVolumeBar
import com.vot.player.ui.components.GestureOverlay
import com.vot.player.ui.components.SettingsDialog
import com.vot.player.ui.components.SubtitleOverlay
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary
import com.vot.player.web.VotWebBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    playerManager: VotPlayerManager,
    videoTitle: String,
    videoAuthor: String,
    thumbnailUrl: String? = null,
    embeddedUrl: String? = null,
    statusMessage: String? = null,
    isLoading: Boolean = false,
    selectedVoiceType: VoiceType,
    onVoiceTypeChange: (VoiceType) -> Unit,
    selectedVoiceGender: VoiceGender = VoiceGender.AUTO,
    onVoiceGenderChange: (VoiceGender) -> Unit = {},
    selectedVoiceActor: VoiceActor = VoiceActor.AUTO,
    onVoiceActorChange: (VoiceActor) -> Unit = {},
    selectedSubtitles: SubtitlesMode,
    onSubtitlesChange: (SubtitlesMode) -> Unit,
    selectedLanguage: TargetLanguage,
    onLanguageChange: (TargetLanguage) -> Unit,
    isSponsorBlockEnabled: Boolean = true,
    onSponsorBlockChange: (Boolean) -> Unit = {},
    preferredPlayerMode: PlayerMode = PlayerMode.ASK_EVERY_TIME,
    onPlayerModeChange: (PlayerMode) -> Unit = {},
    translationTriggerMode: TranslationTriggerMode = TranslationTriggerMode.ALWAYS_AUTO,
    onTranslationTriggerModeChange: (TranslationTriggerMode) -> Unit = {},
    autoSkipRussianVideos: Boolean = true,
    onAutoSkipRussianVideosChange: (Boolean) -> Unit = {},
    isTranslationActive: Boolean = true,
    showTranslationPrompt: Boolean = false,
    onTriggerTranslation: () -> Unit = {},
    onDismissTranslationPrompt: () -> Unit = {},
    onDismissStatus: () -> Unit = {},
    onEnterPiP: () -> Unit,
    isInPipMode: Boolean = false,
    onNavigateBack: () -> Unit,
    onExportVideo: () -> Unit,
    onExportAudio: () -> Unit,
    isLiveVoiceAvailable: Boolean = true,
    hasSubtitles: Boolean = true,
    hasRussianSubtitles: Boolean = true,
    hasRomanianSubtitles: Boolean = true,
    hasEnglishSubtitles: Boolean = true,
    isCustomVoiceSupported: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPositionMs by playerManager.currentPositionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val originalVolume by playerManager.originalVolume.collectAsState()
    val voiceoverVolume by playerManager.voiceoverVolume.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()
    val currentSubtitle by playerManager.currentSubtitle.collectAsState()
    val availableQualities by playerManager.availableQualities.collectAsState()
    val selectedQuality by playerManager.selectedQuality.collectAsState()
    val isAudioOnly by playerManager.isAudioOnly.collectAsState()
    val managerHasSubtitles by playerManager.hasSubtitles.collectAsState()
    val managerHasRussianSubtitles by playerManager.hasRussianSubtitles.collectAsState()
    val managerHasRomanianSubtitles by playerManager.hasRomanianSubtitles.collectAsState()
    val managerHasEnglishSubtitles by playerManager.hasEnglishSubtitles.collectAsState()
    val effectiveHasRussian = hasRussianSubtitles || managerHasRussianSubtitles
    val effectiveHasRomanian = hasRomanianSubtitles || managerHasRomanianSubtitles
    val effectiveHasEnglish = hasEnglishSubtitles || managerHasEnglishSubtitles
    val effectiveHasSubtitles = hasSubtitles || managerHasSubtitles || effectiveHasRussian || effectiveHasRomanian || effectiveHasEnglish

    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Auto-hide controls after 7 seconds of playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isInPipMode) {
            delay(7000L)
            showControls = false
        }
    }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            showControls = false
            showSettingsDialog = false
            showSubtitlePicker = false
        }
    }

    // Request focus for D-Pad / TV key handling
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    // System Back Gesture handling
    BackHandler {
        if (showSubtitlePicker) {
            showSubtitlePicker = false
        } else if (showSettingsDialog) {
            showSettingsDialog = false
        } else if (isLandscape) {
            val act = context as? android.app.Activity
            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            val act = context as? android.app.Activity
            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onNavigateBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.MediaPlayPause, Key.Spacebar -> {
                            playerManager.togglePlayPause()
                            showControls = true
                            true
                        }
                        Key.DirectionLeft -> {
                            playerManager.seekRelative(-10_000L)
                            showControls = true
                            true
                        }
                        Key.DirectionRight -> {
                            playerManager.seekRelative(10_000L)
                            showControls = true
                            true
                        }
                        Key.DirectionUp -> {
                            playerManager.setVoiceoverVolume(voiceoverVolume + 0.1f)
                            showControls = true
                            true
                        }
                        Key.DirectionDown -> {
                            playerManager.setVoiceoverVolume(voiceoverVolume - 0.1f)
                            showControls = true
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (showSettingsDialog) {
                                showSettingsDialog = false
                                true
                            } else if (isLandscape) {
                                val act = context as? android.app.Activity
                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                true
                            } else if (showControls) {
                                showControls = false
                                true
                            } else {
                                val act = context as? android.app.Activity
                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                onNavigateBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video Surface or Audio-Only Podcast View
        GestureOverlay(
            originalVolume = originalVolume,
            voiceoverVolume = voiceoverVolume,
            onOriginalVolumeChange = { playerManager.setOriginalVolume(it) },
            onVoiceoverVolumeChange = { playerManager.setVoiceoverVolume(it) },
            onSeekRelative = { playerManager.seekRelative(it) },
            onToggleControls = { if (!isInPipMode) showControls = !showControls },
            enabled = !isInPipMode
        ) {
            // Core video surface (remains mounted to preserve playback, ExoPlayer/WebView session, and clock sync)
            if (playerManager.videoPlayer.mediaItemCount > 0) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = playerManager.videoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            subtitleView?.visibility = View.GONE
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!embeddedUrl.isNullOrEmpty()) {
                // Minimalist Ad-Free Player (Zero comments, Zero recommendations, Zero headers)
                var webViewRef by remember { mutableStateOf<WebView?>(null) }
                val coroutineScope = rememberCoroutineScope()
                val votApiClient = remember { com.vot.player.data.vot.VotApiClient() }
                val bridge = remember {
                    VotWebBridge(
                        onPlay = { playerManager.play() },
                        onPause = { playerManager.pause() },
                        onSeek = { posMs -> playerManager.syncWebSeek(posMs) },
                        onTimeUpdate = { posMs, durMs -> playerManager.syncWebPosition(posMs, durMs) },
                        onRateChange = { rate -> playerManager.setPlaybackSpeed(rate) },
                        onScreenTap = { if (!isInPipMode) showControls = !showControls },
                        onFullscreenToggle = { isFs ->
                            val act = context as? android.app.Activity
                            act?.requestedOrientation = if (isFs) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        },
                        onCaptionsReceived = { rawText ->
                            coroutineScope.launch {
                                val cues = votApiClient.parseSubtitles(rawText)
                                if (cues.isNotEmpty()) {
                                    playerManager.setSubtitles(cues)
                                }
                            }
                        }
                    )
                }

                DisposableEffect(webViewRef) {
                    val controller = object : VotPlayerManager.WebVideoController {
                        override fun play() {
                            val js = "(function(){ try { if (window.__vot_play) { window.__vot_play(); return; } const p = document.getElementById('movie_player') || document.querySelector('.html5-video-player'); if (p && typeof p.playVideo === 'function') p.playVideo(); const v = document.querySelector('video'); if (v && v.paused) v.play(); } catch(e){} })();"
                            webViewRef?.evaluateJavascript(js, null)
                        }
                        override fun pause() {
                            val js = "(function(){ try { if (window.__vot_pause) { window.__vot_pause(); return; } const p = document.getElementById('movie_player') || document.querySelector('.html5-video-player'); if (p && typeof p.pauseVideo === 'function') p.pauseVideo(); const v = document.querySelector('video'); if (v && !v.paused) v.pause(); } catch(e){} })();"
                            webViewRef?.evaluateJavascript(js, null)
                        }
                        override fun seekTo(positionMs: Long) {
                            val sec = positionMs / 1000.0
                            val js = "(function(){ try { if (window.__vot_seekTo) { window.__vot_seekTo($sec); return; } const p = document.getElementById('movie_player') || document.querySelector('.html5-video-player'); if (p && typeof p.seekTo === 'function') p.seekTo($sec, true); const v = document.querySelector('video'); if (v) v.currentTime = $sec; } catch(e){} })();"
                            webViewRef?.evaluateJavascript(js, null)
                        }
                        override fun setVolume(volume: Float) {
                            webViewRef?.evaluateJavascript("if (window.setOriginalVolume) window.setOriginalVolume($volume);", null)
                        }
                        override fun setPlaybackSpeed(speed: Float) {
                            webViewRef?.evaluateJavascript("const v = document.querySelector('video'); if (v) v.playbackRate = $speed;", null)
                        }
                    }
                    playerManager.webVideoController = controller
                    onDispose {
                        if (playerManager.webVideoController === controller) {
                            playerManager.webVideoController = null
                        }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            addJavascriptInterface(bridge, "VotAndroidBridge")

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val url = request?.url?.toString().orEmpty()
                                    if (url.contains("doubleclick.net") || url.contains("googleads") || url.contains("/pagead/") || url.contains("/ad_status")) {
                                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val cssInjection = """
                                        (function() {
                                            const style = document.createElement('style');
                                            style.innerHTML = `
                                                header, #header-bar, .ytm-pivot-bar-renderer, ytm-pivot-bar-renderer,
                                                ytm-mobile-topbar-renderer, .mobile-topbar-header, .header-bar,
                                                ytm-item-section-renderer[section-identifier="comment-item-section"],
                                                .comment-section, ytm-comments-entry-point-header-renderer,
                                                ytm-single-column-watch-next-results-renderer > ytm-item-section-renderer:not(:first-child),
                                                ytm-related-chip-cloud-renderer, ytm-promoted-sparkles-web-renderer {
                                                    display: none !important;
                                                }
                                                body, html {
                                                    background: #000000 !important;
                                                    overflow: hidden !important;
                                                }
                                                .video-stream, video {
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    object-fit: contain !important;
                                                }
                                            `;
                                            document.head.appendChild(style);
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(cssInjection, null)
                                    view?.evaluateJavascript(VotWebBridge.INJECTION_SCRIPT, null)
                                    view?.evaluateJavascript("window.setOriginalVolume($originalVolume);", null)
                                    val resumeMs = currentPositionMs
                                    if (resumeMs > 0L) {
                                        val startSec = resumeMs / 1000L
                                        view?.evaluateJavascript("if (window.__vot_setInitialPosition) window.__vot_setInitialPosition($startSec);", null)
                                    }
                                }
                            }

                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                    val act = context as? android.app.Activity
                                    act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                                override fun onHideCustomView() {
                                    val act = context as? android.app.Activity
                                    act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }
                            loadUrl(embeddedUrl)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentRed)
                }
            }

            // Audio-Only Podcast Visualizer View (rendered over video surface, preserving playback clock)
            if (isAudioOnly && !isInPipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101018)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            shadowElevation = 12.dp,
                            modifier = Modifier.size(220.dp)
                        ) {
                            if (!thumbnailUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF222233)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            color = AccentRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Headphones, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AUDIO-ONLY / PODCAST (DATA SAVED)", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = videoTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = videoAuthor,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Subtitle Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .displayCutoutPadding(),
            contentAlignment = if (isInPipMode) Alignment.BottomCenter else if (isLandscape) Alignment.BottomCenter else Alignment.Center
        ) {
            SubtitleOverlay(
                subtitleText = currentSubtitle,
                modifier = if (isInPipMode) {
                    Modifier
                        .padding(bottom = 6.dp)
                        .padding(horizontal = 8.dp)
                } else if (isLandscape) {
                    Modifier
                        .padding(bottom = if (showControls) 120.dp else 28.dp)
                        .padding(horizontal = 24.dp)
                } else {
                    // In portrait orientation, video is centered (16:9).
                    // Position subtitle near the bottom edge of the centered 16:9 video frame!
                    Modifier
                        .padding(top = 75.dp)
                        .padding(horizontal = 24.dp)
                }
            )
        }

        // Loading or Status banner
        if (isLoading && !isInPipMode) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC111118), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                CircularProgressIndicator(color = AccentRed, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusMessage ?: "Preparing player...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (!statusMessage.isNullOrEmpty() && showControls && !isInPipMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC1A1A24),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(top = if (isLandscape) 56.dp else 68.dp)
            ) {
                Text(
                    text = statusMessage,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Animated Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x4D000000))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 8.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isLandscape) {
                                val act = context as? android.app.Activity
                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = videoTitle,
                            color = TextPrimary,
                            fontSize = if (isLandscape) 14.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = videoAuthor,
                            color = TextSecondary,
                            fontSize = if (isLandscape) 11.sp else 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isTranslationActive) {
                        Surface(
                            color = AccentRed,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { onTriggerTranslation() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Translate",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Translate (RU)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0x66000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val act = context as? android.app.Activity
                                if (isLandscape) {
                                    act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { playerManager.toggleAudioOnly() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Audio-Only Mode",
                                tint = if (isAudioOnly) AccentRed else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        // Dedicated Closed Caption (CC) Quick Selector Button
                        IconButton(
                            onClick = { showSubtitlePicker = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            if (selectedSubtitles != SubtitlesMode.OFF) {
                                val badge = when (selectedSubtitles) {
                                    SubtitlesMode.RUSSIAN -> "RU"
                                    SubtitlesMode.ROMANIAN -> "RO"
                                    SubtitlesMode.ENGLISH -> "EN"
                                    else -> "CC"
                                }
                                Surface(
                                    color = AccentRed,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaptionDisabled,
                                    contentDescription = "Subtitles / Closed Captions",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onEnterPiP,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Floating Translation Prompt Banner
                AnimatedVisibility(
                    visible = showTranslationPrompt,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = if (isLandscape) 48.dp else 64.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xEE1E1E28),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.8f)),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Translate to Russian?",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = onTriggerTranslation,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Translate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = onDismissTranslationPrompt,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Keep Original", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Center Play/Pause & Skip Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playerManager.seekRelative(-10_000L) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Seek -10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { playerManager.togglePlayPause() },
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0x80000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    IconButton(
                        onClick = { playerManager.seekRelative(10_000L) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Seek +10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Panel: Seekbar + Dual Volume Control
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = if (isLandscape) 24.dp else 14.dp, vertical = if (isLandscape) 4.dp else 8.dp)
                ) {
                    var isDraggingSlider by remember { mutableStateOf(false) }
                    var dragPositionMs by remember { mutableStateOf(0f) }
                    val displayPositionMs = if (isDraggingSlider) dragPositionMs.toLong() else currentPositionMs

                    // Seekbar row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(displayPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.width(46.dp)
                        )
                        val durFloat = durationMs.toFloat()
                        val posFloat = if (isDraggingSlider) dragPositionMs else currentPositionMs.toFloat()
                        val safeValue = if (durFloat > 0f) posFloat.coerceIn(0f, durFloat) else 0f
                        val safeRange = if (durFloat > 0f) 0f..durFloat else 0f..1f

                        Slider(
                            value = safeValue,
                            onValueChange = { newValue ->
                                isDraggingSlider = true
                                dragPositionMs = newValue
                            },
                            onValueChangeFinished = {
                                val target = dragPositionMs.toLong()
                                isDraggingSlider = false
                                playerManager.seekTo(target)
                            },
                            valueRange = safeRange,
                            enabled = durFloat > 0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentRed,
                                activeTrackColor = AccentRed,
                                inactiveTrackColor = Color(0x66FFFFFF)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(46.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 6.dp))

                    // Dual Volume Bar
                    DualVolumeBar(
                        originalVolume = originalVolume,
                        voiceoverVolume = voiceoverVolume,
                        onOriginalVolumeChange = { playerManager.setOriginalVolume(it) },
                        onVoiceoverVolumeChange = { playerManager.setVoiceoverVolume(it) },
                        onToggleOriginalMute = { playerManager.toggleOriginalMute() }
                    )
                }
            }
        }

        // Floating Persistent Pill when controls are hidden
        AnimatedVisibility(
            visible = !showControls && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = if (isLandscape) 8.dp else 12.dp, end = 16.dp)
        ) {
            Surface(
                onClick = { showControls = true },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC1A1A24),
                contentColor = Color.White,
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Show VOT Controls",
                        tint = AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VOT ${(voiceoverVolume * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Animated Status Notification Pill / Loader
        val isErrorMessage = statusMessage?.let { msg ->
            msg.contains("ошибка", ignoreCase = true) ||
            msg.contains("failed", ignoreCase = true) ||
            msg.contains("error", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true)
        } ?: false

        AnimatedVisibility(
            visible = (isLoading || !statusMessage.isNullOrEmpty()) && !isInPipMode,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = if (isLandscape) 52.dp else 68.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE1E1E28),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isLoading) AccentRed.copy(alpha = 0.7f)
                    else if (isErrorMessage) Color(0xFFFF5252).copy(alpha = 0.8f)
                    else Color(0xFF4CAF50).copy(alpha = 0.7f)
                ),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { onDismissStatus() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AccentRed
                        )
                    } else if (isErrorMessage) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMessage ?: if (isLoading) "Loading translation..." else "Ready",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isLoading) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDismissStatus() }
                        )
                    }
                }
            }
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                selectedVoiceType = selectedVoiceType,
                onVoiceTypeChange = onVoiceTypeChange,
                selectedVoiceGender = selectedVoiceGender,
                onVoiceGenderChange = onVoiceGenderChange,
                selectedVoiceActor = selectedVoiceActor,
                onVoiceActorChange = onVoiceActorChange,
                selectedSubtitles = selectedSubtitles,
                onSubtitlesChange = onSubtitlesChange,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                currentSpeed = playbackSpeed,
                onSpeedChange = { playerManager.setPlaybackSpeed(it) },
                availableQualities = availableQualities,
                selectedQuality = selectedQuality,
                onQualityChange = { playerManager.changeQuality(it) },
                isSponsorBlockEnabled = isSponsorBlockEnabled,
                onSponsorBlockChange = onSponsorBlockChange,
                isAudioOnly = isAudioOnly,
                onToggleAudioOnly = { playerManager.toggleAudioOnly() },
                preferredPlayerMode = preferredPlayerMode,
                onPlayerModeChange = onPlayerModeChange,
                translationTriggerMode = translationTriggerMode,
                onTranslationTriggerModeChange = onTranslationTriggerModeChange,
                autoSkipRussianVideos = autoSkipRussianVideos,
                onAutoSkipRussianVideosChange = onAutoSkipRussianVideosChange,
                isTranslationActive = isTranslationActive,
                onTriggerTranslation = onTriggerTranslation,
                onExportVideoClick = onExportVideo,
                onExportAudioClick = onExportAudio,
                isLiveVoiceAvailable = isLiveVoiceAvailable,
                hasSubtitles = effectiveHasSubtitles,
                hasRussianSubtitles = effectiveHasRussian,
                hasRomanianSubtitles = effectiveHasRomanian,
                hasEnglishSubtitles = effectiveHasEnglish,
                isCustomVoiceSupported = isCustomVoiceSupported,
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Subtitle Language Quick Picker Dialog
        if (showSubtitlePicker) {
            AlertDialog(
                onDismissRequest = { showSubtitlePicker = false },
                containerColor = Color(0xFF1E1E28),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Subtitles / Captions",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubtitlesMode.values().forEach { mode ->
                            val isSelected = selectedSubtitles == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) AccentRed.copy(alpha = 0.2f) else Color(0x11FFFFFF),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AccentRed) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSubtitlesChange(mode)
                                        showSubtitlePicker = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = AccentRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitlePicker = false }) {
                        Text("Close", color = Color.White)
                    }
                }
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
