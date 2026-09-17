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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.vot.player.data.model.SubtitlesMode
import com.vot.player.data.model.TargetLanguage
import com.vot.player.data.model.VoiceType
import com.vot.player.data.vot.VotApiClient
import com.vot.player.data.youtube.YouTubeStreamExtractor
import com.vot.player.player.VotPlayerManager
import com.vot.player.ui.PlayerScreen
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.DarkBackground
import com.vot.player.ui.theme.DarkCard
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary
import com.vot.player.ui.theme.VotPlayerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "com.vot.player.EXTRA_VIDEO_URL"
    }

    private lateinit var playerManager: VotPlayerManager
    private val streamExtractor = YouTubeStreamExtractor()
    private val votApiClient = VotApiClient()

    private var activeVideoUrl by mutableStateOf<String?>(null)
    private var videoTitle by mutableStateOf("VOT Player")
    private var videoAuthor by mutableStateOf("")
    private var statusMessage by mutableStateOf<String?>(null)
    private var isLoading by mutableStateOf(false)

    private var selectedVoiceType by mutableStateOf(VoiceType.STANDARD)
    private var selectedSubtitles by mutableStateOf(SubtitlesMode.OFF)
    private var selectedLanguage by mutableStateOf(TargetLanguage.RUSSIAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerManager = VotPlayerManager(this, lifecycleScope)

        handleIntent(intent)

        setContent {
            VotPlayerTheme {
                Scaffold(
                    containerColor = DarkBackground,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    if (activeVideoUrl != null) {
                        PlayerScreen(
                            playerManager = playerManager,
                            videoTitle = videoTitle,
                            videoAuthor = videoAuthor,
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        HomeScreen(
                            onPlayUrl = { url -> loadVideoAndTranslate(url) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
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

    private fun loadVideoAndTranslate(rawUrl: String) {
        activeVideoUrl = rawUrl
        isLoading = true
        statusMessage = "Resolving video stream..."

        lifecycleScope.launch {
            // 1. Extract video stream from YouTube
            val streamResult = streamExtractor.extractStreamInfo(rawUrl)
            if (streamResult.isFailure) {
                isLoading = false
                statusMessage = "Error: ${streamResult.exceptionOrNull()?.message}"
                return@launch
            }

            val videoInfo = streamResult.getOrThrow()
            videoTitle = videoInfo.title
            videoAuthor = videoInfo.author
            statusMessage = "Requesting Yandex voice-over translation..."

            // 2. Request Translation from VOT API
            val votResult = votApiClient.translateVideo(
                videoUrl = "https://www.youtube.com/watch?v=${videoInfo.videoId}",
                durationSeconds = videoInfo.durationSeconds.toDouble(),
                targetLang = selectedLanguage,
                voiceType = selectedVoiceType,
                onProgress = { statusMessage = it }
            )

            // 3. Request Subtitles if enabled
            val subtitlesResult = votApiClient.getSubtitles(
                videoUrl = "https://www.youtube.com/watch?v=${videoInfo.videoId}",
                targetLang = selectedLanguage
            )
            val cues = subtitlesResult.getOrDefault(emptyList())

            isLoading = false
            statusMessage = null

            val translatedAudioUrl = votResult.getOrNull()?.url
            playerManager.prepare(
                videoStreamUrl = videoInfo.streamUrl,
                voiceoverAudioUrl = translatedAudioUrl,
                subtitles = cues
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

@Composable
private fun HomeScreen(
    onPlayUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputUrl by remember { mutableStateOf("") }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (YouTubeStreamExtractor.extractVideoId(text) != null) {
                clipboardUrl = text
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "VOT Player",
            color = TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Voice-Over Translation for YouTube",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Clipboard 1-Tap Card
        if (clipboardUrl != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Link detected in clipboard:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = clipboardUrl ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onPlayUrl(clipboardUrl!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play with Voice-Over")
                    }
                }
            }
        }

        // Manual Paste Box
        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            label = { Text("Paste YouTube Video URL") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentRed,
                unfocusedBorderColor = Color(0x44FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                if (inputUrl.isNotBlank()) onPlayUrl(inputUrl.trim())
            },
            enabled = inputUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open & Translate", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "💡 Tip: You can also open any YouTube video in the official YouTube app and tap Share -> VOT Player!",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}
