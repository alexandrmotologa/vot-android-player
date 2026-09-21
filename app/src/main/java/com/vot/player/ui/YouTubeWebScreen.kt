package com.vot.player.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vot.player.data.model.SubtitlesMode
import com.vot.player.data.model.TargetLanguage
import com.vot.player.data.model.VoiceType
import com.vot.player.player.VotPlayerManager
import com.vot.player.ui.components.DualVolumeBar
import com.vot.player.ui.components.SubtitleOverlay
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.DarkBackground
import com.vot.player.ui.theme.DarkCard
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary
import com.vot.player.web.VotWebBridge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebScreen(
    videoUrl: String,
    startPositionMs: Long = 0L,
    playerManager: VotPlayerManager,
    statusMessage: String? = null,
    isLoading: Boolean = false,
    currentTranslatedAudioUrl: String? = null,
    selectedVoiceType: VoiceType,
    onVoiceTypeChange: (VoiceType) -> Unit,
    selectedSubtitles: SubtitlesMode,
    onSubtitlesChange: (SubtitlesMode) -> Unit,
    selectedLanguage: TargetLanguage,
    onLanguageChange: (TargetLanguage) -> Unit,
    onSwitchToNativePlayer: () -> Unit,
    onVideoUrlChanged: (String) -> Unit = {},
    onNavigateBack: () -> Unit,
    isLiveVoiceAvailable: Boolean = true,
    hasSubtitles: Boolean = true,
    hasRussianSubtitles: Boolean = true,
    hasEnglishSubtitles: Boolean = true,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showControlsSheet by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("YouTube") }
    var isWebLoading by remember { mutableStateOf(true) }
    var lastNavigatedSpaVideoId by remember { mutableStateOf<String?>(null) }

    val originalVolume by playerManager.originalVolume.collectAsState()
    val voiceoverVolume by playerManager.voiceoverVolume.collectAsState()
    val managerHasSubtitles by playerManager.hasSubtitles.collectAsState()
    val managerHasRussianSubtitles by playerManager.hasRussianSubtitles.collectAsState()
    val managerHasEnglishSubtitles by playerManager.hasEnglishSubtitles.collectAsState()
    val effectiveHasRussian = hasRussianSubtitles || managerHasRussianSubtitles
    val effectiveHasEnglish = hasEnglishSubtitles || managerHasEnglishSubtitles
    val effectiveHasSubtitles = hasSubtitles || managerHasSubtitles || effectiveHasRussian || effectiveHasEnglish

    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Handle back button: return from landscape to portrait, or go back in web history, else exit to Home
    BackHandler {
        if (isLandscape) {
            val act = context as? android.app.Activity
            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            val act = context as? android.app.Activity
            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onNavigateBack()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val votApiClient = remember { com.vot.player.data.vot.VotApiClient() }
    val bridge = remember {
        VotWebBridge(
            onPlay = { playerManager.play() },
            onPause = { playerManager.pause() },
            onSeek = { posMs -> playerManager.syncWebSeek(posMs) },
            onTimeUpdate = { posMs, durMs -> playerManager.syncWebPosition(posMs, durMs) },
            onRateChange = { rate -> playerManager.setPlaybackSpeed(rate) },
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
            },
            onDualCaptionsReceived = { ruJson, enJson ->
                coroutineScope.launch {
                    val ruCues = if (ruJson.isNotBlank()) votApiClient.parseSubtitles(ruJson) else emptyList()
                    val enCues = if (enJson.isNotBlank()) votApiClient.parseSubtitles(enJson) else emptyList()
                    playerManager.setDualSubtitles(ruCues, enCues)
                }
            },
            onUrlChanged = { newUrl ->
                lastNavigatedSpaVideoId = com.vot.player.data.youtube.YouTubeStreamExtractor.extractVideoId(newUrl)
                onVideoUrlChanged(newUrl)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pageTitle,
                        maxLines = 1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isLandscape) {
                            val act = context as? android.app.Activity
                            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val act = context as? android.app.Activity
                        if (isLandscape) {
                            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                    }
                    IconButton(onClick = { showControlsSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "VOT Audio Controls", tint = AccentRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = Color.Black,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Android WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(bridge, VotWebBridge.INTERFACE_NAME)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString().orEmpty()
                                if (isAdUrl(url)) {
                                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    return false
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isWebLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isWebLoading = false
                                val hideAdsCss = """
                                    const style = document.createElement('style');
                                    style.textContent = `
                                        .ad-showing .ytp-ad-player-overlay,
                                        .ad-showing .video-ads,
                                        .ad-showing .ytm-ad-badge,
                                        .ad-showing .ytp-ad-module,
                                        .ad-interrupting .ytp-ad-player-overlay,
                                        .ad-interrupting .video-ads,
                                        .ad-interrupting .ytm-ad-badge,
                                        .ad-interrupting .ytp-ad-module,
                                        ytm-promoted-sparkles-web-renderer,
                                        ytm-companion-ad-renderer,
                                        ytm-ad-slot-renderer,
                                        .ad-container,
                                        ytm-mealbar-promo-renderer,
                                        .ytp-caption-window-container,
                                        .caption-window,
                                        .ytp-caption-segment,
                                        .ytm-caption-window,
                                        div[class*="caption-window"],
                                        .ytp-caption-window-rollup,
                                        .ytp-caption-window-bottom,
                                        .ytp-caption-window-top {
                                            display: none !important;
                                            opacity: 0 !important;
                                            visibility: hidden !important;
                                            pointer-events: none !important;
                                        }
                                    `;
                                    document.head.appendChild(style);
                                """.trimIndent()
                                view?.evaluateJavascript(hideAdsCss, null)
                                view?.evaluateJavascript(VotWebBridge.INJECTION_SCRIPT, null)
                                view?.evaluateJavascript("window.setOriginalVolume($originalVolume);", null)
                                if (startPositionMs > 0L) {
                                    val startSec = startPositionMs / 1000L
                                    view?.evaluateJavascript("if (window.__vot_setInitialPosition) window.__vot_setInitialPosition($startSec);", null)
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                if (!title.isNullOrEmpty()) pageTitle = title
                            }

                            override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                val act = context as? android.app.Activity
                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }

                            override fun onHideCustomView() {
                                val act = context as? android.app.Activity
                                act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }

                        loadUrl(videoUrl)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    val currentId = com.vot.player.data.youtube.YouTubeStreamExtractor.extractVideoId(webView.url.orEmpty())
                    val targetId = com.vot.player.data.youtube.YouTubeStreamExtractor.extractVideoId(videoUrl)
                    if (targetId != null && targetId != currentId && targetId != lastNavigatedSpaVideoId) {
                        webView.loadUrl(videoUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isWebLoading) {
                LinearProgressIndicator(
                    color = AccentRed,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }

            // Top Status Notification Banner
            AnimatedVisibility(
                visible = isLoading || !statusMessage.isNullOrEmpty(),
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xEE1E1E28),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isLoading) AccentRed.copy(alpha = 0.7f) else Color(0xFF4CAF50).copy(alpha = 0.7f)
                    ),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AccentRed
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Subtitles Overlay
            val currentSubtitle by playerManager.currentSubtitle.collectAsState()
            SubtitleOverlay(
                subtitleText = currentSubtitle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(bottom = if (isLandscape) 40.dp else 90.dp)
                    .padding(horizontal = 20.dp)
            )

            // Floating VOT Controller Pill
            val isSynced = !currentTranslatedAudioUrl.isNullOrEmpty()
            val pillColor = if (isSynced) Color(0xFF1B5E20) else AccentRed
            Surface(
                color = pillColor,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(16.dp)
                    .clickable { showControlsSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSynced) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VOT Synced (RU)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (!statusMessage.isNullOrEmpty()) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusMessage.take(24),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VOT Active",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // VOT Controls Bottom Sheet
        if (showControlsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showControlsSheet = false },
                containerColor = DarkCard,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Voice-Over & Volume Controls",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Dual Volume Bar
                    DualVolumeBar(
                        originalVolume = originalVolume,
                        voiceoverVolume = voiceoverVolume,
                        onOriginalVolumeChange = { vol ->
                            playerManager.setOriginalVolume(vol)
                            webViewRef?.evaluateJavascript("window.setOriginalVolume($vol);", null)
                        },
                        onVoiceoverVolumeChange = { vol ->
                            playerManager.setVoiceoverVolume(vol)
                        },
                        onToggleOriginalMute = {
                            playerManager.toggleOriginalMute()
                            webViewRef?.evaluateJavascript("window.setOriginalVolume(${playerManager.originalVolume.value});", null)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0x22FFFFFF))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Voice Type (Standard vs Live)
                    Text(text = "Voice Synthesis", color = TextSecondary, fontSize = 13.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VoiceType.values().forEach { voice ->
                            val isVoiceEnabled = voice != VoiceType.LIVE_VOICE || isLiveVoiceAvailable
                            FilterChip(
                                selected = selectedVoiceType == voice,
                                onClick = { onVoiceTypeChange(voice) },
                                enabled = isVoiceEnabled,
                                label = { Text(voice.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentRed,
                                    selectedLabelColor = Color.White,
                                    disabledContainerColor = Color(0x11FFFFFF),
                                    disabledLabelColor = Color.Gray
                                )
                            )
                        }
                    }
                    if (!isLiveVoiceAvailable) {
                        Text(
                            text = "Notice: Only standard voice is provided by Yandex for this video",
                            color = Color(0xFFE5A93C),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0x22FFFFFF))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Subtitles Controls
                    Text(text = "Subtitles", color = TextSecondary, fontSize = 13.sp)
                    val anySubtitlesAvailable = effectiveHasSubtitles || effectiveHasRussian || effectiveHasEnglish
                    if (!anySubtitlesAvailable) {
                        Text(
                            text = "Notice: Subtitles are not available for this video",
                            color = Color(0xFFE5A93C),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubtitlesMode.values().forEach { sub ->
                            val isEnabled = when (sub) {
                                SubtitlesMode.OFF -> true
                                SubtitlesMode.RUSSIAN -> effectiveHasRussian || effectiveHasSubtitles
                                SubtitlesMode.ENGLISH -> effectiveHasEnglish || effectiveHasSubtitles
                            }
                            FilterChip(
                                selected = selectedSubtitles == sub,
                                onClick = { if (isEnabled) onSubtitlesChange(sub) },
                                enabled = isEnabled,
                                label = { Text(sub.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentRed,
                                    selectedLabelColor = Color.White,
                                    disabledContainerColor = Color(0x11FFFFFF),
                                    disabledLabelColor = Color.Gray
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Switch to Native Player Button
                    OutlinedButton(
                        onClick = {
                            showControlsSheet = false
                            onSwitchToNativePlayer()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Switch to Native Player (Ad-Free, 4K, PiP)")
                    }
                }
            }
        }
    }
}

private fun isAdUrl(url: String): Boolean {
    return VotWebBridge.isAdUrl(url)
}
