package com.vot.player.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vot.player.data.model.*
import com.vot.player.data.pref.PlayerMode
import com.vot.player.data.pref.TranslationTriggerMode
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
    keepScreenOn: Boolean = true,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
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
    onDismiss: () -> Unit
) {
    SettingsBottomSheet(
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
        currentSpeed = currentSpeed,
        onSpeedChange = onSpeedChange,
        availableQualities = availableQualities,
        selectedQuality = selectedQuality,
        onQualityChange = onQualityChange,
        isSponsorBlockEnabled = isSponsorBlockEnabled,
        onSponsorBlockChange = onSponsorBlockChange,
        keepScreenOn = keepScreenOn,
        onKeepScreenOnChange = onKeepScreenOnChange,
        isAudioOnly = isAudioOnly,
        onToggleAudioOnly = onToggleAudioOnly,
        preferredPlayerMode = preferredPlayerMode,
        onPlayerModeChange = onPlayerModeChange,
        translationTriggerMode = translationTriggerMode,
        onTranslationTriggerModeChange = onTranslationTriggerModeChange,
        autoSkipRussianVideos = autoSkipRussianVideos,
        onAutoSkipRussianVideosChange = onAutoSkipRussianVideosChange,
        isTranslationActive = isTranslationActive,
        onTriggerTranslation = onTriggerTranslation,
        isLiveVoiceAvailable = isLiveVoiceAvailable,
        hasSubtitles = hasSubtitles,
        hasRussianSubtitles = hasRussianSubtitles,
        hasRomanianSubtitles = hasRomanianSubtitles,
        hasEnglishSubtitles = hasEnglishSubtitles,
        isCustomVoiceSupported = isCustomVoiceSupported,
        onExportVideoClick = onExportVideoClick,
        onExportAudioClick = onExportAudioClick,
        onDismiss = onDismiss
    )
}
