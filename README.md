# VOT Player for Android

Native, ad-free Android video player that brings automated neural **Voice-Over Translation (VOT)** to English videos into **Russian**. Based on the open-source [voice-over-translation](https://github.com/ilyhalight/voice-over-translation) protocol, VOT Player fetches neural voiceovers and synchronized multilingual subtitles from Yandex translation backends, playing them seamlessly alongside high-resolution video streams.

---

## Core Translation Capabilities

### 1. English to Russian Neural Voiceover
- **Real-Time Synchronized Audio Dubbing**: Plays English video streams alongside neural Russian voiceover tracks with automatic clock drift correction and volume ducking.
- **Two Distinct Voice Technologies**:
  - **Standard Voices**: High-stability neural synthesis with selectable voice genders (Male, Female, Auto) and voice actors (**Filipp**, **Ermil**, **Alena**, **Oksana**). Reliable for all video lengths.
  - **Live Voices**: Next-generation neural synthesis with realistic human-like pacing, adaptive emotional inflection, and contextual pause placement.
- **Audio Mixing & Ducking**: Independent volume sliders for the original speaker audio (0–100%) and the translated voiceover (0–100%), with one-tap presets for voiceover-focused listening or original audio focus.

### 2. Guaranteed Multi-Subtitles (RU, RO, EN)
- **Russian Subtitles (RU)**: Neural translated subtitle track generated directly from the voiceover alignment pipeline.
- **Romanian Subtitles (RO)**: Neural translated Romanian subtitle stream delivered through proxy infrastructure to avoid YouTube HTTP 429 rate-limiting.
- **English Original (EN)**: Verbatim original captions extracted directly from YouTube timedtext streams without third-party degradation.
- **Instant Toggle & Styling**: Easily switch between OFF, Russian, Romanian, and English directly from the player overlay or settings sheet.

### 3. Smart Translation Trigger Modes
Configure how and when voiceover translation initiates for every video:
- **Always Auto (`ALWAYS_AUTO`)**: Translation starts immediately in the background upon opening any video.
- **Ask Every Time (`ASK_EVERY_TIME`)**: Displays a lightweight banner on launch (`Translate to Russian? [Translate] [Keep Original]`). Playback begins immediately without waiting for server queries.
- **Manual Only (`MANUAL`)**: Starts playing the video in its original language without querying VOT proxy servers. Tap the **Translate (RU)** button on the top action bar or floating pill at any time to request translation on demand.
- **Smart Russian Video Detection (`autoSkipRussianVideos`)**: Automatically inspects video metadata (Cyrillic character density in title and creator name). If the video is already in Russian, the voiceover query is skipped automatically, preventing awkward duplicate Russian audio and conserving proxy bandwidth. Subtitles remain available.

---

## Player Features

- **Dual Playback Modes**:
  - **Native Player (ExoPlayer)**: Clean, high-performance player with hardware acceleration, adaptive DASH 4K/60fps video, touch gestures (brightness, volume, seeking), and Picture-in-Picture (PiP).
  - **YouTube Web View**: Integrated mobile web view with synchronized voiceover playback, enabling full access to YouTube comments, recommendations, playlists, and channel pages.
- **SponsorBlock Integration**: Automatically skips sponsored segments, intros, and sponsor banners using the open-source SponsorBlock database.
- **Offline Export & Downloads**:
  - **Export Video (MP4)**: Packages the high-resolution video stream and the Russian translated audio track into a single MP4 file saved to `Downloads/VOT`.
  - **Export Audio (MP3)**: Extracts and saves the voiceover track as a standalone MP3 file in `Music/VOT` for offline listening.
- **Podcast & Audio-Only Mode**: Shuts off the video decoder to minimize battery and mobile data usage while keeping background audio and notification controls alive.
- **Watch History & Resume**: SQLite database stores watch progress, resume timestamps, and custom volume levels for every video.
- **In-App Auto Updater**: Automatically checks GitHub releases for new versions and downloads APK updates directly.
- **Share-to-Play**: Open links directly from the YouTube app, Twitter/X, Reddit, TikTok, or browser via Android's native share sheet, or via clipboard auto-detection.

---

## Technical Architecture

```
com.vot.player/
├── data/
│   ├── db/              # Room / SQLite WatchHistoryDatabase & DAO
│   ├── extractor/       # MultiPlatformExtractor (YouTube, Twitch, TikTok, direct URLs)
│   ├── model/           # Data models (UniversalVideoInfo, SubtitleCue, VoiceType)
│   ├── pref/            # PlayerPreferences (TranslationTriggerMode, PlayerMode, etc.)
│   ├── sponsorblock/    # SponsorBlockClient for skip segment queries
│   ├── update/          # UpdateChecker for GitHub Releases APK downloads
│   ├── vot/             # VotApiClient, binary Protobuf serializer, HMAC-SHA256 signature generator
│   └── youtube/         # YouTubeStreamExtractor using InnerTube API client endpoints
├── export/              # VotExportManager (downloading & multiplexing MP4/MP3)
├── player/              # VotPlayerManager (dual ExoPlayer instances, clock sync, volume ducking)
├── service/             # VotMediaService (MediaSession, persistent notification, lockscreen controls)
└── ui/
    ├── components/      # SettingsBottomSheet, SettingsDialog, PlayerModeDialog, Gestures
    ├── theme/           # Dark AMOLED styling, typography, colors
    ├── HistoryScreen.kt # Home screen with URL input, watch history, and clipboard listener
    ├── PlayerScreen.kt  # Native ExoPlayer surface with on-screen HUD and translate prompt
    └── YouTubeWebScreen.kt # Web player with floating VOT translation controls
```

### Protocol & Security
Requests to Yandex VOT servers require binary Protocol Buffers and custom request signatures:
- **Binary Protobuf**: Encodes video URL, target language, voice actor choices, and translation session flags.
- **HMAC-SHA256 Signing**: All outgoing requests are cryptographically signed using `Vtrans-Signature` and token headers.
- **Proxy Rotation**: Automatically fails over between resilient Cloudflare worker proxies (`vot-worker.eu.cc`, `vot-worker.vtrans.eu.cc`) when upstream rate limits or network hiccups occur.

---

## Requirements

- **Android Version**: Android 8.0 (API level 26) or higher
- **Build Target**: Android SDK 35 (compileSdk 35, targetSdk 35)
- **JDK**: Java 17
- **Gradle**: Gradle 8.12 (included via `./gradlew`)

---

## Building from Source

Clone the repository and compile the debug APK:

```bash
git clone https://github.com/alexandrmotologa/vot-android-player.git
cd vot-android-player
./gradlew assembleDebug
```

The compiled APK will be output at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To build a signed or unsigned release APK:
```bash
./gradlew assembleRelease
```

---

## Attribution & Acknowledgments

- [voice-over-translation](https://github.com/ilyhalight/voice-over-translation) by ilyhalight for the core reverse-engineering of the Yandex VOT protocol.
- [SponsorBlock](https://sponsor.ajay.app/) for crowd-sourced sponsor segment skipping.
- [ExoPlayer / Media3](https://github.com/androidx/media) for native video and audio rendering.

---

## License

MIT License. See [LICENSE](LICENSE) for details.
