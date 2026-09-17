package com.vot.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vot.player.data.model.SubtitlesMode
import com.vot.player.data.model.TargetLanguage
import com.vot.player.data.model.VideoQuality
import com.vot.player.data.model.VoiceType
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.DarkCard
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    selectedVoiceType: VoiceType,
    onVoiceTypeChange: (VoiceType) -> Unit,
    selectedSubtitles: SubtitlesMode,
    onSubtitlesChange: (SubtitlesMode) -> Unit,
    selectedLanguage: TargetLanguage,
    onLanguageChange: (TargetLanguage) -> Unit,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    availableQualities: List<VideoQuality> = emptyList(),
    selectedQuality: VideoQuality? = null,
    onQualityChange: (VideoQuality) -> Unit = {},
    onExportClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            modifier = Modifier
                .fillMaxWidth()
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

                // Video Resolution / Quality (1080p / 720p / 480p / 360p)
                if (availableQualities.isNotEmpty()) {
                    Text(text = "Video Quality", color = TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableQualities.take(4).forEach { quality ->
                            FilterChip(
                                selected = selectedQuality?.height == quality.height,
                                onClick = { onQualityChange(quality) },
                                label = { Text(quality.label) },
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
                }

                // Voice Type (Standard vs Live Voice)
                Text(text = "Voice Synthesis Type", color = TextSecondary, fontSize = 13.sp)
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

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0x22FFFFFF))
                Spacer(modifier = Modifier.height(14.dp))

                // Subtitles
                Text(text = "Subtitles", color = TextSecondary, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubtitlesMode.values().forEach { sub ->
                        FilterChip(
                            selected = selectedSubtitles == sub,
                            onClick = { onSubtitlesChange(sub) },
                            label = { Text(sub.label) },
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

                // Target Language
                Text(text = "Translation Language", color = TextSecondary, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TargetLanguage.values().forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.displayName) },
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

                // Playback Speed
                Text(text = "Playback Speed", color = TextSecondary, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
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

                Spacer(modifier = Modifier.height(16.dp))

                // Offline Export Button
                Button(
                    onClick = {
                        onExportClick()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Save Video Offline (Export MP4)", color = Color.White, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

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
