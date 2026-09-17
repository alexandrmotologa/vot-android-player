package com.vot.player.service

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class VotMediaService : MediaSessionService() {

    companion object {
        @Volatile
        var activeSession: MediaSession? = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return activeSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = activeSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        activeSession?.run {
            player.release()
            release()
        }
        activeSession = null
        super.onDestroy()
    }
}
