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

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val downloadedMb: Float, val totalMb: Float) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: java.io.File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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

    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (UpdateDownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(UpdateDownloadState.Downloading(0, 0f, 0f))
            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "VotPlayer-Android")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onProgress(UpdateDownloadState.Error("Server returned HTTP ${response.code}"))
                return@withContext
            }

            val body = response.body ?: run {
                onProgress(UpdateDownloadState.Error("Empty download response from server"))
                return@withContext
            }

            val totalBytes = body.contentLength()
            val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 20.99f

            val updatesDir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = java.io.File(updatesDir, "VOT-Player-v${updateInfo.versionName}.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var bytesCopied: Long = 0
            val buffer = ByteArray(16 * 1024)
            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    var bytes = input.read(buffer)
                    var lastReportTime = 0L
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 100L) {
                            lastReportTime = now
                            val progress = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 99) else 50
                            val downloadedMb = bytesCopied / (1024f * 1024f)
                            withContext(Dispatchers.Main) {
                                onProgress(UpdateDownloadState.Downloading(progress, downloadedMb, totalMb))
                            }
                        }
                        if (totalBytes > 0 && bytesCopied >= totalBytes) {
                            break
                        }
                        bytes = input.read(buffer)
                    }
                    output.flush()
                }
            }

            val finalMb = apkFile.length() / (1024f * 1024f)
            withContext(Dispatchers.Main) {
                onProgress(UpdateDownloadState.Downloading(100, finalMb, finalMb))
                onProgress(UpdateDownloadState.ReadyToInstall(apkFile))
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onProgress(UpdateDownloadState.Error(e.localizedMessage ?: "Download failed"))
            }
        }
    }

    fun installApk(context: Context, apkFile: java.io.File) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow VOT Player to install app updates", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resInfoList = context.packageManager.queryIntentActivities(installIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                context.grantUriPermission(resolveInfo.activityInfo.packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch package installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openReleaseInBrowser(context: Context, updateInfo: AppUpdateInfo) {
        try {
            val url = "https://github.com/alexandrmotologa/vot-android-player/releases/tag/v${updateInfo.versionName}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alexandrmotologa/vot-android-player/releases/latest")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun downloadUpdate(context: Context, updateInfo: AppUpdateInfo) {
        openReleaseInBrowser(context, updateInfo)
    }
}
