package com.vot.player.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vot.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String
)

class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        val CURRENT_VERSION: String get() = BuildConfig.VERSION_NAME
        private const val RELEASES_API_URL = "https://api.github.com/repos/alexandrmotologa/vot-android-player/releases/latest"
    }

    suspend fun checkForUpdates(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("User-Agent", "VotPlayer-Android")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.success(null)
            }

            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) return@withContext Result.success(null)

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements")

            // Find APK in assets
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            if (tagName.isNotEmpty() && isNewerVersion(tagName, CURRENT_VERSION) && !apkUrl.isNullOrEmpty()) {
                Result.success(
                    AppUpdateInfo(
                        versionName = tagName,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkUrl
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.removePrefix("v").trim().split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.removePrefix("v").trim().split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun downloadUpdate(context: Context, updateInfo: AppUpdateInfo) {
        try {
            Toast.makeText(
                context,
                "Opening update download in browser...",
                Toast.LENGTH_LONG
            ).show()

            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkDownloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/alexandrmotologa/vot-android-player/releases/latest")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
            }
        }
    }
}
