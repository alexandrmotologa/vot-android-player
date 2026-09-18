package com.vot.player.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
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
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.DarkBackground
import com.vot.player.ui.theme.DarkCard
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary
import com.vot.player.web.VotWebBridge

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebScreen(
    videoUrl: String,
    playerManager: VotPlayerManager,
    selectedVoiceType: VoiceType,
    onVoiceTypeChange: (VoiceType) -> Unit,
    selectedSubtitles: SubtitlesMode,
    onSubtitlesChange: (SubtitlesMode) -> Unit,
    selectedLanguage: TargetLanguage,
    onLanguageChange: (TargetLanguage) -> Unit,
    onSwitchToNativePlayer: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showControlsSheet by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("YouTube") }
    var isWebLoading by remember { mutableStateOf(true) }

    val originalVolume by playerManager.originalVolume.collectAsState()
    val voiceoverVolume by playerManager.voiceoverVolume.collectAsState()

    // Handle back button: go back in web history if possible, else exit to Home
    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onNavigateBack()
        }
    }

    val bridge = remember {
        VotWebBridge(
            onPlay = { playerManager.play() },
            onPause = { playerManager.pause() },
            onSeek = { posMs -> playerManager.seekTo(posMs) },
            onRateChange = { rate -> playerManager.setPlaybackSpeed(rate) }
        )
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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
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
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(bridge, VotWebBridge.INTERFACE_NAME)

                        webViewClient = object : WebViewClient() {
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
                                view?.evaluateJavascript(VotWebBridge.INJECTION_SCRIPT, null)
                                view?.evaluateJavascript("window.setOriginalVolume($originalVolume);", null)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                if (!title.isNullOrEmpty()) pageTitle = title
                            }
                        }

                        loadUrl(videoUrl)
                        webViewRef = this
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

            // Floating VOT Controller Pill
            Surface(
                color = AccentRed,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clickable { showControlsSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceType.values().forEach { voice ->
                            FilterChip(
                                selected = selectedVoiceType == voice,
                                onClick = { onVoiceTypeChange(voice) },
                                label = { Text(voice.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentRed,
                                    selectedLabelColor = Color.White
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
