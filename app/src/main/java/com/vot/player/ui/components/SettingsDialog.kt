package com.vot.player.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vot.player.data.model.*
import com.vot.player.data.pref.PlayerMode
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.DarkCard
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
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
    isLiveVoiceAvailable: Boolean = true,
    hasSubtitles: Boolean = true,
    isCustomVoiceSupported: Boolean = true,
    onExportVideoClick: () -> Unit = {},
    onExportAudioClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "VOT Settings",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Default Player Mode
                Text(text = "Default Watching Mode", color = TextSecondary, fontSize = 13.sp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlayerMode.values().forEach { mode ->
                        FilterChip(
                            selected = preferredPlayerMode == mode,
                            onClick = { onPlayerModeChange(mode) },
                            label = { Text(if (mode == PlayerMode.ASK_EVERY_TIME) "Ask" else mode.displayName.take(12)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // Podcast / Audio-Only Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Audio-Only Mode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(text = "Save 90% battery & data by turning off video", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isAudioOnly,
                        onCheckedChange = { onToggleAudioOnly() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRed)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // SponsorBlock Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "SponsorBlock (Auto-Skip)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Automatically jump over sponsors and intros", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isSponsorBlockEnabled,
                        onCheckedChange = { onSponsorBlockChange(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRed)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // Voice Gender Selection
                Text(text = "Voice Gender (Yandex AI)", color = TextSecondary, fontSize = 13.sp)
                if (!isCustomVoiceSupported) {
                    Text(
                        text = "Auto-detected: Yandex matches original speaker gender automatically",
                        color = Color(0xFF888899),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoiceGender.values().forEach { gender ->
                        val isEnabled = isCustomVoiceSupported || gender == VoiceGender.AUTO
                        FilterChip(
                            selected = selectedVoiceGender == gender,
                            onClick = { if (isEnabled) onVoiceGenderChange(gender) },
                            enabled = isEnabled,
                            label = { Text(gender.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed,
                                selectedLabelColor = Color.White,
                                disabledContainerColor = Color(0x22FFFFFF),
                                disabledLabelColor = Color(0x44FFFFFF)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // Specific Voice Actor
                Text(text = "Preferred Voice Actor", color = TextSecondary, fontSize = 13.sp)
                if (!isCustomVoiceSupported) {
                    Text(
                        text = "Custom actors not available for this video (Yandex uses auto-voice)",
                        color = Color(0xFF888899),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoiceActor.values().forEach { actor ->
                        val isEnabled = isCustomVoiceSupported || actor == VoiceActor.AUTO
                        FilterChip(
                            selected = selectedVoiceActor == actor,
                            onClick = { if (isEnabled) onVoiceActorChange(actor) },
                            enabled = isEnabled,
                            label = { Text(actor.displayName.substringBefore(" ")) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed,
                                selectedLabelColor = Color.White,
                                disabledContainerColor = Color(0x22FFFFFF),
                                disabledLabelColor = Color(0x44FFFFFF)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // Voice Type (Standard vs Live Voice)
                Text(text = "Voice Synthesis Engine", color = TextSecondary, fontSize = 13.sp)
                if (!isLiveVoiceAvailable) {
                    Text(
                        text = "Notice: Only standard voice is provided by Yandex for this video",
                        color = Color(0xFFFFA726),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoiceType.values().forEach { voice ->
                        val isEnabled = voice == VoiceType.STANDARD || isLiveVoiceAvailable
                        FilterChip(
                            selected = selectedVoiceType == voice,
                            onClick = { if (isEnabled) onVoiceTypeChange(voice) },
                            enabled = isEnabled,
                            label = { Text(voice.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed,
                                selectedLabelColor = Color.White,
                                disabledContainerColor = Color(0x22FFFFFF),
                                disabledLabelColor = Color(0x44FFFFFF)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // Subtitles
                Text(text = "Subtitles", color = TextSecondary, fontSize = 13.sp)
                if (!hasSubtitles) {
                    Text(
                        text = "Notice: Subtitles are not available for this video",
                        color = Color(0xFFFFA726),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubtitlesMode.values().forEach { sub ->
                        val isEnabled = sub == SubtitlesMode.OFF || hasSubtitles
                        FilterChip(
                            selected = selectedSubtitles == sub,
                            onClick = { if (isEnabled) onSubtitlesChange(sub) },
                            enabled = isEnabled,
                            label = { Text(sub.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed,
                                selectedLabelColor = Color.White,
                                disabledContainerColor = Color(0x22FFFFFF),
                                disabledLabelColor = Color(0x44FFFFFF)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // Playback Speed
                Text(text = "Playback Speed", color = TextSecondary, fontSize = 13.sp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        FilterChip(
                            selected = currentSpeed == speed,
                            onClick = { onSpeedChange(speed) },
                            label = { Text("${speed}x") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Offline Export Buttons (MP4 & MP3)
                Text(text = "Offline Downloads", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        onExportVideoClick()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Video (MP4 in Downloads)", color = Color.White, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onExportAudioClick()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E4053)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Audio Only (MP3 in Music)", color = Color.White, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = AccentRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
