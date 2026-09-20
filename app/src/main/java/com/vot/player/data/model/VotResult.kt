package com.vot.player.data.model

data class VotTranslationResult(
    val url: String? = null,
    val duration: Double = 0.0,
    val status: Int = 0,
    val remainingTime: Int = -1,
    val translationId: String = "",
    val isLivelyVoice: Boolean = false,
    val message: String? = null
) {
    // Status 1 = FINISHED, Status 5 = PART_CONTENT (both have playable audio url)
    val isSuccess: Boolean get() = (status == 1 || status == 5) && !url.isNullOrEmpty()
    // Status 2 = WAITING, Status 3 = LONG_WAITING, Status 6 = AUDIO_REQUESTED
    val isWaiting: Boolean get() = status == 2 || status == 3 || status == 6 || (status == 0 && remainingTime > 0)
    val isAudioRequested: Boolean get() = status == 6
    val isFailed: Boolean get() = (status == 0 && remainingTime <= 0 && !message.isNullOrEmpty()) || (status != 1 && status != 2 && status != 3 && status != 5 && status != 6)
}

data class SubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)
