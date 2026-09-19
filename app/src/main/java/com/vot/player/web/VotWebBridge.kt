package com.vot.player.web

import android.webkit.JavascriptInterface

class VotWebBridge(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSeek: (positionMs: Long) -> Unit,
    private val onTimeUpdate: (positionMs: Long) -> Unit = {},
    private val onRateChange: (rate: Float) -> Unit = {}
) {
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
            .standalone-collection-badge-renderer {
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

                    video.addEventListener('pause', function() {
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
        onPlay()
    }

    @JavascriptInterface
    fun onVideoPause() {
        onPause()
    }

    @JavascriptInterface
    fun onVideoSeek(positionMs: Long) {
        onSeek(positionMs)
    }

    @JavascriptInterface
    fun onVideoTimeUpdate(positionMs: Long) {
        onTimeUpdate(positionMs)
    }

    @JavascriptInterface
    fun onVideoRateChange(rate: Float) {
        onRateChange(rate)
    }
}
