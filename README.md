# VOT Player for Android

Native Android video player that brings automated Voice-Over-Translation (VOT) to YouTube and web videos. It plays the original video stream alongside a synchronized translated voice track using the Yandex VOT protocol.

## Features

- Share-to-play: send video links directly from the YouTube app, mobile browsers, or copy to clipboard for auto-detection.
- Dual audio mixing: independent volume controls for the original audio track and the translated voice (0% to 100%), with quick presets for voice focus, balanced mix, and original only.
- Translation modes: toggle between standard translation and Live Voice. Supports translation into Russian and English.
- Synchronized subtitles: on-screen subtitle overlay with customizable display options.
- MediaSession & lockscreen controls: persistent foreground notification with playback controls (play, pause, seek +/-10s), artwork, and Bluetooth/Android Auto metadata.
- Watch history & continue watching: local SQLite database tracks watched videos, progress bars, and resume positions.
- High-resolution video: parses adaptive DASH formats and merges 1080p, 1440p, or 4K video streams with audio in real time.
- Multi-platform support: extracts and plays videos from YouTube, Twitch VODs/clips, TikTok, Twitter/X, and direct MP4/M3U8 URLs.
- Offline export: download and save translated videos as standalone MP4 files to device storage (`Downloads/VOT`).
- Android TV support: full D-pad remote navigation for smart TVs and TV boxes.
- Picture-in-Picture (PiP) and background playback: continue watching while using other apps or with the screen turned off.
- Playback synchronization: dual ExoPlayer instances with periodic clock drift correction and playback speed matching.
- Resilient backend client: binary protobuf protocol with HMAC-SHA256 request signing and automatic proxy worker rotation (`vot-worker.eu.cc`, `vot-worker.vtrans.eu.cc`).
- Touch gestures: vertical swiping for volume adjustment, double tap to seek forward or backward.

## Architecture

The project is organized into modular layers:

1. `data/extractor`: `MultiPlatformExtractor` routes incoming links across platforms (YouTube, Twitch, TikTok, Twitter/X, direct streams).
2. `data/youtube`: `YouTubeStreamExtractor` queries the YouTube InnerTube API (Android VR client) to obtain direct `googlevideo.com` progressive MP4 streams and adaptive 1080p/4K streams.
3. `data/vot`: `VotProtobuf` and `VotApiClient` handle binary Protobuf serialization, generate SHA-256 HMAC request signatures, set security headers (`Sec-Vtrans-Token`, `Sec-Vtrans-Sk`, `Vtrans-Signature`), and communicate with VOT proxy workers.
4. `data/db`: `WatchHistoryDatabase` manages local SQLite persistence for playback positions, timestamps, and volume preferences.
5. `player`: `VotPlayerManager` orchestrates dual `ExoPlayer` instances, handles `MergingMediaSource` for high-resolution video streams, and registers with `MediaSession`.
6. `service`: `VotMediaService` publishes system media session notifications for background playback and lockscreen controls.
7. `export`: `VotExportManager` downloads and packages video streams and translated audio into local MP4 files.
8. `ui`: Jetpack Compose interface including `HistoryScreen`, `PlayerScreen`, volume controls (`DualVolumeBar`), gesture overlay (`GestureOverlay`), settings (`SettingsDialog`), and `ExportProgressDialog`.

## Requirements

- Android 8.0 (API level 26) or higher
- Android SDK 35
- JDK 17
- Gradle 8.12 (included via wrapper)

## Building from source

Clone the repository and build the debug APK with Gradle:

```bash
git clone https://github.com/alexandrmotologa/vot-android-player.git
cd vot-android-player
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To install directly to a connected Android device:

```bash
./gradlew installDebug
```

## How to use

1. Open a YouTube video in the official YouTube app or your browser.
2. Tap the **Share** button and select **VOT Player** from the app list (or paste any video link into the home screen search bar).
3. The app opens, extracts the video streams, requests the voice-over translation, and starts playing both synchronized streams.
4. Use the bottom sliders to balance the original audio volume and the translated voice volume.
5. Tap the settings gear icon to select video quality (up to 4K), standard or live voice, toggle subtitles, or export the video offline.

## License

MIT License. See [LICENSE](LICENSE) for details.
