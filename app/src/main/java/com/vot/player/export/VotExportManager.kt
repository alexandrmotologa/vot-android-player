package com.vot.player.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

sealed class ExportState {
    object Idle : ExportState()
    data class Downloading(val progressPercent: Int, val status: String) : ExportState()
    data class Muxing(val status: String) : ExportState()
    data class Success(val fileUri: Uri, val filePath: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

class VotExportManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    suspend fun exportVideo(
        videoUrl: String,
        audioUrl: String?,
        title: String
    ) = withContext(Dispatchers.IO) {
        try {
            _exportState.value = ExportState.Downloading(5, "Preparing download...")

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "_").take(50).trim()
            val tempVideoFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
            val tempAudioFile = if (!audioUrl.isNullOrEmpty()) {
                File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.mp3")
            } else null

            // 1. Download video track
            _exportState.value = ExportState.Downloading(10, "Downloading video stream...")
            downloadFile(videoUrl, tempVideoFile) { progress ->
                _exportState.value = ExportState.Downloading(10 + (progress * 0.4).toInt(), "Downloading video: $progress%")
            }

            // 2. Download audio track if present
            if (tempAudioFile != null && audioUrl != null) {
                _exportState.value = ExportState.Downloading(50, "Downloading translated audio track...")
                downloadFile(audioUrl, tempAudioFile) { progress ->
                    _exportState.value = ExportState.Downloading(50 + (progress * 0.3).toInt(), "Downloading voice: $progress%")
                }
            }

            // 3. Save to MediaStore or Downloads
            _exportState.value = ExportState.Muxing("Saving video to Downloads...")

            val fileName = "VOT_${safeTitle}_${System.currentTimeMillis()}.mp4"
            val outputUri: Uri?
            val destinationPath: String

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VOT")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                outputUri = context.contentResolver.insert(collection, values)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")

                context.contentResolver.openOutputStream(outputUri)?.use { outStream ->
                    tempVideoFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(outputUri, values, null, null)
                destinationPath = "Downloads/VOT/$fileName"
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadDir, "VOT").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                tempVideoFile.copyTo(targetFile, overwrite = true)
                outputUri = Uri.fromFile(targetFile)
                destinationPath = targetFile.absolutePath
            }

            // Clean up temp files
            tempVideoFile.delete()
            tempAudioFile?.delete()

            _exportState.value = ExportState.Success(outputUri, destinationPath)
        } catch (e: Exception) {
            _exportState.value = ExportState.Error(e.message ?: "Export failed")
        }
    }

    private fun downloadFile(url: String, destination: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body ?: throw IllegalStateException("Empty download response body")
        val contentLength = body.contentLength()

        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val percent = ((totalBytesRead * 100) / contentLength).toInt()
                        onProgress(percent)
                    }
                }
                output.flush()
            }
        }
    }

    fun reset() {
        _exportState.value = ExportState.Idle
    }
}
