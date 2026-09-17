package com.vot.player.data.model

data class YouTubeVideoInfo(
    val videoId: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val streamUrl: String,
    val directAudioUrl: String? = null
)

enum class VoiceType(val label: String) {
    STANDARD("Standard"),
    LIVE_VOICE("Live Voice (Cloned)")
}

enum class TargetLanguage(val code: String, val displayName: String) {
    RUSSIAN("ru", "Russian"),
    ENGLISH("en", "English")
}

enum class SubtitlesMode(val label: String) {
    OFF("Subtitles Off"),
    RUSSIAN("Russian")
}
