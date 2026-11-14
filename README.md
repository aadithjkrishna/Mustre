# 🎵 Mustre

**Mustre** is a sleek, modern Android audio player and music streaming app built for high-fidelity playback, real-time synchronized lyrics, glassmorphic UI design, and floating player capabilities.

---

## ✨ Key Features

- **🎧 High-Fidelity Audio Playback**: Powered by **Jetpack Media3 (ExoPlayer)** with support for high-res audio streaming and smooth playback control.
- **🎤 Synchronized Lyrics Integration**: Line-by-line synced lyrics powered by **LRCLIB** with dynamic scrolling, line highlights, and instrumental track detection.
- **✨ Glassmorphic & OLED Design**: Beautiful glassmorphic card backdrops, dynamic background blurred artwork, smooth sheet animations, and pure black OLED dark mode.
- **🖼️ Floating Popup Player**: Overlay floating window service (`FloatingPlayerService`) to control playback anywhere on system apps.
- **⚡ Smart Audio & Media Caching**: Built-in disk caching (`AudioCacheManager`) for fast playback resume and low network consumption.
- **🎛️ Audio Quality Metadata**: Real-time media quality detection displaying Hi-Res Lossless and quality badges.
- **🌐 Custom Media Server Backend**: Configurable server endpoint integration for streaming from custom self-hosted audio catalogs.

---

## 🚀 Download APK

Download the latest pre-compiled release APK directly from GitHub:

📥 **[Download Mustre v1.0.1 APK](https://github.com/aadithjkrishna/Mustre/releases/download/v1.0.1/app-release.apk)**

*(Compatible with Android 14+, API 34+)*

---

## 🛠️ Building From Source

### Prerequisites
- JDK 17+
- Android SDK 35+ / Target SDK 37
- Gradle 8.x

### Commands

```bash
# Clone the repository
git clone https://github.com/aadithjkrishna/Mustre.git
cd Mustre

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

The output APK will be located at `app/build/outputs/apk/release/app-release.apk`.

---

## 🏛️ Architecture & Dependencies

- **Language**: Java / Kotlin
- **Media Playback**: Jetpack Media3 ExoPlayer (`androidx.media3:media3-exoplayer`, `media3-session`, `media3-ui`)
- **Networking**: OkHttp 4 & Gson
- **Image Loading**: Glide (`com.github.bumptech.glide`)
- **Min SDK**: 34 (Android 14) | **Target SDK**: 37 (Android 15)

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).
