package com.vot.player.data.extractor

import com.vot.player.data.model.PlatformType
import com.vot.player.data.model.UniversalVideoInfo
import com.vot.player.data.model.VideoQuality
import com.vot.player.data.youtube.YouTubeStreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MultiPlatformExtractor(
    private val youtubeExtractor: YouTubeStreamExtractor = YouTubeStreamExtractor(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private val TWITCH_CLIP_REGEX = Pattern.compile("https?:\\/\\/(?:clips\\.twitch\\.tv\\/|(?:www\\.)?twitch\\.tv\\/[^\\/]+\\/clip\\/)([a-zA-Z0-9_-]+)")
        private val TWITCH_VOD_REGEX = Pattern.compile("https?:\\/\\/(?:www\\.)?twitch\\.tv\\/videos\\/([0-9]+)")
        private val TIKTOK_REGEX = Pattern.compile("https?:\\/\\/(?:www\\.|vm\\.|vt\\.)?tiktok\\.com\\/.*")
        private val TWITTER_REGEX = Pattern.compile("https?:\\/\\/(?:twitter\\.com|x\\.com)\\/[^\\/]+\\/status\\/([0-9]+)")
    }

    suspend fun extract(rawUrl: String): Result<UniversalVideoInfo> = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()

        // 1. YouTube
        if (YouTubeStreamExtractor.extractVideoId(trimmed) != null) {
            val ytResult = youtubeExtractor.extractStreamInfo(trimmed)
            return@withContext ytResult.map { yt ->
                UniversalVideoInfo(
                    id = yt.videoId,
                    rawUrl = trimmed,
                    title = yt.title,
                    author = yt.author,
                    durationSeconds = yt.durationSeconds,
                    streamUrl = yt.streamUrl,
                    directAudioUrl = yt.directAudioUrl,
                    thumbnailUrl = yt.thumbnailUrl,
                    platform = PlatformType.YOUTUBE,
                    availableQualities = yt.availableQualities
                )
            }
        }

        // 2. Direct Video Files (.mp4, .m3u8, .webm)
        if (trimmed.endsWith(".mp4", ignoreCase = true) || 
            trimmed.endsWith(".m3u8", ignoreCase = true) || 
            trimmed.endsWith(".webm", ignoreCase = true)) {
            val filename = trimmed.substringAfterLast("/").substringBefore("?")
            return@withContext Result.success(
                UniversalVideoInfo(
                    id = "direct_${filename.hashCode()}",
                    rawUrl = trimmed,
                    title = filename.ifEmpty { "Direct Video Stream" },
                    author = "Direct Media",
                    durationSeconds = 0L,
                    streamUrl = trimmed,
                    thumbnailUrl = null,
                    platform = PlatformType.DIRECT
                )
            )
        }

        // 3. Twitch (VODs & Clips)
        if (trimmed.contains("twitch.tv")) {
            val vodMatcher = TWITCH_VOD_REGEX.matcher(trimmed)
            val clipMatcher = TWITCH_CLIP_REGEX.matcher(trimmed)
            val twitchId = when {
                vodMatcher.find() -> vodMatcher.group(1)
                clipMatcher.find() -> clipMatcher.group(1)
                else -> "twitch_${System.currentTimeMillis()}"
            }
            return@withContext Result.success(
                UniversalVideoInfo(
                    id = twitchId,
                    rawUrl = trimmed,
                    title = "Twitch Video ($twitchId)",
                    author = "Twitch Streamer",
                    durationSeconds = 0L,
                    streamUrl = trimmed,
                    platform = PlatformType.TWITCH
                )
            )
        }

        // 4. TikTok
        if (TIKTOK_REGEX.matcher(trimmed).matches()) {
            return@withContext try {
                val oembedUrl = "https://www.tiktok.com/oembed?url=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
                val req = Request.Builder().url(oembedUrl).build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                val json = if (body.isNotEmpty()) JSONObject(body) else JSONObject()
                val title = json.optString("title", "TikTok Video")
                val author = json.optString("author_name", "TikTok Creator")
                val thumb = json.optString("thumbnail_url").takeIf { it.isNotEmpty() }

                Result.success(
                    UniversalVideoInfo(
                        id = "tiktok_${trimmed.hashCode()}",
                        rawUrl = trimmed,
                        title = title,
                        author = author,
                        durationSeconds = 0L,
                        streamUrl = trimmed,
                        thumbnailUrl = thumb,
                        platform = PlatformType.TIKTOK
                    )
                )
            } catch (_: Exception) {
                Result.success(
                    UniversalVideoInfo(
                        id = "tiktok_${trimmed.hashCode()}",
                        rawUrl = trimmed,
                        title = "TikTok Video",
                        author = "TikTok Creator",
                        durationSeconds = 0L,
                        streamUrl = trimmed,
                        platform = PlatformType.TIKTOK
                    )
                )
            }
        }

        // 5. Twitter / X
        val twitterMatcher = TWITTER_REGEX.matcher(trimmed)
        if (twitterMatcher.find()) {
            val tweetId = twitterMatcher.group(1)
            return@withContext Result.success(
                UniversalVideoInfo(
                    id = "twitter_$tweetId",
                    rawUrl = trimmed,
                    title = "X Video ($tweetId)",
                    author = "X Post",
                    durationSeconds = 0L,
                    streamUrl = trimmed,
                    platform = PlatformType.TWITTER
                )
            )
        }

        // Fallback: Default directly as playable media URL
        Result.success(
            UniversalVideoInfo(
                id = "media_${trimmed.hashCode()}",
                rawUrl = trimmed,
                title = "Video Stream",
                author = "Web",
                durationSeconds = 0L,
                streamUrl = trimmed,
                platform = PlatformType.DIRECT
            )
        )
    }
}
