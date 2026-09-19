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
            .player-control-background,
            .player-controls-middle,
            ytm-custom-control,
            .ytp-gradient-bottom,
            .ytp-gradient-top,
            .ytp-progress-bar-container {
                display: none !important;
            }
            body, html {
                background: #000 !important;
            }
        """.trimIndent()

        val INJECTION_SCRIPT = """
            (function() {
                if (window.__vot_bridge_installed) return;
                window.__vot_bridge_installed = true;

                window.__vot_original_volume = 0.20;
                window.__vot_seeking_until = 0;

                function applyTargetVolume(video) {
                    if (!video) return;
                    const vol = (window.__vot_original_volume !== undefined) ? window.__vot_original_volume : 0.20;
                    const shouldMute = (vol <= 0.01);
                    if (video.muted !== shouldMute || Math.abs(video.volume - vol) > 0.05) {
                        video.volume = vol;
                        video.muted = shouldMute;
                    }
                }

                function notifyTime(video) {
                    if (!video || !window.VotAndroidBridge) return;
                    if (Date.now() < window.__vot_seeking_until) return; // Don't report old positions right after seeking!
                    const posMs = Math.round(video.currentTime * 1000);
                    const durMs = Math.round((video.duration || 0) * 1000);
                    window.VotAndroidBridge.onVideoTimeUpdate(posMs, durMs);
                }

                // Dedicated seek handler that interacts with YouTube's player API or HTML5 video
                window.__vot_seekTo = function(sec) {
                    window.__vot_seeking_until = Date.now() + 800; // block timeupdate for 800ms
                    const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                    if (player && typeof player.seekTo === 'function') {
                        player.seekTo(sec, true);
                    } else {
                        const video = document.querySelector('video');
                        if (video) {
                            video.currentTime = sec;
                        }
                    }
                };

                // Intercept volumechange: Prevents YouTube's tap-to-unmute from unmuting or resetting volume to 100%!
                document.addEventListener('volumechange', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                        applyTargetVolume(e.target);
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
                    // Re-apply target volume on user clicks to negate YouTube's tap-to-unmute
                    const v = document.querySelector('video');
                    if (v) {
                        setTimeout(function() { applyTargetVolume(v); }, 50);
                        setTimeout(function() { applyTargetVolume(v); }, 250);
                    }

                    const target = e.target;
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
                    if (video) {
                        applyTargetVolume(video);
                    }
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
