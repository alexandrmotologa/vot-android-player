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
    private val workerHosts: List<String> = listOf("vot-worker.vtrans.eu.cc", "vot-worker.eu.cc")
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

    private suspend fun getOrCreateSession(): SessionData = withContext(Dispatchers.IO) {
        val current = activeSession
        val now = System.currentTimeMillis()
        if (current != null && current.expiresAtMs > now + 60_000) {
            return@withContext current
        }

        var lastException: Exception? = null
        for (i in workerHosts.indices) {
            val host = workerHosts[currentHostIndex % workerHosts.size]
            try {
                val uuid = generateUUID()
                val sessionBody = VotProtobuf.encodeSessionRequest(uuid, "video-translation")
                val bodySign = signHmacSha256(sessionBody)

                val innerHeaders = buildJsonHeaders(
                    "Accept" to "application/x-protobuf",
                    "Content-Type" to "application/x-protobuf",
                    "User-Agent" to USER_AGENT,
                    "Vtrans-Signature" to bodySign
                )

                val encodedHeaders = encodeBase64(innerHeaders.toByteArray(Charsets.UTF_8))

                val request = Request.Builder()
                    .url("https://$host/session/create")
                    .post(sessionBody.toRequestBody("application/x-protobuf".toMediaType()))
                    .header("User-Agent", "vot.js/3.1.0")
                    .header("X-VOT-Headers", encodedHeaders)
                    .build()

                val response = okHttpClient.newCall(request).execute()
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
                    useLivelyVoice = (voiceType == VoiceType.LIVE_VOICE)
                )
                firstRequest = false

                val bodySign = signHmacSha256(requestBytes)

                val innerHeaders = buildJsonHeaders(
                    "Accept" to "application/x-protobuf",
                    "Content-Type" to "application/x-protobuf",
                    "User-Agent" to USER_AGENT,
                    "Sec-Vtrans-Token" to "$tokenSign:$token",
                    "Sec-Vtrans-Sk" to session.secretKey,
                    "Vtrans-Signature" to bodySign
                )

                val encodedHeaders = encodeBase64(innerHeaders.toByteArray(Charsets.UTF_8))

                val request = Request.Builder()
                    .url("https://$workerHost$path")
                    .post(requestBytes.toRequestBody("application/x-protobuf".toMediaType()))
                    .header("User-Agent", "vot.js/3.1.0")
                    .header("X-VOT-Headers", encodedHeaders)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    rotateHost()
                    activeSession = null
                    if (attempts < maxAttempts) continue
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
        var ruCues: List<SubtitleCue> = emptyList()
        var roCues: List<SubtitleCue> = emptyList()
        var enCues: List<SubtitleCue> = emptyList()

        // 1. Try Yandex subtitles API via VOT worker
        try {
            val session = getOrCreateSession()
            val path = "/video-subtitles/get-subtitles"
            val token = "${session.uuid}:$path:$COMPONENT_VERSION"
            val tokenSign = signHmacSha256(token.toByteArray(Charsets.UTF_8))

            val requestBytes = VotProtobuf.encodeSubtitlesRequest(
                url = videoUrl,
                language = "en"
            )
            val bodySign = signHmacSha256(requestBytes)

            val innerHeaders = buildJsonHeaders(
                "Accept" to "application/x-protobuf",
                "Content-Type" to "application/x-protobuf",
                "User-Agent" to USER_AGENT,
                "Sec-Vsubs-Token" to "$tokenSign:$token",
                "Sec-Vsubs-Sk" to session.secretKey,
                "Vsubs-Signature" to bodySign
            )

            val encodedHeaders = encodeBase64(innerHeaders.toByteArray(Charsets.UTF_8))

            val request = Request.Builder()
                .url("https://$workerHost$path")
                .post(requestBytes.toRequestBody("application/x-protobuf".toMediaType()))
                .header("User-Agent", "vot.js/3.1.0")
                .header("X-VOT-Headers", encodedHeaders)
                .build()

            val response = okHttpClient.newCall(request).execute()
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

        // 2. Fallback: Parse watch page HTML for ytInitialPlayerResponse
        if (captionTracksArray == null || captionTracksArray.length() == 0) {
            try {
                val pageReq = Request.Builder()
                    .url("https://www.youtube.com/watch?v=$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .build()
                val pageResp = okHttpClient.newCall(pageReq).execute()
                val html = pageResp.body?.string().orEmpty()
                val p = Pattern.compile("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});")
                val m = p.matcher(html)
                if (m.find()) {
                    val pJson = JSONObject(m.group(1) ?: "{}")
                    captionTracksArray = pJson.optJSONObject("captions")
                        ?.optJSONObject("playerCaptionsTracklistRenderer")
                        ?.optJSONArray("captionTracks")
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
            var url = rawUrl
            if (!url.contains("fmt=")) {
                url += "&fmt=json3"
            }
            return try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    parseSubtitles(resp.body?.string().orEmpty())
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
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

