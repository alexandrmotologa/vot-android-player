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
    val isSuccess: Boolean get() = status == 1 && !url.isNullOrEmpty()
    val isWaiting: Boolean get() = status == 2 || status == 6 || (status == 0 && remainingTime > 0)
    val isAudioRequested: Boolean get() = status == 3
    val isFailed: Boolean get() = status == 5
}

data class SubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)
