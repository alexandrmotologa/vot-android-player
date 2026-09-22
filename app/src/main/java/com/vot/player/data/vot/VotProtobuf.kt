package com.vot.player.data.vot

import com.vot.player.data.model.SubtitleCue
import com.vot.player.data.model.VotTranslationResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object VotProtobuf {

    // --- Varint & Binary Writer / Reader Helpers ---

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7F.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun writeTag(out: ByteArrayOutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint(out, ((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeString(out: ByteArrayOutputStream, fieldNumber: Int, value: String) {
        if (value.isNotEmpty()) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeTag(out, fieldNumber, 2)
            writeVarint(out, bytes.size.toLong())
            out.write(bytes)
        }
    }

    private fun writeBool(out: ByteArrayOutputStream, fieldNumber: Int, value: Boolean) {
        writeTag(out, fieldNumber, 0)
        writeVarint(out, if (value) 1 else 0)
    }

    private fun writeDouble(out: ByteArrayOutputStream, fieldNumber: Int, value: Double) {
        writeTag(out, fieldNumber, 1)
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value)
        out.write(buf.array())
    }

    private fun readVarint(stream: ByteArrayInputStream): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = stream.read()
            if (b == -1) break
            result = result or ((b.toLong() and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readString(stream: ByteArrayInputStream, length: Int): String {
        val bytes = ByteArray(length)
        stream.read(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readDouble(stream: ByteArrayInputStream): Double {
        val bytes = ByteArray(8)
        stream.read(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).double
    }

    private fun skipField(stream: ByteArrayInputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(stream)
            1 -> stream.skip(8)
            2 -> {
                val len = readVarint(stream).toInt()
                stream.skip(len.toLong())
            }
            5 -> stream.skip(4)
        }
    }

    // --- Message Encoders ---

    fun encodeSessionRequest(uuid: String, module: String = "video-translation"): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, 1, uuid)
        writeString(out, 2, module)
        return out.toByteArray()
    }

    fun decodeSessionResponse(bytes: ByteArray): Pair<String, Int> {
        val stream = ByteArrayInputStream(bytes)
        var secretKey = ""
        var expires = 0
        while (stream.available() > 0) {
            val tag = readVarint(stream).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when (field) {
                1 -> {
                    val len = readVarint(stream).toInt()
                    secretKey = readString(stream, len)
                }
                2 -> expires = readVarint(stream).toInt()
                else -> skipField(stream, wire)
            }
        }
        return Pair(secretKey, expires)
    }

    fun encodeTranslationRequest(
        url: String,
        duration: Double = 0.0,
        responseLang: String = "ru",
        requestLang: String = "en",
        firstRequest: Boolean = true,
        useLivelyVoice: Boolean = false,
        videoTitle: String = ""
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, 3, url)
        if (firstRequest) {
            writeBool(out, 5, true)
        }
        val validDuration = if (duration <= 0.0) 300.0 else duration
        writeDouble(out, 6, validDuration)
        writeBool(out, 7, true) // unknown0 = true (CRITICAL for Yandex VOT protocol)
        writeString(out, 8, requestLang)
        writeString(out, 14, responseLang)
        writeBool(out, 15, true) // unknown2 = true
        writeTag(out, 16, 0) // configVersion tag (field 16, wire 0)
        writeVarint(out, 2) // configVersion = 2 (CRITICAL for Live Voice generation)
        if (useLivelyVoice) {
            writeBool(out, 18, true)
        }
        if (videoTitle.isNotEmpty()) {
            writeString(out, 19, videoTitle)
        }
        return out.toByteArray()
    }

    fun encodeTranslationAudioRequest(
        url: String,
        translationId: String,
        fileId: String,
        audioBytes: ByteArray = ByteArray(0)
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, 1, translationId)
        writeString(out, 2, url)

        // Field 6: AudioBufferObject
        val audioBufOut = ByteArrayOutputStream()
        writeString(audioBufOut, 1, fileId)
        if (audioBytes.isNotEmpty()) {
            writeTag(audioBufOut, 2, 2)
            writeVarint(audioBufOut, audioBytes.size.toLong())
            audioBufOut.write(audioBytes)
        }
        val audioBufBytes = audioBufOut.toByteArray()
        writeTag(out, 6, 2)
        writeVarint(out, audioBufBytes.size.toLong())
        out.write(audioBufBytes)

        return out.toByteArray()
    }

    fun decodeTranslationResponse(bytes: ByteArray): VotTranslationResult {
        val stream = ByteArrayInputStream(bytes)
        var url: String? = null
        var duration = 0.0
        var status = 0
        var remainingTime = -1
        var translationId = ""
        var isLivelyVoice = false
        var message: String? = null

        while (stream.available() > 0) {
            val tag = readVarint(stream).toInt()
            val field = tag ushr 3
            val wire = tag and 7
            when (field) {
                1 -> {
                    val len = readVarint(stream).toInt()
                    url = readString(stream, len)
                }
                2 -> duration = readDouble(stream)
                4 -> status = readVarint(stream).toInt()
                5 -> remainingTime = readVarint(stream).toInt()
                7 -> {
                    val len = readVarint(stream).toInt()
                    translationId = readString(stream, len)
                }
                9 -> {
                    val len = readVarint(stream).toInt()
                    message = readString(stream, len)
                }
                10 -> isLivelyVoice = readVarint(stream) != 0L
                else -> skipField(stream, wire)
            }
        }

        return VotTranslationResult(
            url = url,
            duration = duration,
            status = status,
            remainingTime = remainingTime,
            translationId = translationId,
            isLivelyVoice = isLivelyVoice,
            message = message
        )
    }

    fun encodeSubtitlesRequest(url: String, language: String = "en"): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, 1, url)
        writeString(out, 2, language)
        return out.toByteArray()
    }

    data class SubtitleEntry(
        val language: String,
        val url: String,
        val hasTranslation: Boolean,
        val translatedLanguage: String,
        val translatedUrl: String
    )

    fun decodeSubtitlesResponse(bytes: ByteArray): List<SubtitleEntry> {
        val stream = ByteArrayInputStream(bytes)
        val entries = mutableListOf<SubtitleEntry>()

        while (stream.available() > 0) {
            val tag = readVarint(stream).toInt()
            val field = tag ushr 3
            val wire = tag and 7

            if (field == 2 && wire == 2) {
                // SubtitlesObject message
                val len = readVarint(stream).toInt()
                val subBytes = ByteArray(len)
                stream.read(subBytes)
                val subStream = ByteArrayInputStream(subBytes)
                var lang = ""
                var url = ""
                var hasTrans = false
                var transLang = ""
                var transUrl = ""

                while (subStream.available() > 0) {
                    val subTag = readVarint(subStream).toInt()
                    val subField = subTag ushr 3
                    val subWire = subTag and 7
                    when (subField) {
                        1 -> {
                            val strLen = readVarint(subStream).toInt()
                            lang = readString(subStream, strLen)
                        }
                        2 -> {
                            val strLen = readVarint(subStream).toInt()
                            url = readString(subStream, strLen)
                        }
                        3 -> {
                            hasTrans = readVarint(subStream) != 0L
                        }
                        4 -> {
                            val strLen = readVarint(subStream).toInt()
                            transLang = readString(subStream, strLen)
                        }
                        5 -> {
                            val strLen = readVarint(subStream).toInt()
                            transUrl = readString(subStream, strLen)
                        }
                        else -> skipField(subStream, subWire)
                    }
                }

                if (url.isNotEmpty() || transUrl.isNotEmpty()) {
                    entries.add(SubtitleEntry(lang, url, hasTrans, transLang, transUrl))
                }
            } else {
                skipField(stream, wire)
            }
        }
        return entries
    }
}
