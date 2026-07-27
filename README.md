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
