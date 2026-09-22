package com.vot.player.data.vot

import com.vot.player.data.model.SubtitleCue
import com.vot.player.data.model.TargetLanguage
import com.vot.player.data.model.VoiceType
import com.vot.player.data.model.VotTranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import android.webkit.CookieManager
import okhttp3.FormBody

data class MultiSubtitles(
    val russianCues: List<SubtitleCue> = emptyList(),
    val romanianCues: List<SubtitleCue> = emptyList(),
    val englishCues: List<SubtitleCue> = emptyList()
)

data class DualSubtitles(
    val russianCues: List<SubtitleCue> = emptyList(),
    val englishCues: List<SubtitleCue> = emptyList()
)

class VotApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val hosts: List<String> = listOf(
        "api.browser.yandex.ru",
        "vot-worker.vtrans.eu.cc",
        "vot-worker.toil.cc",
        "vot-worker.eu.cc"
    )
) {
    private var currentHostIndex = 0
    private val currentHost: String get() = hosts[currentHostIndex % hosts.size]
    private val isDirectHost: Boolean get() = currentHost == DIRECT_HOST

    private fun rotateHost() {
        activeSession = null
        currentHostIndex = (currentHostIndex + 1) % hosts.size
    }

    private fun resetToPrimaryHost() {
        if (currentHostIndex != 0) {
            currentHostIndex = 0
            activeSession = null
        }
    }

    companion object {
        private const val DIRECT_HOST = "api.browser.yandex.ru"
        private const val HMAC_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf"
        private const val COMPONENT_VERSION = "26.8.3.1002"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 YaBrowser/26.8.0.0 Safari/537.36"
        private const val SEC_CH_UA = "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"YaBrowser\";v=\"26.8\", \"Yowser\";v=\"2.5\""
        private const val SEC_CH_UA_FULL_VERSION_LIST = "\"Not;A=Brand\";v=\"8.0.0.0\", \"Chromium\";v=\"150.0.7871.1002\", \"YaBrowser\";v=\"26.8.3.1002\", \"Yowser\";v=\"2.5\""

        fun normalizeVideoUrl(rawUrl: String): Pair<String, String?> {
            val trimmed = rawUrl.trim()
            val patterns = listOf(
                Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})"),
                Pattern.compile("^([a-zA-Z0-9_-]{11})$")
            )
            for (p in patterns) {
                val m = p.matcher(trimmed)
                if (m.find()) {
                    val id = m.group(1)
                    return Pair("https://youtu.be/$id", id)
                }
            }
            return Pair(trimmed, null)
        }
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

    private fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun buildJsonHeaders(vararg pairs: Pair<String, String>): String {
        val escaped = pairs.joinToString(",") { (k, v) ->
            val safeV = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            "\"$k\":\"$safeV\""
        }
        return "{$escaped}"
    }

    private fun executeYaRequest(
        path: String,
        body: ByteArray,
        secType: String = "Vtrans",
        session: SessionData,
        method: String = "POST",
        contentType: String = "application/x-protobuf",
        acceptType: String = "application/x-protobuf",
        withSecHeaders: Boolean = true
    ): okhttp3.Response {
        val host = currentHost
        val reqBuilder = Request.Builder()
            .url("https://$host$path")

        if (withSecHeaders) {
            val token = "${session.uuid}:$path:$COMPONENT_VERSION"
            val tokenSign = signHmacSha256(token.toByteArray(Charsets.UTF_8))
            val bodySign = signHmacSha256(body)

            if (host == DIRECT_HOST) {
                reqBuilder
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", contentType)
                    .header("Accept", acceptType)
                    .header("$secType-Signature", bodySign)
                    .header("Sec-$secType-Token", "$tokenSign:$token")
                    .header("Sec-$secType-Sk", session.secretKey)
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-full-version-list", SEC_CH_UA_FULL_VERSION_LIST)
                    .header("Sec-Fetch-Mode", "no-cors")
            } else {
                val innerHeaders = buildJsonHeaders(
                    "Accept" to acceptType,
                    "Content-Type" to contentType,
                    "User-Agent" to USER_AGENT,
                    "Sec-$secType-Token" to "$tokenSign:$token",
                    "Sec-$secType-Sk" to session.secretKey,
                    "$secType-Signature" to bodySign
                )
                val encodedHeaders = encodeBase64(innerHeaders.toByteArray(Charsets.UTF_8))
                reqBuilder
                    .header("User-Agent", "vot.js/3.1.2")
                    .header("Content-Type", contentType)
                    .header("X-VOT-Headers", encodedHeaders)
            }
        } else {
            if (host == DIRECT_HOST) {
                reqBuilder
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", contentType)
                    .header("Accept", acceptType)
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-full-version-list", SEC_CH_UA_FULL_VERSION_LIST)
                    .header("Sec-Fetch-Mode", "no-cors")
            } else {
                val innerHeaders = buildJsonHeaders(
                    "Accept" to acceptType,
                    "Content-Type" to contentType,
                    "User-Agent" to USER_AGENT
                )
                val encodedHeaders = encodeBase64(innerHeaders.toByteArray(Charsets.UTF_8))
                reqBuilder
                    .header("User-Agent", "vot.js/3.1.2")
                    .header("Content-Type", contentType)
                    .header("X-VOT-Headers", encodedHeaders)
            }
        }

        val requestBody = body.toRequestBody(contentType.toMediaType())
        when (method.uppercase()) {
            "PUT" -> reqBuilder.put(requestBody)
            "POST" -> reqBuilder.post(requestBody)
            "GET" -> reqBuilder.get()
        }

        return okHttpClient.newCall(reqBuilder.build()).execute()
    }

    private suspend fun getOrCreateSession(): SessionData = withContext(Dispatchers.IO) {
        val current = activeSession
        val now = System.currentTimeMillis()
        if (current != null && current.expiresAtMs > now + 60_000) {
            return@withContext current
        }

        var lastException: Exception? = null
        for (i in hosts.indices) {
            val host = currentHost
            try {
                val uuid = generateUUID()
                val sessionBody = VotProtobuf.encodeSessionRequest(uuid, "video-translation")
                val bodySign = signHmacSha256(sessionBody)

                val reqBuilder = Request.Builder()
                    .url("https://$host/session/create")

                if (host == DIRECT_HOST) {
                    reqBuilder
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/x-protobuf")
                        .header("Accept", "application/x-protobuf")
                        .header("Vtrans-Signature", bodySign)
                        .header("sec-ch-ua", SEC_CH_UA)
                        .header("sec-ch-ua-full-version-list", SEC_CH_UA_FULL_VERSION_LIST)
                        .header("Sec-Fetch-Mode", "no-cors")
                } else {
                    val innerHeaders = buildJsonHeaders(
                        "Accept" to "application/x-protobuf",
                        "Content-Type" to "application/x-protobuf",
                        "User-Agent" to USER_AGENT,
                        "Vtrans-Signature" to bodySign
                    )
                    val encodedHeaders = encodeBase64(innerHeaders.toByteArray(Charsets.UTF_8))
                    reqBuilder
                        .header("User-Agent", "vot.js/3.1.2")
                        .header("Content-Type", "application/x-protobuf")
                        .header("X-VOT-Headers", encodedHeaders)
                }

                reqBuilder.post(sessionBody.toRequestBody("application/x-protobuf".toMediaType()))
                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    rotateHost()
                    continue
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
                return@withContext session
            } catch (e: Exception) {
                lastException = e
                rotateHost()
            }
        }
        throw lastException ?: Exception("Failed to create VOT session on all hosts")
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
            resetToPrimaryHost()
            val (normalizedUrl, videoId) = normalizeVideoUrl(videoUrl)
            var firstRequest = true
            var attempts = 0
            val maxAttempts = 35
            var audioFallbackSent = false
            var currentTranslationId = ""

            while (attempts < maxAttempts) {
                attempts++
                val session = getOrCreateSession()
                val path = "/video-translation/translate"

                val requestBytes = VotProtobuf.encodeTranslationRequest(
                    url = normalizedUrl,
                    duration = if (durationSeconds <= 0.0) 300.0 else durationSeconds,
                    responseLang = targetLang.code,
                    requestLang = if (targetLang == TargetLanguage.RUSSIAN) "en" else "ru",
                    firstRequest = firstRequest,
                    useLivelyVoice = (voiceType == VoiceType.LIVE_VOICE),
                    translationId = currentTranslationId
                )

                val response = executeYaRequest(
                    path = path,
                    body = requestBytes,
                    secType = "Vtrans",
                    session = session
                )

                if (!response.isSuccessful) {
                    val code = response.code
                    rotateHost()
                    if (code == 429) {
                        delay(minOf(attempts * 2500L, 8000L))
                    } else if (code in 500..599) {
                        delay(minOf(attempts * 1500L, 5000L))
                    } else {
                        activeSession = null
                        delay(minOf(attempts * 1200L, 4000L))
                    }
                    if (attempts < maxAttempts) continue
                    return@withContext Result.failure(Exception("Translation request failed: HTTP $code"))
                }

                val responseBytes = response.body?.bytes()
                    ?: return@withContext Result.failure(Exception("Empty translation response"))

                val result = VotProtobuf.decodeTranslationResponse(responseBytes)

                if (result.translationId.isNotEmpty()) {
                    currentTranslationId = result.translationId
                }

                if (result.isSuccess) {
                    onProgress?.invoke("Translation ready!")
                    return@withContext Result.success(result)
                } else if (result.isAudioRequested) {
                    firstRequest = false
                    if (videoId != null && !audioFallbackSent) {
                        audioFallbackSent = true
                        onProgress?.invoke("Queueing audio with Yandex neural translator...")
                        try {
                            // 1. Send fail-audio-js PUT (without Vtrans signature headers)
                            val failJson = JSONObject().apply {
                                put("video_url", normalizedUrl)
                            }.toString().toByteArray(Charsets.UTF_8)
                            executeYaRequest(
                                path = "/video-translation/fail-audio-js",
                                body = failJson,
                                session = session,
                                method = "PUT",
                                contentType = "application/json",
                                acceptType = "application/json",
                                withSecHeaders = false
                            )

                            // 2. Send fallback audio PUT
                            val fileId = "fallback-empty-audio:video-translation:$videoId"
                            val audioReqBytes = VotProtobuf.encodeTranslationAudioRequest(
                                url = normalizedUrl,
                                translationId = result.translationId.ifEmpty { currentTranslationId },
                                fileId = fileId
                            )
                            executeYaRequest(
                                path = "/video-translation/audio",
                                body = audioReqBytes,
                                secType = "Vtrans",
                                session = session,
                                method = "PUT"
                            )
                        } catch (_: Exception) {}
                        delay(2000L)
                    } else {
                        val waitSec = if (result.remainingTime > 0) result.remainingTime.coerceIn(3, 6) else 3
                        val remainingInfo = if (result.remainingTime > 0) " (~${result.remainingTime}s remaining)..." else "..."
                        onProgress?.invoke("Generating voice-over translation$remainingInfo")
                        delay(waitSec * 1000L)
                    }
                } else if (result.isWaiting) {
                    firstRequest = false
                    val waitSec = if (result.remainingTime > 0) result.remainingTime.coerceIn(3, 6) else 3
                    val remainingInfo = if (result.remainingTime > 0) " (~${result.remainingTime}s remaining)..." else "..."
                    onProgress?.invoke(
                        if (voiceType == VoiceType.LIVE_VOICE) 
                            "Generating Live Voice$remainingInfo"
                        else 
                            "Generating voice-over translation$remainingInfo"
                    )
                    delay(waitSec * 1000L)
                } else if (result.isFailed) {
                    if (voiceType == VoiceType.LIVE_VOICE || preferredVoice.isNotEmpty()) {
                        onProgress?.invoke("Using standard voice-over...")
                        return@withContext translateVideo(
                            videoUrl = videoUrl,
                            durationSeconds = durationSeconds,
                            targetLang = targetLang,
                            voiceType = VoiceType.STANDARD,
                            preferredVoice = "",
                            onProgress = onProgress
                        )
                    }
                    return@withContext Result.failure(Exception(result.message ?: "Yandex translation failed for this video."))
                } else {
                    firstRequest = false
                    delay(2000L)
                }
            }

            Result.failure(Exception("Translation timed out after waiting."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMultiSubtitles(
        videoUrl: String
    ): Result<MultiSubtitles> = withContext(Dispatchers.IO) {
        resetToPrimaryHost()
        val (normalizedUrl, _) = normalizeVideoUrl(videoUrl)
        var ruCues: List<SubtitleCue> = emptyList()
        var roCues: List<SubtitleCue> = emptyList()
        var enCues: List<SubtitleCue> = emptyList()

        // 1. Try Yandex subtitles API with Vsubs headers
        try {
            val session = getOrCreateSession()
            val path = "/video-subtitles/get-subtitles"
            val requestBytes = VotProtobuf.encodeSubtitlesRequest(
                url = normalizedUrl,
                language = "en"
            )

            val response = executeYaRequest(
                path = path,
                body = requestBytes,
                secType = "Vsubs",
                session = session
            )

            if (response.isSuccessful) {
                val responseBytes = response.body?.bytes()
                if (responseBytes != null) {
                    val subtitleEntries = VotProtobuf.decodeSubtitlesResponse(responseBytes)
                    val ruEntry = subtitleEntries.firstOrNull { it.translatedLanguage == "ru" && it.translatedUrl.isNotBlank() }
                        ?: subtitleEntries.firstOrNull { it.language == "ru" && it.url.isNotBlank() }
                    val roEntry = subtitleEntries.firstOrNull { it.translatedLanguage == "ro" && it.translatedUrl.isNotBlank() }
                        ?: subtitleEntries.firstOrNull { it.language == "ro" && it.url.isNotBlank() }
                    val enEntry = subtitleEntries.firstOrNull { it.language == "en" && it.url.isNotBlank() }
                        ?: subtitleEntries.firstOrNull { it.url.isNotBlank() && it != ruEntry && it != roEntry }

                    if (ruEntry != null) {
                        val subUrl = ruEntry.translatedUrl.ifBlank { ruEntry.url }
                        if (subUrl.isNotBlank()) {
                            val subResp = okHttpClient.newCall(Request.Builder().url(subUrl).get().build()).execute()
                            if (subResp.isSuccessful) {
                                ruCues = parseSubtitles(subResp.body?.string().orEmpty())
                            }
                        }
                    }
                    if (roEntry != null) {
                        val subUrl = roEntry.translatedUrl.ifBlank { roEntry.url }
                        if (subUrl.isNotBlank()) {
                            val subResp = okHttpClient.newCall(Request.Builder().url(subUrl).get().build()).execute()
                            if (subResp.isSuccessful) {
                                roCues = parseSubtitles(subResp.body?.string().orEmpty())
                            }
                        }
                    }
                    if (enEntry != null) {
                        val subUrl = enEntry.url.ifBlank { enEntry.translatedUrl }
                        if (subUrl.isNotBlank()) {
                            val subResp = okHttpClient.newCall(Request.Builder().url(subUrl).get().build()).execute()
                            if (subResp.isSuccessful) {
                                enCues = parseSubtitles(subResp.body?.string().orEmpty())
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback: YouTube direct caption tracks (WITHOUT &tlang to prevent HTTP 429 rate limit)
        if (ruCues.isEmpty() || roCues.isEmpty() || enCues.isEmpty()) {
            try {
                val directTracks = fetchYouTubeDirectCaptionTracks(videoUrl)
                if (ruCues.isEmpty() && directTracks.containsKey("ru")) {
                    ruCues = directTracks["ru"] ?: emptyList()
                }
                if (roCues.isEmpty() && directTracks.containsKey("ro")) {
                    roCues = directTracks["ro"] ?: emptyList()
                }
                if (enCues.isEmpty() && directTracks.containsKey("en")) {
                    enCues = directTracks["en"] ?: emptyList()
                }
                if (enCues.isEmpty() && directTracks.containsKey("base")) {
                    enCues = directTracks["base"] ?: emptyList()
                }
            } catch (_: Exception) {}
        }

        // 3. Guaranteed availability: Translate missing tracks using parallel GTX batch translator
        val baseCues = when {
            enCues.isNotEmpty() -> enCues
            ruCues.isNotEmpty() -> ruCues
            roCues.isNotEmpty() -> roCues
            else -> emptyList()
        }

        if (baseCues.isNotEmpty()) {
            val needRu = ruCues.isEmpty()
            val needRo = roCues.isEmpty()
            val needEn = enCues.isEmpty()

            val ruDeferred = if (needRu) async { translateCues(baseCues, "ru") } else null
            val roDeferred = if (needRo) async { translateCues(baseCues, "ro") } else null
            val enDeferred = if (needEn) async { translateCues(baseCues, "en") } else null

            if (ruDeferred != null) ruCues = ruDeferred.await()
            if (roDeferred != null) roCues = roDeferred.await()
            if (enDeferred != null) enCues = enDeferred.await()
        }

        Result.success(MultiSubtitles(russianCues = ruCues, romanianCues = roCues, englishCues = enCues))
    }

    suspend fun getDualSubtitles(
        videoUrl: String
    ): Result<DualSubtitles> = withContext(Dispatchers.IO) {
        val multi = getMultiSubtitles(videoUrl).getOrDefault(MultiSubtitles())
        Result.success(DualSubtitles(russianCues = multi.russianCues, englishCues = multi.englishCues))
    }

    suspend fun getSubtitles(
        videoUrl: String,
        targetLang: TargetLanguage = TargetLanguage.RUSSIAN
    ): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        val multi = getMultiSubtitles(videoUrl).getOrDefault(MultiSubtitles())
        val cues = when (targetLang) {
            TargetLanguage.RUSSIAN -> multi.russianCues
            TargetLanguage.ROMANIAN -> multi.romanianCues
            TargetLanguage.ENGLISH -> multi.englishCues
            else -> multi.russianCues.ifEmpty { multi.romanianCues }.ifEmpty { multi.englishCues }
        }
        Result.success(cues)
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/)([a-zA-Z0-9_-]{11})"),
            Pattern.compile("^([a-zA-Z0-9_-]{11})$")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private suspend fun fetchYouTubeDirectCaptionTracks(
        videoUrl: String
    ): Map<String, List<SubtitleCue>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, List<SubtitleCue>>()
        val videoId = extractVideoId(videoUrl) ?: return@withContext results

        var captionTracksArray: JSONArray? = null

        // 1. Try YouTube InnerTube ANDROID_VR client (fast, reliable signed captionTracks)
        try {
            val requestJson = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.60.19")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("osName", "Android")
                        put("osVersion", "12")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                captionTracksArray = json.optJSONObject("captions")
                    ?.optJSONObject("playerCaptionsTracklistRenderer")
                    ?.optJSONArray("captionTracks")
            }
        } catch (_: Exception) {}

        val youtubeCookie = try {
            CookieManager.getInstance().getCookie("https://www.youtube.com")
        } catch (_: Exception) {
            null
        }

        // 2. Fallback: Parse watch page HTML for captionTracks
        if (captionTracksArray == null || captionTracksArray.length() == 0) {
            try {
                val pageReqBuilder = Request.Builder()
                    .url("https://www.youtube.com/watch?v=$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                if (!youtubeCookie.isNullOrEmpty()) {
                    pageReqBuilder.header("Cookie", youtubeCookie)
                }
                val pageResp = okHttpClient.newCall(pageReqBuilder.build()).execute()
                val html = pageResp.body?.string().orEmpty()
                val directPattern = Pattern.compile("\"captionTracks\":\\s*(\\[.+?\\])")
                val directMatcher = directPattern.matcher(html)
                if (directMatcher.find()) {
                    val tracksJsonStr = directMatcher.group(1) ?: "[]"
                    captionTracksArray = JSONArray(tracksJsonStr)
                }
            } catch (_: Exception) {}
        }

        if (captionTracksArray == null || captionTracksArray.length() == 0) return@withContext results

        var ruUrl = ""
        var roUrl = ""
        var enUrl = ""
        var firstUrl = ""

        for (i in 0 until captionTracksArray.length()) {
            val track = captionTracksArray.getJSONObject(i)
            val lang = track.optString("languageCode", "")
            val vss = track.optString("vssId", "")
            val bUrl = track.optString("baseUrl", "")
            if (bUrl.isBlank()) continue

            if (firstUrl.isEmpty()) firstUrl = bUrl
            if (lang.equals("ru", ignoreCase = true) || vss.contains(".ru")) {
                if (ruUrl.isEmpty()) ruUrl = bUrl
            }
            if (lang.equals("ro", ignoreCase = true) || vss.contains(".ro")) {
                if (roUrl.isEmpty()) roUrl = bUrl
            }
            if (lang.equals("en", ignoreCase = true) || vss.contains(".en")) {
                if (enUrl.isEmpty()) enUrl = bUrl
            }
        }

        fun downloadAndParse(rawUrl: String): List<SubtitleCue> {
            if (rawUrl.isBlank()) return emptyList()
            // 1. Try direct raw url (preserves valid signature and XML/srv format)
            try {
                val reqBuilder = Request.Builder()
                    .url(rawUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.youtube.com/")
                if (!youtubeCookie.isNullOrEmpty()) {
                    reqBuilder.header("Cookie", youtubeCookie)
                }
                val resp = okHttpClient.newCall(reqBuilder.build()).execute()
                if (resp.isSuccessful) {
                    val text = resp.body?.string().orEmpty()
                    val cues = parseSubtitles(text)
                    if (cues.isNotEmpty()) return cues
                }
            } catch (_: Exception) {}

            // 2. Try with &fmt=json3 if not already specified
            if (!rawUrl.contains("fmt=")) {
                try {
                    val json3Url = if (rawUrl.contains("?")) "$rawUrl&fmt=json3" else "$rawUrl?fmt=json3"
                    val reqBuilder = Request.Builder()
                        .url(json3Url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://www.youtube.com/")
                    if (!youtubeCookie.isNullOrEmpty()) {
                        reqBuilder.header("Cookie", youtubeCookie)
                    }
                    val resp = okHttpClient.newCall(reqBuilder.build()).execute()
                    if (resp.isSuccessful) {
                        val text = resp.body?.string().orEmpty()
                        val cues = parseSubtitles(text)
                        if (cues.isNotEmpty()) return cues
                    }
                } catch (_: Exception) {}
            }

            return emptyList()
        }

        if (ruUrl.isNotEmpty()) {
            val cues = downloadAndParse(ruUrl)
            if (cues.isNotEmpty()) results["ru"] = cues
        }
        if (roUrl.isNotEmpty()) {
            val cues = downloadAndParse(roUrl)
            if (cues.isNotEmpty()) results["ro"] = cues
        }
        if (enUrl.isNotEmpty()) {
            val cues = downloadAndParse(enUrl)
            if (cues.isNotEmpty()) results["en"] = cues
        }
        if (!results.containsKey("en") && firstUrl.isNotEmpty() && firstUrl != ruUrl && firstUrl != roUrl) {
            val cues = downloadAndParse(firstUrl)
            if (cues.isNotEmpty()) results["base"] = cues
        }

        results
    }

    suspend fun translateCues(
        cues: List<SubtitleCue>,
        targetLangCode: String = "ru"
    ): List<SubtitleCue> = coroutineScope {
        if (cues.isEmpty()) return@coroutineScope emptyList()
        val batchSize = 35
        val batches = cues.chunked(batchSize)

        val deferreds = batches.map { batch ->
            async(Dispatchers.IO) {
                val combinedText = batch.joinToString("\n") { it.text.replace("\n", " ").trim() }
                try {
                    val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLangCode&dt=t"
                    val formBody = FormBody.Builder()
                        .add("q", combinedText)
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .post(formBody)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                        .build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string().orEmpty()
                        val jsonArray = JSONArray(bodyStr)
                        val transArray = jsonArray.optJSONArray(0)
                        val sb = StringBuilder()
                        if (transArray != null) {
                            for (k in 0 until transArray.length()) {
                                val part = transArray.optJSONArray(k)
                                val piece = part?.optString(0, "") ?: ""
                                sb.append(piece)
                            }
                        }
                        val transLines = sb.toString().trim().lines()
                        return@async batch.mapIndexed { j, cue ->
                            val transLine = if (j < transLines.size && transLines[j].isNotBlank()) transLines[j].trim() else cue.text
                            SubtitleCue(cue.startTimeMs, cue.endTimeMs, transLine)
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to original cues if batch translation fails
                }
                batch
            }
        }
        deferreds.awaitAll().flatten()
    }

    fun parseSubtitles(raw: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val trimmed = raw.trim()
        try {
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                // Format A: Yandex JSON { "subtitles": [ ... ] }
                if (json.has("subtitles")) {
                    val arr = json.getJSONArray("subtitles")
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val startMs = when {
                            item.has("startMs") -> item.optDouble("startMs", 0.0).toLong()
                            item.has("start") -> (item.optDouble("start", 0.0) * 1000).toLong()
                            else -> 0L
                        }
                        val endMs = when {
                            item.has("endMs") -> item.optDouble("endMs", 0.0).toLong()
                            item.has("end") -> (item.optDouble("end", 0.0) * 1000).toLong()
                            item.has("durationMs") -> startMs + item.optDouble("durationMs", 0.0).toLong()
                            item.has("duration") -> startMs + (item.optDouble("duration", 0.0) * 1000).toLong()
                            else -> startMs + 3000L
                        }
                        val text = item.optString("text", "")
                        if (text.isNotBlank()) {
                            cues.add(SubtitleCue(startMs, endMs, text.trim()))
                        }
                    }
                }
                // Format B: YouTube json3 { "events": [ { "tStartMs": ..., "dDurationMs": ..., "segs": [...] } ] }
                else if (json.has("events")) {
                    val events = json.getJSONArray("events")
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i)
                        val startMs = event.optLong("tStartMs", 0L)
                        val durMs = event.optLong("dDurationMs", 0L)
                        val endMs = startMs + durMs
                        if (event.has("segs")) {
                            val segs = event.getJSONArray("segs")
                            val textBuilder = StringBuilder()
                            for (s in 0 until segs.length()) {
                                textBuilder.append(segs.getJSONObject(s).optString("utf8", ""))
                            }
                            val text = textBuilder.toString().replace("\n", " ").trim()
                            if (text.isNotBlank()) {
                                cues.add(SubtitleCue(startMs, endMs, text))
                            }
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                // Format C: JSON Array [ { "start": 1.2, "end": 3.4, "text": "..." } ]
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val startMs = when {
                        item.has("startMs") -> item.optLong("startMs", 0L)
                        item.has("start") -> (item.optDouble("start", 0.0) * 1000).toLong()
                        else -> 0L
                    }
                    val endMs = when {
                        item.has("endMs") -> item.optLong("endMs", 0L)
                        item.has("end") -> (item.optDouble("end", 0.0) * 1000).toLong()
                        item.has("durationMs") -> startMs + item.optLong("durationMs", 0L)
                        item.has("duration") -> startMs + (item.optDouble("duration", 0.0) * 1000).toLong()
                        else -> startMs + 3000L
                    }
                    val text = item.optString("text", "")
                    if (text.isNotBlank()) {
                        cues.add(SubtitleCue(startMs, endMs, text.trim()))
                    }
                }
            } else if (trimmed.contains("<p t=") || trimmed.contains("<text start=")) {
                // Format D: YouTube XML timedtext format
                val pPattern = Pattern.compile("<(?:p|text)\\s+[^>]*(?:t|start)=\"([0-9.]+)\"[^>]*?(?:(?:d|dur)=\"([0-9.]+)\")?[^>]*>(.*?)</(?:p|text)>", Pattern.DOTALL)
                val matcher = pPattern.matcher(trimmed)
                while (matcher.find()) {
                    val tRaw = matcher.group(1) ?: "0"
                    val dRaw = matcher.group(2)
                    val content = matcher.group(3) ?: ""
                    val startMs = if (tRaw.contains(".")) (tRaw.toDouble() * 1000).toLong() else (tRaw.toLongOrNull() ?: 0L)
                    val durMs = if (dRaw != null) {
                        if (dRaw.contains(".")) (dRaw.toDouble() * 1000).toLong() else (dRaw.toLongOrNull() ?: 3000L)
                    } else 3000L
                    val cleanText = content
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&apos;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#([0-9]+);".toRegex()) { mr ->
                            mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
                        }
                        .replace("<[^>]+>".toRegex(), "")
                        .trim()
                    if (cleanText.isNotBlank()) {
                        cues.add(SubtitleCue(startMs, startMs + durMs, cleanText))
                    }
                }
            } else if (trimmed.contains("-->")) {
                // Format E: VTT / SRT format
                val lines = trimmed.lines()
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
                        val text = textLines.joinToString(" ")
                            .replace("<[^>]+>".toRegex(), "")
                            .trim()
                        if (text.isNotBlank()) {
                            cues.add(SubtitleCue(startMs, endMs, text))
                        }
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

