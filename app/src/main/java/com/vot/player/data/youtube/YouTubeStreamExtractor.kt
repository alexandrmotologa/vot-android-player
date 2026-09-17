package com.vot.player.data.youtube

import com.vot.player.data.model.YouTubeVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeStreamExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private val VIDEO_ID_REGEX = Pattern.compile(
            "^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*",
            Pattern.CASE_INSENSITIVE
        )

        fun extractVideoId(url: String): String? {
            val matcher = VIDEO_ID_REGEX.matcher(url)
            return if (matcher.matches()) {
                val id = matcher.group(1)
                if (id.length == 11) id else null
            } else if (url.length == 11 && !url.contains("/")) {
                url
            } else {
                null
            }
        }
    }

    suspend fun extractStreamInfo(rawUrl: String): Result<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(rawUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL: $rawUrl"))

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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty YouTube API response"))

            val json = JSONObject(responseBody)
            val videoDetails = json.optJSONObject("videoDetails")
                ?: return@withContext Result.failure(Exception("Video details not found"))

            val title = videoDetails.optString("title", "YouTube Video")
            val author = videoDetails.optString("author", "Unknown Channel")
            val durationSeconds = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L

            val streamingData = json.optJSONObject("streamingData")
                ?: return@withContext Result.failure(Exception("Streaming formats not found. Video may be restricted."))

            var bestStreamUrl: String? = null
            var directAudioUrl: String? = null

            // 1. Check direct progressive formats (video + audio combined)
            val formats = streamingData.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val url = fmt.optString("url")
                    if (url.isNotEmpty()) {
                        bestStreamUrl = url
                        break
                    }
                }
            }

            // 2. If no progressive format found, check adaptive formats
            if (bestStreamUrl == null) {
                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                    var bestVideoHeight = 0
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.getJSONObject(i)
                        val mimeType = fmt.optString("mimeType")
                        val url = fmt.optString("url")
                        val height = fmt.optInt("height", 0)

                        if (url.isNotEmpty()) {
                            if (mimeType.startsWith("video/") && height > bestVideoHeight && height <= 1080) {
                                bestVideoHeight = height
                                bestStreamUrl = url
                            } else if (mimeType.startsWith("audio/") && directAudioUrl == null) {
                                directAudioUrl = url
                            }
                        }
                    }
                }
            }

            if (bestStreamUrl == null) {
                // Fallback to HLS manifest if present
                val hlsManifest = streamingData.optString("hlsManifestUrl")
                if (hlsManifest.isNotEmpty()) {
                    bestStreamUrl = hlsManifest
                }
            }

            if (bestStreamUrl.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No playable video streams found for video $videoId"))
            }

            Result.success(
                YouTubeVideoInfo(
                    videoId = videoId,
                    title = title,
                    author = author,
                    durationSeconds = durationSeconds,
                    streamUrl = bestStreamUrl,
                    directAudioUrl = directAudioUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
