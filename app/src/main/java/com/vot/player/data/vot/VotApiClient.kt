package com.vot.player.data.vot

import android.util.Base64
import com.vot.player.data.model.SubtitleCue
import com.vot.player.data.model.TargetLanguage
import com.vot.player.data.model.VoiceType
import com.vot.player.data.model.VotTranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class VotApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val workerHosts: List<String> = listOf("vot-worker.eu.cc", "vot-worker.vtrans.eu.cc")
) {
    private var currentHostIndex = 0
    private val workerHost: String get() = workerHosts[currentHostIndex % workerHosts.size]

    private fun rotateHost() {
        currentHostIndex = (currentHostIndex + 1) % workerHosts.size
    }
    companion object {
        private const val HMAC_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf"
        private const val COMPONENT_VERSION = "26.8.3.971"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 YaBrowser/26.8.0.0 Safari/537.36"
    }

    private data class SessionData(
        val uuid: String,
        val secretKey: String,
        val expiresAtMs: Long
    )

    private var activeSession: SessionData? = null

    private fun generateUUID(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun signHmacSha256(data: ByteArray, key: String = HMAC_KEY): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val signedBytes = mac.doFinal(data)
        return signedBytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun getOrCreateSession(): SessionData = withContext(Dispatchers.IO) {
        val current = activeSession
        val now = System.currentTimeMillis()
        if (current != null && current.expiresAtMs > now + 60_000) {
            return@withContext current
        }

        val uuid = generateUUID()
        val sessionBody = VotProtobuf.encodeSessionRequest(uuid, "video-translation")
        val bodySign = signHmacSha256(sessionBody)

        val innerHeaders = JSONObject().apply {
            put("Accept", "application/x-protobuf")
            put("Content-Type", "application/x-protobuf")
            put("User-Agent", USER_AGENT)
            put("Vtrans-Signature", bodySign)
        }

        val encodedHeaders = Base64.encodeToString(
            innerHeaders.toString().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        val request = Request.Builder()
            .url("https://$workerHost/session/create")
            .post(sessionBody.toRequestBody("application/x-protobuf".toMediaType()))
            .header("User-Agent", "vot.js/3.1.0")
            .header("X-VOT-Headers", encodedHeaders)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to create VOT session: HTTP ${response.code}")
        }

        val responseBytes = response.body?.bytes()
            ?: throw Exception("Empty session response")

        val (secretKey, expiresSeconds) = VotProtobuf.decodeSessionResponse(responseBytes)
        val session = SessionData(
            uuid = uuid,
            secretKey = secretKey,
            expiresAtMs = now + (expiresSeconds.toLong() * 1000)
        )
        activeSession = session
        session
    }

    suspend fun translateVideo(
        videoUrl: String,
        durationSeconds: Double,
        targetLang: TargetLanguage = TargetLanguage.RUSSIAN,
        voiceType: VoiceType = VoiceType.STANDARD,
        preferredVoice: String = "",
        onProgress: ((String) -> Unit)? = null
    ): Result<VotTranslationResult> = withContext(Dispatchers.IO) {
        try {
            var firstRequest = true
            var attempts = 0
            val maxAttempts = 15

            while (attempts < maxAttempts) {
                attempts++
                val session = getOrCreateSession()
                val path = "/video-translation/translate"
                val token = "${session.uuid}:$path:$COMPONENT_VERSION"
                val tokenSign = signHmacSha256(token.toByteArray(Charsets.UTF_8))

                val requestBytes = VotProtobuf.encodeTranslationRequest(
                    url = videoUrl,
                    duration = durationSeconds,
                    responseLang = targetLang.code,
                    requestLang = if (targetLang == TargetLanguage.RUSSIAN) "en" else "ru",
                    firstRequest = firstRequest,
                    useLivelyVoice = voiceType == VoiceType.LIVE_VOICE,
                    selectedVoice = preferredVoice
                )
                firstRequest = false

                val bodySign = signHmacSha256(requestBytes)

                val innerHeaders = JSONObject().apply {
                    put("Accept", "application/x-protobuf")
                    put("Content-Type", "application/x-protobuf")
                    put("User-Agent", USER_AGENT)
                    put("Sec-Vtrans-Token", "$tokenSign:$token")
                    put("Sec-Vtrans-Sk", session.secretKey)
                    put("Vtrans-Signature", bodySign)
                }

                val encodedHeaders = Base64.encodeToString(
                    innerHeaders.toString().toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )

                val request = Request.Builder()
                    .url("https://$workerHost$path")
                    .post(requestBytes.toRequestBody("application/x-protobuf".toMediaType()))
                    .header("User-Agent", "vot.js/3.1.0")
                    .header("X-VOT-Headers", encodedHeaders)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Translation request failed: HTTP ${response.code}"))
                }

                val responseBytes = response.body?.bytes()
                    ?: return@withContext Result.failure(Exception("Empty translation response"))

                val result = VotProtobuf.decodeTranslationResponse(responseBytes)

                if (result.isSuccess) {
                    onProgress?.invoke("Translation ready!")
                    return@withContext Result.success(result)
                } else if (result.isWaiting) {
                    val waitSec = if (result.remainingTime > 0) result.remainingTime.coerceIn(1, 10) else 2
                    onProgress?.invoke(
                        if (voiceType == VoiceType.LIVE_VOICE) 
                            "Generating Live Voice (~${result.remainingTime}s remaining)..."
                        else 
                            "Generating voice-over translation (~${result.remainingTime}s remaining)..."
                    )
                    delay(waitSec * 1000L)
                } else if (result.isFailed) {
                    return@withContext Result.failure(Exception(result.message ?: "Yandex translation failed for this video."))
                } else {
                    delay(2000L)
                }
            }

            Result.failure(Exception("Translation timed out after waiting."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSubtitles(
        videoUrl: String,
        targetLang: TargetLanguage = TargetLanguage.RUSSIAN
    ): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        try {
            val session = getOrCreateSession()
            val path = "/video-subtitles/get-subtitles"
            val token = "${session.uuid}:$path:$COMPONENT_VERSION"
            val tokenSign = signHmacSha256(token.toByteArray(Charsets.UTF_8))

            val requestBytes = VotProtobuf.encodeSubtitlesRequest(
                url = videoUrl,
                language = if (targetLang == TargetLanguage.RUSSIAN) "en" else "ru"
            )
            val bodySign = signHmacSha256(requestBytes)

            val innerHeaders = JSONObject().apply {
                put("Accept", "application/x-protobuf")
                put("Content-Type", "application/x-protobuf")
                put("User-Agent", USER_AGENT)
                put("Sec-Vsubs-Token", "$tokenSign:$token")
                put("Sec-Vsubs-Sk", session.secretKey)
                put("Vsubs-Signature", bodySign)
            }

            val encodedHeaders = Base64.encodeToString(
                innerHeaders.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

            val request = Request.Builder()
                .url("https://$workerHost$path")
                .post(requestBytes.toRequestBody("application/x-protobuf".toMediaType()))
                .header("User-Agent", "vot.js/3.1.0")
                .header("X-VOT-Headers", encodedHeaders)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Subtitles request failed: HTTP ${response.code}"))
            }

            val responseBytes = response.body?.bytes()
                ?: return@withContext Result.failure(Exception("Empty subtitles response"))

            val subtitleUrls = VotProtobuf.decodeSubtitlesResponse(responseBytes)
            if (subtitleUrls.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // Download and parse the first subtitle file
            val subUrl = subtitleUrls.first()
            val subRequest = Request.Builder().url(subUrl).get().build()
            val subResponse = okHttpClient.newCall(subRequest).execute()
            val subText = subResponse.body?.string() ?: ""

            val cues = parseSubtitles(subText)
            Result.success(cues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSubtitles(raw: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            if (raw.trim().startsWith("[")) {
                // Yandex JSON format: [ { "start": 1.2, "end": 3.4, "text": "..." } ]
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val start = (item.optDouble("start", 0.0) * 1000).toLong()
                    val end = (item.optDouble("end", 0.0) * 1000).toLong()
                    val text = item.optString("text", "")
                    if (text.isNotEmpty()) {
                        cues.add(SubtitleCue(start, end, text))
                    }
                }
            } else if (raw.contains("-->")) {
                // VTT / SRT format
                val lines = raw.lines()
                var i = 0
                while (i < lines.size) {
                    val line = lines[i].trim()
                    if (line.contains("-->")) {
                        val parts = line.split("-->").map { it.trim() }
                        val startMs = parseTimestamp(parts[0])
                        val endMs = parseTimestamp(parts.getOrElse(1) { "" }.split(" ")[0])
                        val textLines = mutableListOf<String>()
                        i++
                        while (i < lines.size && lines[i].trim().isNotEmpty()) {
                            textLines.add(lines[i].trim())
                            i++
                        }
                        cues.add(SubtitleCue(startMs, endMs, textLines.joinToString(" ")))
                    }
                    i++
                }
            }
        } catch (_: Exception) {}
        return cues
    }

    private fun parseTimestamp(ts: String): Long {
        return try {
            val clean = ts.replace(",", ".")
            val parts = clean.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val s = parts[2].toDouble()
                (h * 3600_000) + (m * 60_000) + (s * 1000).toLong()
            } else if (parts.size == 2) {
                val m = parts[0].toLong()
                val s = parts[1].toDouble()
                (m * 60_000) + (s * 1000).toLong()
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
