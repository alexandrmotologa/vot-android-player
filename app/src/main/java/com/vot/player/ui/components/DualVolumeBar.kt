package com.vot.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vot.player.ui.theme.AccentBlue
import com.vot.player.ui.theme.AccentRed
import com.vot.player.ui.theme.TextPrimary
import com.vot.player.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DualVolumeBar(
    originalVolume: Float,
    voiceoverVolume: Float,
    onOriginalVolumeChange: (Float) -> Unit,
    onVoiceoverVolumeChange: (Float) -> Unit,
    onToggleOriginalMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC1A1A24), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Quick Presets with FlowRow to wrap cleanly on any screen width
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetButton(
                label = "Voice Only",
                isSelected = originalVolume == 0f && voiceoverVolume > 0.8f,
                onClick = {
                    onOriginalVolumeChange(0f)
                    onVoiceoverVolumeChange(1f)
                }
            )
            PresetButton(
                label = "Natural (20%)",
                isSelected = (originalVolume * 100).roundToInt() == 20 && voiceoverVolume > 0.8f,
                onClick = {
                    onOriginalVolumeChange(0.20f)
                    onVoiceoverVolumeChange(1f)
                }
            )
            PresetButton(
                label = "Balanced",
                isSelected = (originalVolume * 100).roundToInt() in 35..45,
                onClick = {
                    onOriginalVolumeChange(0.40f)
                    onVoiceoverVolumeChange(0.80f)
                }
            )
            PresetButton(
                label = "Original",
                isSelected = originalVolume > 0.9f && voiceoverVolume == 0f,
                onClick = {
                    onOriginalVolumeChange(1f)
                    onVoiceoverVolumeChange(0f)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Original Volume Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleOriginalMute,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (originalVolume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Original Volume",
                    tint = if (originalVolume > 0f) AccentBlue else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Orig: ${(originalVolume * 100).roundToInt()}%",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 66.dp)
            )
            Slider(
                value = originalVolume,
                onValueChange = onOriginalVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = Color(0xFF333344)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
        }

        // Voiceover Volume Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {},
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice-over Volume",
                    tint = AccentRed,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Voice: ${(voiceoverVolume * 100).roundToInt()}%",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 66.dp)
            )
            Slider(
                value = voiceoverVolume,
                onValueChange = onVoiceoverVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentRed,
                    activeTrackColor = AccentRed,
                    inactiveTrackColor = Color(0xFF333344)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Color(0xFF3A2840) else Color(0x33FFFFFF),
            contentColor = if (isSelected) AccentRed else Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(30.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            maxLines = 1,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
