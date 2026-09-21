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

enum class VoiceGender(val label: String, val code: String) {
    AUTO("Auto Detect", "auto"),
    MALE("Male Voice", "male"),
    FEMALE("Female Voice", "female")
}

enum class VoiceActor(val displayName: String, val voiceId: String, val gender: VoiceGender) {
    AUTO("Default Actor", "", VoiceGender.AUTO),
    FILIPP("Filipp (Male)", "filipp", VoiceGender.MALE),
    ERMIL("Ermil (Male)", "ermil", VoiceGender.MALE),
    MADIRUS("Madirus (Male)", "madirus", VoiceGender.MALE),
    ALENA("Alena (Female)", "alena", VoiceGender.FEMALE),
    OKSANA("Oksana (Female)", "oksana", VoiceGender.FEMALE),
    JANE("Jane (Female)", "jane", VoiceGender.FEMALE)
}

enum class TargetLanguage(val code: String, val displayName: String) {
    RUSSIAN("ru", "Russian"),
    ROMANIAN("ro", "Romanian"),
    ENGLISH("en", "English")
}

enum class SubtitlesMode(val label: String, val langCode: String?) {
    OFF("Subtitles Off", null),
    RUSSIAN("Russian", "ru"),
    ROMANIAN("Romanian", "ro"),
    ENGLISH("English (Original)", "en")
}
