package com.vot.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun GestureOverlay(
    originalVolume: Float,
    voiceoverVolume: Float,
    onOriginalVolumeChange: (Float) -> Unit,
    onVoiceoverVolumeChange: (Float) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
        return
    }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(gestureFeedback) {
        if (gestureFeedback != null) {
            delay(1200L)
            gestureFeedback = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < size.width / 2
                        if (isLeft) {
                            onSeekRelative(-10_000L)
                            gestureFeedback = "⏪ -10s"
                        } else {
                            onSeekRelative(10_000L)
                            gestureFeedback = "⏩ +10s"
                        }
                    }
                )
            }
            .pointerInput(originalVolume, voiceoverVolume) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val isLeft = change.position.x < size.width / 2
                    val sensitivity = 0.005f
                    val delta = -dragAmount.y * sensitivity

                    if (isLeft) {
                        val newVol = (originalVolume + delta).coerceIn(0f, 1f)
                        onOriginalVolumeChange(newVol)
                        gestureFeedback = "Original: ${(newVol * 100).roundToInt()}%"
                    } else {
                        val newVol = (voiceoverVolume + delta).coerceIn(0f, 1f)
                        onVoiceoverVolumeChange(newVol)
                        gestureFeedback = "Voice: ${(newVol * 100).roundToInt()}%"
                    }
                }
            }
    ) {
        content()

        // Floating gesture feedback pill
        AnimatedVisibility(
            visible = gestureFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = gestureFeedback ?: "",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
    }
}
