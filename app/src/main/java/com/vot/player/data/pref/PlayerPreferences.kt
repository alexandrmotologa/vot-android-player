package com.vot.player.data.pref

import android.content.Context
import android.content.SharedPreferences
import com.vot.player.data.model.SubtitlesMode
import com.vot.player.data.model.VoiceType

enum class PlayerMode(val displayName: String, val description: String) {
    ASK_EVERY_TIME("Ask every time", "Prompt to choose between Native and Web player"),
    NATIVE_PLAYER("Native Player", "Ad-free minimalist player with 4K, PiP and gesture controls"),
    YOUTUBE_WEB("YouTube Web View", "Full YouTube interface with comments, likes and account sign-in")
}

enum class TranslationTriggerMode(val displayName: String, val description: String) {
    ALWAYS_AUTO("Automatic (Recommended)", "Always translate video as soon as it opens"),
    ASK_EVERY_TIME("Ask every time", "Prompt before requesting voice-over translation"),
    MANUAL("Manual only", "Play in original audio; translate only when tapped")
}

class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vot_player_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PLAYER_MODE = "pref_player_mode"
        private const val KEY_VOICE_TYPE = "pref_voice_type"
        private const val KEY_SUBTITLES_MODE = "pref_subtitles_mode"
        private const val KEY_SPONSOR_BLOCK = "pref_sponsor_block"
        private const val KEY_AUDIO_ONLY = "pref_audio_only"
        private const val KEY_VOICE_GENDER = "pref_voice_gender"
        private const val KEY_VOICE_ACTOR = "pref_voice_actor"
        private const val KEY_TRANSLATION_TRIGGER = "pref_translation_trigger"
        private const val KEY_SKIP_RUSSIAN = "pref_skip_russian"

        @Volatile
        private var instance: PlayerPreferences? = null

        fun getInstance(context: Context): PlayerPreferences {
            return instance ?: synchronized(this) {
                instance ?: PlayerPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    var preferredPlayerMode: PlayerMode
        get() {
            val name = prefs.getString(KEY_PLAYER_MODE, PlayerMode.ASK_EVERY_TIME.name)
            return try {
                PlayerMode.valueOf(name ?: PlayerMode.ASK_EVERY_TIME.name)
            } catch (_: Exception) {
                PlayerMode.ASK_EVERY_TIME
            }
        }
        set(value) {
            prefs.edit().putString(KEY_PLAYER_MODE, value.name).commit()
        }

    var preferredVoiceType: VoiceType
        get() {
            val name = prefs.getString(KEY_VOICE_TYPE, VoiceType.LIVE_VOICE.name)
            return try {
                VoiceType.valueOf(name ?: VoiceType.LIVE_VOICE.name)
            } catch (_: Exception) {
                VoiceType.LIVE_VOICE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_VOICE_TYPE, value.name).commit()
        }

    var preferredSubtitlesMode: SubtitlesMode
        get() {
            val name = prefs.getString(KEY_SUBTITLES_MODE, SubtitlesMode.OFF.name)
            return try {
                SubtitlesMode.valueOf(name ?: SubtitlesMode.OFF.name)
            } catch (_: Exception) {
                SubtitlesMode.OFF
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SUBTITLES_MODE, value.name).commit()
        }

    var isSponsorBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPONSOR_BLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SPONSOR_BLOCK, value).apply()

    var isAudioOnlyDefault: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_ONLY, value).apply()

    var preferredVoiceGender: String
        get() = prefs.getString(KEY_VOICE_GENDER, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_VOICE_GENDER, value).apply()

    var preferredVoiceActor: String
        get() = prefs.getString(KEY_VOICE_ACTOR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VOICE_ACTOR, value).apply()

    var translationTriggerMode: TranslationTriggerMode
        get() {
            val name = prefs.getString(KEY_TRANSLATION_TRIGGER, TranslationTriggerMode.ALWAYS_AUTO.name)
            return try {
                TranslationTriggerMode.valueOf(name ?: TranslationTriggerMode.ALWAYS_AUTO.name)
            } catch (_: Exception) {
                TranslationTriggerMode.ALWAYS_AUTO
            }
        }
        set(value) {
            prefs.edit().putString(KEY_TRANSLATION_TRIGGER, value.name).commit()
        }

    var autoSkipRussianVideos: Boolean
        get() = prefs.getBoolean(KEY_SKIP_RUSSIAN, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_RUSSIAN, value).apply()
}
