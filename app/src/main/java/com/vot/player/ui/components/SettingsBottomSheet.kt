package com.vot.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vot.player.data.model.*
import com.vot.player.data.pref.PlayerMode
import com.vot.player.data.pref.TranslationTriggerMode
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.DarkCard
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary

enum class SettingsCategory(val title: String, val icon: ImageVector) {
    AUDIO_VOICE("Voice & Audio", Icons.Default.RecordVoiceOver),
    SUBTITLES_SPEED("Subs & Speed", Icons.Default.Subtitles),
    PLAYER_QUALITY("Player & Quality", Icons.Default.VideoSettings),
    DOWNLOADS("Offline", Icons.Default.Download)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
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
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    availableQualities: List<VideoQuality> = emptyList(),
    selectedQuality: VideoQuality? = null,
    onQualityChange: (VideoQuality) -> Unit = {},
    isSponsorBlockEnabled: Boolean = true,
    onSponsorBlockChange: (Boolean) -> Unit = {},
    isAudioOnly: Boolean = false,
    onToggleAudioOnly: () -> Unit = {},
    preferredPlayerMode: PlayerMode = PlayerMode.ASK_EVERY_TIME,
    onPlayerModeChange: (PlayerMode) -> Unit = {},
    translationTriggerMode: TranslationTriggerMode = TranslationTriggerMode.ALWAYS_AUTO,
    onTranslationTriggerModeChange: (TranslationTriggerMode) -> Unit = {},
    autoSkipRussianVideos: Boolean = true,
    onAutoSkipRussianVideosChange: (Boolean) -> Unit = {},
    isTranslationActive: Boolean = true,
    onTriggerTranslation: () -> Unit = {},
    isLiveVoiceAvailable: Boolean = true,
    hasSubtitles: Boolean = true,
    hasRussianSubtitles: Boolean = true,
    hasRomanianSubtitles: Boolean = true,
    hasEnglishSubtitles: Boolean = true,
    isCustomVoiceSupported: Boolean = true,
    onExportVideoClick: () -> Unit = {},
    onExportAudioClick: () -> Unit = {},
    originalVolume: Float? = null,
    voiceoverVolume: Float? = null,
    onOriginalVolumeChange: ((Float) -> Unit)? = null,
    onVoiceoverVolumeChange: ((Float) -> Unit)? = null,
    onToggleOriginalMute: (() -> Unit)? = null,
    onSwitchToNativePlayer: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf(SettingsCategory.AUDIO_VOICE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14141E),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0x66FFFFFF),
                height = 4.dp,
                width = 36.dp
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(AccentRed.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "VOT Settings & Controls",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Customize voice-over, subtitles & playback",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0x22FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Category Tabs (Ergonomic Pill Segmented Control)
            ScrollableTabRow(
                selectedTabIndex = selectedCategory.ordinal,
                containerColor = Color(0xFF1B1B28),
                contentColor = Color.White,
                edgePadding = 0.dp,
                divider = {},
                indicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp)
            ) {
                SettingsCategory.values().forEach { cat ->
                    val isSelected = selectedCategory == cat
                    val bg = if (isSelected) AccentRed else Color.Transparent
                    val textColor = if (isSelected) Color.White else Color(0xFFAAAAAA)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.title,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Content per Category
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedCategory) {
                    SettingsCategory.AUDIO_VOICE -> {
                        // Immediate Translate Action (if translation is currently not active)
                        if (!isTranslationActive) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, AccentRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onTriggerTranslation()
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = AccentRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Translate Now to Russian",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tap to synthesize synchronized neural voice-over",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AccentRed
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Optional Dual Volume Bar (when available, e.g., in Web View)
                        if (originalVolume != null && voiceoverVolume != null && onOriginalVolumeChange != null && onVoiceoverVolumeChange != null) {
                            SettingsCard(title = "Volume Levels", icon = Icons.AutoMirrored.Filled.VolumeUp) {
                                DualVolumeBar(
                                    originalVolume = originalVolume,
                                    voiceoverVolume = voiceoverVolume,
                                    onOriginalVolumeChange = onOriginalVolumeChange,
                                    onVoiceoverVolumeChange = onVoiceoverVolumeChange,
                                    onToggleOriginalMute = { onToggleOriginalMute?.invoke() }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Translation Trigger Mode & Russian Detection
                        SettingsCard(title = "Translation Trigger", icon = Icons.Default.AutoAwesome) {
                            Text(
                                text = "How voice-over translation starts for videos:",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TranslationTriggerMode.values().forEach { mode ->
                                    FilterChip(
                                        selected = translationTriggerMode == mode,
                                        onClick = { onTranslationTriggerModeChange(mode) },
                                        label = { Text(mode.displayName, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Skip videos already in Russian",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Saves proxy quota and avoids dubbing over native Russian audio",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = autoSkipRussianVideos,
                                    onCheckedChange = { onAutoSkipRussianVideosChange(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AccentRed
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Voice Synthesis Engine
                        SettingsCard(title = "Voice Synthesis Engine", icon = Icons.Default.Psychology) {
                            if (!isLiveVoiceAvailable) {
                                Text(
                                    text = "Notice: Only standard voice is provided by Yandex for this video",
                                    color = Color(0xFFFFA726),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VoiceType.values().forEach { voice ->
                                    val isEnabled = voice == VoiceType.STANDARD || isLiveVoiceAvailable
                                    FilterChip(
                                        selected = selectedVoiceType == voice,
                                        onClick = { if (isEnabled) onVoiceTypeChange(voice) },
                                        enabled = isEnabled,
                                        label = { Text(voice.label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentRed,
                                            selectedLabelColor = Color.White,
                                            disabledContainerColor = Color(0x11FFFFFF),
                                            disabledLabelColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Voice Gender & Character
                        SettingsCard(title = "Voice Gender & Tone", icon = Icons.Default.Face) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VoiceGender.values().forEach { gender ->
                                    FilterChip(
                                        selected = selectedVoiceGender == gender,
                                        onClick = { onVoiceGenderChange(gender) },
                                        label = { Text(gender.label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Podcast / Audio-Only Mode Switch
                        SettingsCard(title = "Battery & Data Saver", icon = Icons.Default.Headphones) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Audio-Only Mode (Podcast)",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Saves 90% battery & mobile data by turning off video decode",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isAudioOnly,
                                    onCheckedChange = { onToggleAudioOnly() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AccentRed
                                    )
                                )
                            }
                        }
                    }

                    SettingsCategory.SUBTITLES_SPEED -> {
                        // Subtitles
                        SettingsCard(title = "Subtitles Language", icon = Icons.Default.Subtitles) {
                            val anySubtitlesAvailable = hasSubtitles || hasRussianSubtitles || hasRomanianSubtitles || hasEnglishSubtitles
                            if (!anySubtitlesAvailable) {
                                Text(
                                    text = "Notice: Subtitles are not available for this video",
                                    color = Color(0xFFFFA726),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SubtitlesMode.values().forEach { sub ->
                                    val isEnabled = when (sub) {
                                        SubtitlesMode.OFF -> true
                                        SubtitlesMode.RUSSIAN -> hasRussianSubtitles || hasSubtitles
                                        SubtitlesMode.ROMANIAN -> hasRomanianSubtitles || hasSubtitles
                                        SubtitlesMode.ENGLISH -> hasEnglishSubtitles || hasSubtitles
                                    }
                                    FilterChip(
                                        selected = selectedSubtitles == sub,
                                        onClick = { if (isEnabled) onSubtitlesChange(sub) },
                                        enabled = isEnabled,
                                        label = { Text(sub.label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentRed,
                                            selectedLabelColor = Color.White,
                                            disabledContainerColor = Color(0x11FFFFFF),
                                            disabledLabelColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Playback Speed
                        SettingsCard(title = "Playback Speed", icon = Icons.Default.Speed) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    FilterChip(
                                        selected = currentSpeed == speed,
                                        onClick = { onSpeedChange(speed) },
                                        label = { Text("${speed}x", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentRed,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    SettingsCategory.PLAYER_QUALITY -> {
                        // Default Player Mode
                        SettingsCard(title = "Watching Mode Preference", icon = Icons.Default.OndemandVideo) {
                            var currentMode by remember(preferredPlayerMode) { mutableStateOf(preferredPlayerMode) }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlayerMode.values().forEach { mode ->
                                    val isSelected = currentMode == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            currentMode = mode
                                            onPlayerModeChange(mode)
                                        },
                                        label = {
                                            Text(
                                                when (mode) {
                                                    PlayerMode.ASK_EVERY_TIME -> "Ask Every Time"
                                                    PlayerMode.NATIVE_PLAYER -> "Native Player (Ad-Free)"
                                                    PlayerMode.YOUTUBE_WEB -> "YouTube Web"
                                                },
                                                fontSize = 12.sp
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentRed,
                                            selectedLabelColor = Color.White,
                                            selectedLeadingIconColor = Color.White
                                        )
                                    )
                                }
                            }
                            Text(
                                text = currentMode.description,
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // SponsorBlock Auto-Skip
                        SettingsCard(title = "SponsorBlock Protection", icon = Icons.Default.Shield) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Skip Sponsors & Intros",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Automatically jumps over sponsored segments and non-music intros",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isSponsorBlockEnabled,
                                    onCheckedChange = { onSponsorBlockChange(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AccentRed
                                    )
                                )
                            }
                        }

                        // Video Qualities (if available)
                        if (availableQualities.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            SettingsCard(title = "Resolution & Video Quality", icon = Icons.Default.HighQuality) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableQualities.forEach { quality ->
                                        FilterChip(
                                            selected = selectedQuality == quality,
                                            onClick = { onQualityChange(quality) },
                                            label = { Text(quality.label, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AccentRed,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Switch to Native Player Button (if in Web View)
                        if (onSwitchToNativePlayer != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onSwitchToNativePlayer()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = AccentRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Switch to Native Player (Ad-Free, 4K, PiP)", fontSize = 13.sp)
                            }
                        }
                    }

                    SettingsCategory.DOWNLOADS -> {
                        SettingsCard(title = "Offline Storage", icon = Icons.Default.DownloadForOffline) {
                            Text(
                                text = "Export high quality translations for offline enjoyment",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Button(
                                onClick = {
                                    onExportVideoClick()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Video (MP4 in Downloads)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    onExportAudioClick()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E4053)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Audio Only (MP3 in Music)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color(0xFF1E1E2C),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}
