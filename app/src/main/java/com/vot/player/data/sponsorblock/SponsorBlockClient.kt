package com.vot.player.data.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class SponsorSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val category: String
)

class SponsorBlockClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    suspend fun getSkipSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext emptyList()
        try {
            val categories = "%5B%22sponsor%22%2C%22intro%22%2C%22outro%22%2C%22selfpromo%22%2C%22interaction%22%5D"
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categories"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) return@withContext emptyList()

            val jsonArray = JSONArray(body)
            val segments = mutableListOf<SponsorSegment>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val category = item.optString("category", "sponsor")
                val segmentArray = item.optJSONArray("segment")
                if (segmentArray != null && segmentArray.length() >= 2) {
                    val startSec = segmentArray.getDouble(0)
                    val endSec = segmentArray.getDouble(1)
                    segments.add(
                        SponsorSegment(
                            startTimeMs = (startSec * 1000).toLong(),
                            endTimeMs = (endSec * 1000).toLong(),
                            category = category
                        )
                    )
                }
            }
            segments
        } catch (_: Exception) {
            emptyList()
        }
    }
}
