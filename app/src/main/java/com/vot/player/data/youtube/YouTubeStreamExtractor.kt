package com.vot.player.data.youtube

import com.vot.player.data.model.VideoQuality
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
                if (id?.length == 11) id else null
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

            val cookie = try {
                android.webkit.CookieManager.getInstance().getCookie("https://www.youtube.com")
            } catch (e: Exception) {
                null
            }

            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            if (!cookie.isNullOrEmpty()) {
                reqBuilder.header("Cookie", cookie)
            }
            val request = reqBuilder.build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty YouTube API response"))

            val json = JSONObject(responseBody)
            val videoDetails = json.optJSONObject("videoDetails")
                ?: return@withContext Result.failure(Exception("Video details not found"))

            var streamingData = json.optJSONObject("streamingData")
            var title = videoDetails.optString("title", "YouTube Video")
            var author = videoDetails.optString("author", "Unknown Channel")
            var durationSeconds = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
            val thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            // If ANDROID_VR didn't yield formats, retry with ANDROID client
            if (streamingData == null || (streamingData.optJSONArray("formats") == null && streamingData.optJSONArray("adaptiveFormats") == null)) {
                val fallbackRequestJson = JSONObject().apply {
                    put("videoId", videoId)
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "ANDROID")
                            put("clientVersion", "19.09.37")
                            put("osName", "Android")
                            put("osVersion", "14")
                            put("hl", "en")
                            put("gl", "US")
                        })
                    })
                }
                val fbReqBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player")
                    .post(fallbackRequestJson.toString().toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip")
                if (!cookie.isNullOrEmpty()) {
                    fbReqBuilder.header("Cookie", cookie)
                }
                val fbRequest = fbReqBuilder.build()
                val fbResponse = client.newCall(fbRequest).execute()
                val fbBody = fbResponse.body?.string()
                if (!fbBody.isNullOrEmpty()) {
                    val fbJson = JSONObject(fbBody)
                    val fbDetails = fbJson.optJSONObject("videoDetails")
                    if (fbDetails != null) {
                        title = fbDetails.optString("title", title)
                        author = fbDetails.optString("author", author)
                        durationSeconds = fbDetails.optString("lengthSeconds", durationSeconds.toString()).toLongOrNull() ?: durationSeconds
                    }
                    streamingData = fbJson.optJSONObject("streamingData")
                }
            }

            if (streamingData == null) {
                return@withContext Result.failure(Exception("Streaming formats not found. Video may be restricted."))
            }

            val qualityList = mutableListOf<VideoQuality>()
            var bestProgressiveUrl: String? = null
            var bestAudioUrl: String? = null

            // 1. Check direct progressive formats (video + audio combined)
            val formats = streamingData.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val url = fmt.optString("url")
                    val height = fmt.optInt("height", 0)
                    val qualityLabel = fmt.optString("qualityLabel", "${height}p")
                    if (url.isNotEmpty() && height > 0) {
                        qualityList.add(VideoQuality(qualityLabel, height, url, null))
                        if (bestProgressiveUrl == null || height >= 720) {
                            bestProgressiveUrl = url
                        }
                    }
                }
            }

            // 2. Parse adaptive formats (video-only and audio-only)
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                // Find best audio stream: prefer audio/mp4, fallback to any audio/
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.getJSONObject(i)
                    val mimeType = fmt.optString("mimeType")
                    val url = fmt.optString("url")
                    if (url.isNotEmpty() && mimeType.startsWith("audio/mp4")) {
                        bestAudioUrl = url
                        break
                    }
                }
                if (bestAudioUrl == null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.getJSONObject(i)
                        val mimeType = fmt.optString("mimeType")
                        val url = fmt.optString("url")
                        if (url.isNotEmpty() && mimeType.startsWith("audio/")) {
                            bestAudioUrl = url
                            break
                        }
                    }
                }

                // Add adaptive video streams for all resolutions (2160p down to 144p)
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.getJSONObject(i)
                    val mimeType = fmt.optString("mimeType")
                    val url = fmt.optString("url")
                    val height = fmt.optInt("height", 0)
                    val qualityLabel = fmt.optString("qualityLabel", "${height}p")

                    if (url.isNotEmpty() && mimeType.startsWith("video/") && height > 0) {
                        val existingIndex = qualityList.indexOfFirst { it.height == height }
                        if (existingIndex == -1) {
                            qualityList.add(VideoQuality(qualityLabel, height, url, bestAudioUrl))
                        } else if (mimeType.startsWith("video/mp4") && !qualityList[existingIndex].videoUrl.contains("mime=video%2Fmp4")) {
                            // Upgrade to MP4 stream if currently WebM for better hardware decoder compatibility
                            qualityList[existingIndex] = VideoQuality(qualityLabel, height, url, bestAudioUrl)
                        }
                    }
                }
            }

            // Sort quality options descending (e.g. 2160p, 1440p, 1080p, 720p, 480p, 360p)
            qualityList.sortByDescending { it.height }

            val primaryStreamUrl = qualityList.firstOrNull()?.videoUrl 
                ?: bestProgressiveUrl 
                ?: streamingData.optString("hlsManifestUrl")

            if (primaryStreamUrl.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No playable video streams found for video $videoId"))
            }

            Result.success(
                YouTubeVideoInfo(
                    videoId = videoId,
                    title = title,
                    author = author,
                    durationSeconds = durationSeconds,
                    streamUrl = primaryStreamUrl,
                    directAudioUrl = bestAudioUrl,
                    thumbnailUrl = thumbnailUrl,
                    availableQualities = qualityList
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
