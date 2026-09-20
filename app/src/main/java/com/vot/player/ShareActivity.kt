package com.vot.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.vot.player.data.youtube.YouTubeStreamExtractor

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = extractUrlFromIntent(intent)
        if (targetUrl != null) {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_VIDEO_URL, targetUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(mainIntent)
        }

        finish()
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null

        if (Intent.ACTION_SEND == intent.action && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            // Check if text contains a YouTube URL
            val words = sharedText.split("\\s+".toRegex())
            for (word in words) {
                if (YouTubeStreamExtractor.extractVideoId(word) != null) {
                    return word
                }
            }
        } else if (Intent.ACTION_VIEW == intent.action) {
            return intent.dataString
        }

        return null
    }
}
