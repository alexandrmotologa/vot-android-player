package com.vot.player.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class VotWebBridge(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSeek: (positionMs: Long) -> Unit,
    private val onTimeUpdate: (positionMs: Long) -> Unit = {},
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
            .video-ads {
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

                function hookVideo(video) {
                    if (!video || video.__vot_hooked) return;
                    video.__vot_hooked = true;

                    // If already playing when hooked, notify bridge immediately
                    if (!video.paused && video.currentTime > 0) {
                        if (window.VotAndroidBridge) {
                            window.VotAndroidBridge.onVideoPlay();
                            window.VotAndroidBridge.onVideoTimeUpdate(Math.round(video.currentTime * 1000));
                        }
                    }

                    video.addEventListener('play', function() {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoPlay();
                    });

                    video.addEventListener('playing', function() {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoPlay();
                    });

                    video.addEventListener('pause', function() {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoPause();
                    });

                    video.addEventListener('waiting', function() {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onVideoPause();
                    });

                    video.addEventListener('seeked', function() {
                        if (window.VotAndroidBridge) {
                            window.VotAndroidBridge.onVideoSeek(Math.round(video.currentTime * 1000));
                        }
                    });

                    video.addEventListener('timeupdate', function() {
                        if (window.VotAndroidBridge && !video.paused) {
                            window.VotAndroidBridge.onVideoTimeUpdate(Math.round(video.currentTime * 1000));
                        }
                    });

                    video.addEventListener('ratechange', function() {
                        if (window.VotAndroidBridge) {
                            window.VotAndroidBridge.onVideoRateChange(video.playbackRate);
                        }
                    });
                }

                function findAndHook() {
                    const videos = document.querySelectorAll('video');
                    videos.forEach(hookVideo);
                }

                const observer = new MutationObserver(findAndHook);
                observer.observe(document.documentElement, { childList: true, subtree: true });
                findAndHook();

                document.addEventListener('click', function(e) {
                    // Check if clicked fullscreen button inside player
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
                    const video = document.querySelector('video');
                    if (video) {
                        video.volume = Math.max(0, Math.min(1, vol));
                        video.muted = (vol <= 0.01);
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
    fun onVideoTimeUpdate(positionMs: Long) {
        mainHandler.post { onTimeUpdate(positionMs) }
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
