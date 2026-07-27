# TV Shield Browser

TV Shield is a lightweight browser designed for Android TV and Google TV. It combines a remote-friendly interface, mouse support, bookmarks, visit history, adjustable zoom, fullscreen video, and local tracker filtering.

## Highlights

- D-pad and mouse navigation
- Two-stage URL editing that avoids remote-control focus traps
- Persistent bookmarks and most-visited websites
- Responsive TV home screen with popular-site shortcuts
- Page zoom controls and immersive fullscreen mode
- Direct media delivery without a local streaming proxy
- Local domain-level tracker and advertising filter
- HTTPS upgrades for manually entered HTTP addresses
- Third-party cookies disabled by default
- Brave Search fallback for search terms

## Requirements

- Android Studio or JDK 17+
- Android SDK 35
- Android TV or Google TV running Android 6.0 (API 23) or newer

## Install with Downloader

1. Install and open **Downloader by AFTVnews** on the TV.
2. Enter code **4671706** and select **Go**.
3. Install `TVShieldBrowser-v0.2.6-release.apk` when prompted.

Short URL: [aftv.news/4671706](https://aftv.news/4671706)

If Android blocks the installation, allow Downloader under **Settings → Apps → Special app access → Install unknown apps**, then retry.

## Build

```sh
./gradlew assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install with ADB

Enable Developer options and wireless debugging on the TV, then run:

```sh
adb connect TV_IP:ADB_PORT
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Shield filter

The bundled hostname list is located at:

```text
app/src/main/assets/blocklist.txt
```

Filtering happens locally in the WebView request path. Media delivery hosts bypass filtering so video segments are not delayed by filter processing.

## Project status

TV Shield is currently a beta. It uses Android WebView rather than shipping a separate Chromium engine. DRM services may reject WebView, and domain filtering cannot reliably remove advertising delivered from the same hosts as requested content.
