package com.vot.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vot.player.data.model.*
import com.vot.player.data.pref.PlayerMode
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
    onEnterPiP: () -> Unit,
    onNavigateBack: () -> Unit,
    onExportVideo: () -> Unit,
    onExportAudio: () -> Unit,
    isLiveVoiceAvailable: Boolean = true,
    hasSubtitles: Boolean = true,
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Auto-hide controls after 7 seconds of playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(7000L)
            showControls = false
        }
    }

    // Request focus for D-Pad / TV key handling
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                        Key.Back -> {
                            if (showControls) {
                                showControls = false
                                true
                            } else {
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
            onToggleControls = { showControls = !showControls }
        ) {
            if (isAudioOnly) {
                // Podcast Visualizer View
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
                                Text("AUDIO-ONLY / PODCAST (90% DATA SAVED)", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            } else if (playerManager.videoPlayer.mediaItemCount > 0) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = playerManager.videoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!embeddedUrl.isNullOrEmpty()) {
                // Minimalist Ad-Free Player (Zero comments, Zero recommendations, Zero headers)
                var webViewRef by remember { mutableStateOf<WebView?>(null) }
                val bridge = remember {
                    VotWebBridge(
                        onPlay = { playerManager.play() },
                        onPause = { playerManager.pause() },
                        onSeek = { posMs -> playerManager.seekTo(posMs) },
                        onTimeUpdate = { posMs -> playerManager.syncWebPosition(posMs) },
                        onRateChange = { rate -> playerManager.setPlaybackSpeed(rate) },
                        onScreenTap = { showControls = !showControls },
                        onFullscreenToggle = { isFs ->
                            val act = context as? android.app.Activity
                            act?.requestedOrientation = if (isFs) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    )
                }

                DisposableEffect(webViewRef) {
                    val controller = object : VotPlayerManager.WebVideoController {
                        override fun play() {
                            webViewRef?.evaluateJavascript("const v = document.querySelector('video'); if (v && v.paused) v.play();", null)
                        }
                        override fun pause() {
                            webViewRef?.evaluateJavascript("const v = document.querySelector('video'); if (v && !v.paused) v.pause();", null)
                        }
                        override fun seekTo(positionMs: Long) {
                            val sec = positionMs / 1000.0
                            webViewRef?.evaluateJavascript("const v = document.querySelector('video'); if (v) v.currentTime = $sec;", null)
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
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val cssInjection = """
                                        const style = document.createElement('style');
                                        style.textContent = `${VotWebBridge.MINIMALIST_CSS}`;
                                        document.head.appendChild(style);
                                    """.trimIndent()
                                    view?.evaluateJavascript(cssInjection, null)
                                    view?.evaluateJavascript(VotWebBridge.INJECTION_SCRIPT, null)
                                    view?.evaluateJavascript("window.setOriginalVolume($originalVolume);", null)
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
        }

        // Subtitle Overlay
        SubtitleOverlay(
            subtitleText = currentSubtitle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showControls) (if (isLandscape) 135.dp else 195.dp) else 36.dp)
                .padding(horizontal = 20.dp)
        )

        // Loading or Status banner
        if (isLoading || statusMessage != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC111118), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = AccentRed, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = statusMessage ?: "Preparing player...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Animated Controls Overlay
        AnimatedVisibility(
            visible = showControls,
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
                        .padding(horizontal = 8.dp, vertical = if (isLandscape) 6.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .padding(horizontal = 8.dp)
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        IconButton(onClick = { playerManager.toggleAudioOnly() }) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Audio-Only Mode",
                                tint = if (isAudioOnly) AccentRed else Color.White
                            )
                        }
                        IconButton(onClick = onEnterPiP) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
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
                        .padding(horizontal = if (isLandscape) 24.dp else 14.dp, vertical = if (isLandscape) 4.dp else 8.dp)
                ) {
                    // Seekbar row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.width(46.dp)
                        )
                        Slider(
                            value = currentPositionMs.toFloat(),
                            onValueChange = { playerManager.seekTo(it.toLong()) },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
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
            visible = !showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (isLandscape) 12.dp else 28.dp, end = 16.dp)
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
                onExportVideoClick = onExportVideo,
                onExportAudioClick = onExportAudio,
                isLiveVoiceAvailable = isLiveVoiceAvailable,
                hasSubtitles = hasSubtitles,
                isCustomVoiceSupported = isCustomVoiceSupported,
                onDismiss = { showSettingsDialog = false }
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
