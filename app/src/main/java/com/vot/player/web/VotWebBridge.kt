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
    private val onFullscreenToggle: (Boolean) -> Unit = {},
    private val onCaptionsReceived: ((String) -> Unit)? = null,
    private val onUrlChanged: ((String) -> Unit)? = null
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
            .ytp-progress-bar-container,
            .ytp-unmute,
            .ytm-unmute-button,
            .player-control-overlay-unmute-button,
            button.unmute-button,
            .ytp-unmute-inner,
            .ytp-unmute-box,
            .player-control-overlay-unmute,
            [aria-label*="unmute" i],
            [aria-label*="Unmute" i],
            .ytp-button[aria-label*="unmute" i] {
                display: none !important;
                pointer-events: none !important;
                opacity: 0 !important;
                visibility: hidden !important;
            }
            body, html {
                background: #000 !important;
            }
        """.trimIndent()

        fun isAdUrl(url: String): Boolean {
            return url.contains("doubleclick.net") ||
                   url.contains("googleads.g.doubleclick.net") ||
                   url.contains("pagead2.googlesyndication.com") ||
                   url.contains("youtube.com/pagead/") ||
                   url.contains("youtube.com/api/stats/ads") ||
                   url.contains("youtube.com/get_midroll_info") ||
                   url.contains("youtube.com/ptracking") ||
                   url.contains("adservice.google.")
        }

        val INJECTION_SCRIPT = """
            (function() {
                if (window.__vot_bridge_installed) return;
                window.__vot_bridge_installed = true;

                window.__vot_original_volume = 0.0;
                window.__vot_seeking_until = 0;

                function applyTargetVolume(video) {
                    if (!video) return;
                    const vol = (window.__vot_original_volume !== undefined) ? window.__vot_original_volume : 0.0;
                    const shouldMute = (vol <= 0.01);
                    if (video.muted !== shouldMute || Math.abs(video.volume - vol) > 0.02) {
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

                // Dedicated seek handler that interacts with YouTube's player API and HTML5 video
                window.__vot_seekTo = function(sec) {
                    window.__vot_seeking_until = Date.now() + 1000; // block timeupdate for 1000ms during seek buffer
                    try {
                        const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (player && typeof player.seekTo === 'function') {
                            player.seekTo(sec, true);
                        }
                    } catch(e) {}
                    try {
                        const video = document.querySelector('video');
                        if (video) {
                            video.currentTime = sec;
                        }
                    } catch(e) {}
                };

                window.__vot_play = function() {
                    try {
                        const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (player && typeof player.playVideo === 'function') {
                            player.playVideo();
                        }
                    } catch(e) {}
                    try {
                        const video = document.querySelector('video');
                        if (video && video.paused) {
                            video.play();
                        }
                    } catch(e) {}
                };

                window.__vot_pause = function() {
                    try {
                        const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (player && typeof player.pauseVideo === 'function') {
                            player.pauseVideo();
                        }
                    } catch(e) {}
                    try {
                        const video = document.querySelector('video');
                        if (video && !video.paused) {
                            video.pause();
                        }
                    } catch(e) {}
                };

                // Intercept volumechange: Prevents YouTube's tap-to-unmute from unmuting or resetting volume!
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
                let captionsFetched = false;
                function checkAndFetchCaptions() {
                    if (captionsFetched) return;
                    try {
                        const p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (p && typeof p.getOption === 'function') {
                            const tracklist = p.getOption('captions', 'tracklist') || [];
                            if (tracklist.length > 0) {
                                let track = tracklist.find(t => t.languageCode === 'ru' || (t.vssId && t.vssId.includes('.ru')))
                                    || tracklist.find(t => t.languageCode === 'en')
                                    || tracklist[0];
                                if (track && track.baseUrl) {
                                    captionsFetched = true;
                                    let cUrl = track.baseUrl;
                                    if (!cUrl.includes('fmt=')) cUrl += '&fmt=json3';
                                    fetch(cUrl)
                                        .then(r => r.text())
                                        .then(txt => {
                                            if (txt && txt.length > 20 && window.VotAndroidBridge && window.VotAndroidBridge.onCaptionsLoaded) {
                                                window.VotAndroidBridge.onCaptionsLoaded(txt);
                                            }
                                        })
                                        .catch(() => {});
                                }
                            }
                        }
                    } catch(e) {}
                }

                // Active ad blocking & skipping for mobile YouTube (safe - never touches video.currentTime)
                function skipAndBlockAds() {
                    try {
                        const skipBtns = document.querySelectorAll(
                            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button.ytp-ad-skip-button-slot, [class*="skip-button"], .ytm-skip-ad-button'
                        );
                        skipBtns.forEach(function(b) {
                            try {
                                if (b.offsetParent !== null || b.offsetWidth > 0 || b.offsetHeight > 0) {
                                    b.click();
                                }
                            } catch(e) {}
                        });

                        const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (player && typeof player.skipAd === 'function') {
                            const isAd = (player.classList && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')))
                                || (typeof player.getAdState === 'function' && player.getAdState() === 1);
                            if (isAd) {
                                try { player.skipAd(); } catch(e) {}
                            }
                        }

                        const adSelectors = [
                            'ytm-promoted-sparkles-web-renderer',
                            'ytm-companion-ad-renderer',
                            'ytm-ad-slot-renderer',
                            '.ad-container',
                            'ytm-mealbar-promo-renderer',
                            'ytm-promoted-video-renderer',
                            '.sparkles-light-cta',
                            '.standalone-collection-badge-renderer-icon'
                        ];
                        adSelectors.forEach(function(sel) {
                            try {
                                document.querySelectorAll(sel).forEach(function(el) {
                                    el.style.display = 'none';
                                });
                            } catch(e) {}
                        });
                    } catch(e) {}
                }

                // SPA URL Change detection
                let lastKnownHref = window.location.href;
                function checkUrlChange() {
                    const curHref = window.location.href;
                    if (curHref !== lastKnownHref) {
                        lastKnownHref = curHref;
                        captionsFetched = false;
                        if (window.VotAndroidBridge && typeof window.VotAndroidBridge.onUrlChanged === 'function') {
                            window.VotAndroidBridge.onUrlChanged(curHref);
                        }
                    }
                }
                window.addEventListener('yt-navigate-finish', checkUrlChange);
                window.addEventListener('popstate', checkUrlChange);
                const _origPush = history.pushState;
                history.pushState = function() {
                    _origPush.apply(this, arguments);
                    checkUrlChange();
                };
                const _origReplace = history.replaceState;
                history.replaceState = function() {
                    _origReplace.apply(this, arguments);
                    checkUrlChange();
                };

                setInterval(function() {
                    const v = document.querySelector('video');
                    if (v) {
                        applyTargetVolume(v);
                        if (!v.paused) {
                            notifyTime(v);
                        }
                    }
                    checkAndFetchCaptions();
                    checkUrlChange();
                    skipAndBlockAds();
                }, 300);

                document.addEventListener('click', function(e) {
                    const target = e.target;
                    // If user clicked directly on an unmute element that escaped CSS, suppress it
                    if (target && target.closest && target.closest('.ytp-unmute, .ytm-unmute-button, button.unmute-button, [aria-label*="unmute" i]')) {
                        e.preventDefault();
                        e.stopPropagation();
                    }

                    // Re-apply target volume on user clicks to negate YouTube's tap-to-unmute
                    const v = document.querySelector('video');
                    if (v) {
                        applyTargetVolume(v);
                        setTimeout(function() { applyTargetVolume(v); }, 20);
                        setTimeout(function() { applyTargetVolume(v); }, 60);
                        setTimeout(function() { applyTargetVolume(v); }, 150);
                        setTimeout(function() { applyTargetVolume(v); }, 300);
                        setTimeout(function() { applyTargetVolume(v); }, 600);
                    }

                    const fsBtn = target ? target.closest('.fullscreen-icon, button.ytp-fullscreen-button, button[aria-label*="Full screen"], button[aria-label*="Fullscreen"]') : null;
                    if (fsBtn) {
                        if (window.VotAndroidBridge) window.VotAndroidBridge.onFullscreenChanged(true);
                    } else {
                        if (window.VotAndroidBridge) {
                            window.VotAndroidBridge.onUserTap();
                        }
                    }
                    setTimeout(checkUrlChange, 200);
                    setTimeout(skipAndBlockAds, 100);
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

                window.__vot_setInitialPosition = function(sec) {
                    if (sec <= 0) return;
                    let attempts = 0;
                    const interval = setInterval(function() {
                        attempts++;
                        const video = document.querySelector('video');
                        if (video && video.readyState >= 1) {
                            window.__vot_seekTo(sec);
                            clearInterval(interval);
                        } else if (attempts > 25) {
                            clearInterval(interval);
                        }
                    }, 250);
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

    @JavascriptInterface
    fun onCaptionsLoaded(rawJson: String) {
        mainHandler.post { onCaptionsReceived?.invoke(rawJson) }
    }

    @JavascriptInterface
    fun onUrlChanged(newUrl: String) {
        mainHandler.post { onUrlChanged?.invoke(newUrl) }
    }
}
