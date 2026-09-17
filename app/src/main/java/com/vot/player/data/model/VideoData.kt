package com.vot.player.data.model

data class VideoQuality(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String? = null
)

data class YouTubeVideoInfo(
    val videoId: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val streamUrl: String,
    val directAudioUrl: String? = null,
    val thumbnailUrl: String? = null,
    val availableQualities: List<VideoQuality> = emptyList()
)

data class UniversalVideoInfo(
    val id: String,
    val rawUrl: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val streamUrl: String,
    val directAudioUrl: String? = null,
    val thumbnailUrl: String? = null,
    val platform: PlatformType = PlatformType.YOUTUBE,
    val availableQualities: List<VideoQuality> = emptyList()
)

enum class PlatformType(val displayName: String) {
    YOUTUBE("YouTube"),
    TWITCH("Twitch"),
    TIKTOK("TikTok"),
    TWITTER("Twitter/X"),
    DIRECT("Direct Video")
}

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
