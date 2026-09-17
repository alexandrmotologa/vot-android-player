# VOT Player for Android

Native Android video player that brings automated Voice-Over-Translation (VOT) to YouTube videos. It plays the original video stream alongside a synchronized translated voice track using the Yandex VOT protocol.

## Features

- Share-to-play: send video links directly from the YouTube app, mobile browsers, or copy to clipboard for auto-detection.
- Dual audio mixing: independent volume controls for the original audio track and the translated voice (0% to 100%), with quick presets for voice focus, balanced mix, and original only.
- Translation modes: toggle between standard translation and Live Voice. Supports translation into Russian and English.
- Synchronized subtitles: on-screen subtitle overlay with customizable display options.
- Picture-in-Picture (PiP) and background playback: continue watching while using other apps or with the screen turned off.
- Playback synchronization: dual ExoPlayer instances with periodic clock drift correction and playback speed matching.
- Direct stream extraction: extracts video and audio streams directly through the InnerTube Android VR client, avoiding external scraping services.
- Resilient backend client: binary protobuf protocol with HMAC-SHA256 request signing and automatic proxy worker rotation (`vot-worker.eu.cc`, `vot-worker.vtrans.eu.cc`).
- Touch controls: vertical gestures for volume adjustment, double tap to seek forward or backward.

## Architecture

The project is structured into three main layers:

1. `data/youtube`: `YouTubeStreamExtractor` queries the YouTube InnerTube API (Android VR client) to obtain direct `googlevideo.com` progressive MP4 stream URLs.
2. `data/vot`: `VotProtobuf` and `VotApiClient` handle binary Protobuf serialization, generate SHA-256 HMAC request signatures, set security headers (`Sec-Vtrans-Token`, `Sec-Vtrans-Sk`, `Vtrans-Signature`), and communicate with VOT proxy workers.
3. `player`: `VotPlayerManager` orchestrates two `ExoPlayer` instances (one for the main video stream, one for the translated audio stream), synchronizing position and playback state.
4. `ui`: Jetpack Compose UI including `PlayerScreen`, volume mixing controls (`DualVolumeBar`), gesture overlay (`GestureOverlay`), settings (`SettingsDialog`), and synchronized subtitle rendering (`SubtitleOverlay`).

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
2. Tap the **Share** button and select **VOT Player** from the app list.
3. The app opens, extracts the video streams, requests the voice-over translation, and starts playing both synchronized streams.
4. Use the bottom sliders to balance the original audio volume and the translated voice volume.
5. Tap the settings gear icon to select standard or live voice, toggle subtitles, or switch translation language.

## License

MIT License. See [LICENSE](LICENSE) for details.
