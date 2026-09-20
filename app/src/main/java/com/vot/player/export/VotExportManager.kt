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
            if (videoUrl.contains("youtube.com/watch") || videoUrl.contains("m.youtube.com") || videoUrl.contains("youtu.be")) {
                _exportState.value = ExportState.Error("Direct video download is restricted for this YouTube stream. Please use 'Export Audio Only' to save the Russian voice-over MP3 track.")
                return@withContext
            }

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

            // 3. Mux video and translated audio track if present
            var tempMuxedFile: File? = null
            val finalFileToSave: File = if (tempAudioFile != null && tempAudioFile.exists()) {
                val muxCandidate = File(context.cacheDir, "temp_muxed_${System.currentTimeMillis()}.mp4")
                try {
                    _exportState.value = ExportState.Muxing("Muxing video with translated voice-over...")
                    muxVideoAndAudio(tempVideoFile, tempAudioFile, muxCandidate)
                    tempMuxedFile = muxCandidate
                    muxCandidate
                } catch (_: Exception) {
                    // Fallback to video file if muxing fails
                    tempVideoFile
                }
            } else {
                tempVideoFile
            }

            // 4. Save to MediaStore or Downloads
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
                    finalFileToSave.inputStream().use { inStream ->
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
                finalFileToSave.copyTo(targetFile, overwrite = true)
                outputUri = Uri.fromFile(targetFile)
                destinationPath = targetFile.absolutePath
            }

            // Clean up temp files
            tempVideoFile.delete()
            tempAudioFile?.delete()
            tempMuxedFile?.delete()

            _exportState.value = ExportState.Success(outputUri, destinationPath)
        } catch (e: Exception) {
            _exportState.value = ExportState.Error(e.message ?: "Export failed")
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var videoMuxerTrackIndex = -1
            var maxVideoBufferSize = 1024 * 1024

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoMuxerTrackIndex = muxer.addTrack(format)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxVideoBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(maxVideoBufferSize)
                    }
                    break
                }
            }

            var audioTrackIndex = -1
            var audioMuxerTrackIndex = -1
            var maxAudioBufferSize = 256 * 1024

            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioMuxerTrackIndex = muxer.addTrack(format)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxAudioBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(maxAudioBufferSize)
                    }
                    break
                }
            }

            if (videoTrackIndex == -1) {
                throw IllegalStateException("No video track found in source video")
            }

            muxer.start()

            // Write video frames
            videoExtractor.selectTrack(videoTrackIndex)
            val videoBuffer = ByteBuffer.allocate(maxVideoBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(videoMuxerTrackIndex, videoBuffer, bufferInfo)
                videoExtractor.advance()
            }

            // Write audio frames if available
            if (audioTrackIndex != -1 && audioMuxerTrackIndex != -1) {
                audioExtractor.selectTrack(audioTrackIndex)
                val audioBuffer = ByteBuffer.allocate(maxAudioBufferSize)

                while (true) {
                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags

                    muxer.writeSampleData(audioMuxerTrackIndex, audioBuffer, bufferInfo)
                    audioExtractor.advance()
                }
            }
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    suspend fun exportAudioOnly(
        audioUrl: String,
        title: String,
        author: String = "VOT"
    ) = withContext(Dispatchers.IO) {
        try {
            _exportState.value = ExportState.Downloading(10, "Preparing audio download...")
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "_").take(50).trim()
            val tempAudioFile = File(context.cacheDir, "temp_podcast_${System.currentTimeMillis()}.mp3")

            _exportState.value = ExportState.Downloading(20, "Downloading translated MP3...")
            downloadFile(audioUrl, tempAudioFile) { progress ->
                _exportState.value = ExportState.Downloading(20 + (progress * 0.7).toInt(), "Downloading audio: $progress%")
            }

            _exportState.value = ExportState.Muxing("Saving MP3 to Music/VOT...")
            val fileName = "VOT_${safeTitle}.mp3"
            val outputUri: Uri?
            val destinationPath: String

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, author)
                    put(MediaStore.Audio.Media.ALBUM, "VOT Podcasts")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VOT")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                outputUri = context.contentResolver.insert(collection, values)
                    ?: throw IllegalStateException("Failed to create MediaStore audio entry")

                context.contentResolver.openOutputStream(outputUri)?.use { outStream ->
                    tempAudioFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(outputUri, values, null, null)
                destinationPath = "Music/VOT/$fileName"
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "VOT").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                tempAudioFile.copyTo(targetFile, overwrite = true)
                outputUri = Uri.fromFile(targetFile)
                destinationPath = targetFile.absolutePath
            }

            tempAudioFile.delete()
            _exportState.value = ExportState.Success(outputUri, destinationPath)
        } catch (e: Exception) {
            _exportState.value = ExportState.Error(e.message ?: "Audio export failed")
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
