package com.vot.player.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class VotWebBridge(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSeek: (positionMs: Long) -> Unit,
    private val onTimeUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    private val onRateChange: (rate: Float) -> Unit = {},
    private val onScreenTap: () -> Unit = {},
    private val onFullscreenToggle: (Boolean) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val INTERFACE_NAME = "VotAndroidBridge"

        val MINIMALIST_CSS = """
            #comments, 
            #related, 
            ytm-item-section-renderer, 
            .header-bar, 
            ytm-mobile-topbar-renderer, 
            ytm-pivot-bar-renderer, 
            .slim-video-metadata-header, 
            ytm-engagement-panel, 
            .ytm-carousel,
            ytm-search-box,
            .standalone-collection-badge-renderer,
            ytm-slim-video-action-bar-renderer,
            ytm-slim-owner-renderer,
            .slim-video-information-renderer,
            ytm-video-description-header-renderer,
            .slim-video-action-bar-actions,
            ytm-subscribe-button-renderer,
            ytm-single-column-watch-next-results-renderer,
            ytm-segmented-like-dislike-button-renderer,
            ytm-button-renderer,
            .ytm-video-meta,
            ytm-reel-shelf-renderer,
            ytm-ad-slot-renderer,
            .ad-container,
            .video-ads,
            .ytp-chrome-bottom,
            .ytp-chrome-top,
            .ytp-play-button,
            .ytp-next-button,
            .ytp-prev-button,
            .player-controls-bottom,
            .player-controls-middle,
            ytm-custom-control,
            .ytp-gradient-bottom,
            .ytp-gradient-top,
            .ytp-progress-bar-container,
            .ytp-unmute,
            .ytm-unmute-button,
            .player-control-overlay-unmute-button,
            button.unmute-button,
            .ytp-unmute-inner,
            .ytp-unmute-box {
                display: none !important;
            }
            video {
                display: block !important;
                opacity: 1 !important;
                visibility: visible !important;
                width: 100% !important;
                height: 100% !important;
            }
            body, html {
                background: #000 !important;
            }
        """.trimIndent()

        val INJECTION_SCRIPT = """
            (function() {
                if (window.__vot_bridge_installed) return;
                window.__vot_bridge_installed = true;

                window.__vot_original_volume = 0.0;
                window.__vot_seeking_until = 0;

                function applyTargetVolume(video) {
                    const vol = (window.__vot_original_volume !== undefined) ? window.__vot_original_volume : 0.0;
                    const shouldMute = (vol <= 0.01);
                    if (video) {
                        video.muted = shouldMute;
                        video.volume = vol;
                    }
                    const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                    if (player) {
                        try {
                            if (shouldMute) {
                                if (typeof player.mute === 'function') player.mute();
                            } else {
                                if (typeof player.unMute === 'function') player.unMute();
                                if (typeof player.setVolume === 'function') player.setVolume(Math.round(vol * 100));
                            }
                        } catch(e) {}
                    }
                }

                function notifyTime(video) {
                    if (!video || !window.VotAndroidBridge) return;
                    if (Date.now() < window.__vot_seeking_until) return; // Don't report old positions right after seeking!
                    const posMs = Math.round(video.currentTime * 1000);
                    const durMs = Math.round((video.duration || 0) * 1000);
                    window.VotAndroidBridge.onVideoTimeUpdate(posMs, durMs);
                }

                // Dedicated seek handler that interacts with YouTube's player API and HTML5 video
                window.__vot_seekTo = function(sec) {
                    window.__vot_seeking_until = Date.now() + 1000; // block timeupdate for 1000ms during seek buffer
                    const video = document.querySelector('video');
                    if (video) {
                        try {
                            video.currentTime = sec;
                        } catch(e) {}
                    }
                    const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                    if (player && typeof player.seekTo === 'function') {
                        try {
                            player.seekTo(sec, true);
                        } catch(e) {}
                    }
                };

                // Intercept volumechange: Prevents YouTube's tap-to-unmute from unmuting or resetting volume!
                document.addEventListener('volumechange', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                        // Only enforce mute if target volume is muted (0.0)
                        if (window.__vot_original_volume !== undefined && window.__vot_original_volume <= 0.01) {
                            applyTargetVolume(e.target);
                        }
                    }
                }, true);

                // Capture phase listeners catch play/pause/timeupdate from ANY video element,
                // even before it's hooked or if it's created dynamically!
                document.addEventListener('play', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoPlay();
                        applyTargetVolume(e.target);
                        notifyTime(e.target);
                    }
                }, true);

                document.addEventListener('playing', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoPlay();
                        applyTargetVolume(e.target);
                    }
                }, true);

                document.addEventListener('pause', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoPause();
                    }
                }, true);

                document.addEventListener('timeupdate', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO') && !e.target.paused) {
                        notifyTime(e.target);
                    }
                }, true);

                document.addEventListener('ratechange', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoRateChange(e.target.playbackRate);
                    }
                }, true);

                document.addEventListener('seeked', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                        if (window.VotAndroidBridge) {
                            window.VotAndroidBridge.onVideoSeek(Math.round(e.target.currentTime * 1000));
                        }
                    }
                }, true);

                // Periodic time & duration sync (every 500ms) to ensure duration & position never get out of sync
                setInterval(function() {
                    const v = document.querySelector('video');
                    if (v) {
                        applyTargetVolume(v);
                        if (!v.paused) {
                            notifyTime(v);
                        }
                    }
                }, 500);

                document.addEventListener('click', function(e) {
                    const target = e.target;
                    // If user clicked directly on an unmute element that escaped CSS, suppress it
                    if (target && target.closest && target.closest('.ytp-unmute, .ytm-unmute-button, button.unmute-button, [aria-label*="unmute" i]')) {
                        e.preventDefault();
                        e.stopPropagation();
                    }

                    // Only re-apply target volume on user clicks if currently configured as muted
                    const v = document.querySelector('video');
                    if (v && (window.__vot_original_volume === undefined || window.__vot_original_volume <= 0.01)) {
                        applyTargetVolume(v);
                        setTimeout(function() { applyTargetVolume(v); }, 50);
                        setTimeout(function() { applyTargetVolume(v); }, 200);
                    }

                    const fsBtn = target ? target.closest('.fullscreen-icon, button.ytp-fullscreen-button, button[aria-label*="Full screen"], button[aria-label*="Fullscreen"]') : null;
                    if (fsBtn) {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onFullscreenChanged(true);
                    } else {
                        if (window.VotAndroidBridge) {
                            window.VotAndroidBridge.onUserTap();
                        }
                    }
                }, true);

                document.addEventListener('fullscreenchange', function() {
                    const isFs = !!document.fullscreenElement;
                    if (window.VotAndroidBridge) window.VotAndroidBridge.onFullscreenChanged(isFs);
                });

                document.addEventListener('webkitfullscreenchange', function() {
                    const isFs = !!document.webkitFullscreenElement;
                    if (window.VotAndroidBridge) window.VotAndroidBridge.onFullscreenChanged(isFs);
                });

                window.setOriginalVolume = function(vol) {
                    window.__vot_original_volume = Math.max(0, Math.min(1, vol));
                    const video = document.querySelector('video');
                    applyTargetVolume(video);
                };
            })();
        """.trimIndent()
    }

    @JavascriptInterface
    fun onVideoPlay() {
        mainHandler.post { onPlay() }
    }

    @JavascriptInterface
    fun onVideoPause() {
        mainHandler.post { onPause() }
    }

    @JavascriptInterface
    fun onVideoSeek(positionMs: Long) {
        mainHandler.post { onSeek(positionMs) }
    }

    @JavascriptInterface
    fun onVideoTimeUpdate(positionMs: Long, durationMs: Long = 0L) {
        mainHandler.post { onTimeUpdate(positionMs, durationMs) }
    }

    @JavascriptInterface
    fun onVideoRateChange(rate: Float) {
        mainHandler.post { onRateChange(rate) }
    }

    @JavascriptInterface
    fun onUserTap() {
        mainHandler.post { onScreenTap() }
    }

    @JavascriptInterface
    fun onFullscreenChanged(isFullscreen: Boolean) {
        mainHandler.post { onFullscreenToggle(isFullscreen) }
    }
}
