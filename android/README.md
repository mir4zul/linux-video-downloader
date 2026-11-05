# Android app

This is the Android app for Download. It has a native URL entry screen, accepts links shared from other apps, and uses the embedded yt-dlp engine to analyze supported public video pages. This phone APK targets arm64-v8a only; emulator x86_64 binaries are excluded to reduce its size.

## Current limitation

The extractor handles public URLs supported by yt-dlp. Private videos, login-required media, DRM-protected media, and sites that block the Android HTTP client can fail. Available quality options depend on the source and the phone's supported video codecs. Selected separate video/audio streams are combined with FFmpeg using stream copy, so the downloaded quality is preserved without re-encoding. Audio-only MP3 is also available; it is converted on the phone.

The Android yt-dlp library is MIT-licensed and includes its third-party notices in the app. FFmpegKit audio is LGPL-3.0 and its license notice is bundled in the app resources. See [yt-dlp-android](https://github.com/ffmpegkit-maintained/yt-dlp-android) and [FFmpegKit for Android](https://github.com/ffmpegkit-maintained/ffmpeg) for their source and license terms.

## Build

Install JDK 17 or newer and Android SDK Platform 35, then run from this directory:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with:

```sh
./gradlew installDebug
```
