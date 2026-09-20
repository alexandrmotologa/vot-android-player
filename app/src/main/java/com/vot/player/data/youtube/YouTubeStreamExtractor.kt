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
        private const val IOS_CLIENT_VERSION = "20.10.4"
        private const val IOS_DEVICE_MODEL = "iPhone16,2"
        private const val IOS_USER_AGENT = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

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

            // 1. Request player metadata using verified iOS client
            val requestJson = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", IOS_CLIENT_VERSION)
                        put("deviceModel", IOS_DEVICE_MODEL)
                        put("osName", "iOS")
                        put("osVersion", "18.3.2.22D82")
                        put("hl", "en")
                        put("gl", "US")
                        put("userAgent", IOS_USER_AGENT)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", IOS_USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty YouTube API response"))

            val json = JSONObject(responseBody)
            val playability = json.optJSONObject("playabilityStatus")
            val playStatus = playability?.optString("status")
            if (playStatus != null && playStatus != "OK") {
                val reason = playability.optString("reason", "Video unavailable ($playStatus)")
                return@withContext Result.failure(Exception(reason))
            }

            val videoDetails = json.optJSONObject("videoDetails")
                ?: return@withContext Result.failure(Exception("Video details not found"))

            val streamingData = json.optJSONObject("streamingData")
                ?: return@withContext Result.failure(Exception("Streaming formats not found. Video may be restricted."))

            val title = videoDetails.optString("title", "YouTube Video")
            val author = videoDetails.optString("author", "Unknown Channel")
            val durationSeconds = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
            val thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val hlsManifestUrl = streamingData.optString("hlsManifestUrl").takeIf { it.isNotEmpty() }

            val qualityList = mutableListOf<VideoQuality>()
            var bestProgressiveUrl: String? = null
            var bestAudioUrl: String? = null

            // 2. Direct progressive formats (video + audio combined in standard MP4)
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

            // 3. Adaptive formats (separate video-only and audio-only streams)
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                // Find best audio stream: prefer medium-quality mp4a (128kbps AAC)
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.getJSONObject(i)
                    val mimeType = fmt.optString("mimeType")
                    val url = fmt.optString("url")
                    val audioQuality = fmt.optString("audioQuality")
                    if (url.isNotEmpty() && mimeType.startsWith("audio/mp4") && audioQuality.contains("MEDIUM")) {
                        bestAudioUrl = url
                        break
                    }
                }
                if (bestAudioUrl == null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.getJSONObject(i)
                        val mimeType = fmt.optString("mimeType")
                        val url = fmt.optString("url")
                        if (url.isNotEmpty() && mimeType.startsWith("audio/mp4")) {
                            bestAudioUrl = url
                            break
                        }
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

                // Add adaptive video streams (prefer avc1 / H.264 mp4 for universal hardware decoding)
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.getJSONObject(i)
                    val mimeType = fmt.optString("mimeType")
                    val url = fmt.optString("url")
                    val height = fmt.optInt("height", 0)
                    val qualityLabel = fmt.optString("qualityLabel", "${height}p")

                    if (url.isNotEmpty() && mimeType.startsWith("video/") && height > 0) {
                        val existingIndex = qualityList.indexOfFirst { it.height == height }
                        val isAvc = mimeType.contains("avc1")
                        if (existingIndex == -1) {
                            qualityList.add(VideoQuality(qualityLabel, height, url, bestAudioUrl))
                        } else if (isAvc) {
                            qualityList[existingIndex] = VideoQuality(qualityLabel, height, url, bestAudioUrl)
                        }
                    }
                }
            }

            // Sort standard qualities descending (1080p, 720p, 480p, 360p...)
            qualityList.sortByDescending { it.height }

            // Offer Auto (HLS) if master variant manifest is available
            if (!hlsManifestUrl.isNullOrEmpty()) {
                qualityList.add(0, VideoQuality("Auto (HLS)", 9999, hlsManifestUrl, null))
            }

            val primaryStreamUrl = qualityList.firstOrNull()?.videoUrl
                ?: hlsManifestUrl
                ?: bestProgressiveUrl

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
