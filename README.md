# YT Downloader — Android (Kotlin + Jetpack Compose)

Native Android port of the Electron "YT Downloader" (`../ytproject/`), plus the new
**Download as MP3** feature. Browse YouTube in an in-app `WebView`; tapping a video
downloads it with yt-dlp (via youtubedl-android) and plays it locally, ad-free. MP3s
are extracted/encoded to a separate folder and listed in a separate **Music** tab.

Specs: `../ANDROID_PORT_PLAN.md` and `../ANDROID_PORT_PROMPT.md`.

---

## 1. Tech stack

- Kotlin + Jetpack Compose + Material3, single-module Gradle project.
- Download engine: `com.github.yausername.youtubedl-android:library` + `:ffmpeg`
  (bundles yt-dlp + ffmpeg; used for merge/remux and MP3 encode).
- Playback: Media3 ExoPlayer.
- Storage: `SharedPreferences` (settings) + two JSON files in `filesDir`
  (`videoLibrary.json`, `mp3Library.json`), atomic writes.
- `minSdk 26`, `targetSdk 34` (Xiaomi 14T Pro, Android 14, arm64).

## 2. Dependency versions (pinned in `gradle/libs.versions.toml`)

| Dependency | Version |
|---|---|
| AGP | 8.5.2 |
| Kotlin | 1.9.24 |
| Compose BOM | 2024.06.00 (compiler ext 1.5.14) |
| Navigation Compose | 2.7.7 |
| Activity Compose | 1.9.0 |
| Lifecycle | 2.8.3 |
| Coroutines | 1.8.1 |
| Media3 | 1.4.0 |
| youtubedl-android (`library` + `ffmpeg`) | **0.17.4** ⚠️ re-verify on first sync |
| JUnit | 4.13.2 |

> ⚠️ **Re-verify `youtubedlAndroid` on first sync.** It was pinned from public
> release info (0.17.4 confirmed). If JitPack can't resolve it, bump to the latest
> release tag on <https://github.com/yausername/youtubedl-android/releases> and
> re-check the `YoutubeDL` / `YoutubeDLRequest` / `DownloadProgressCallback` API.
> Gradle: `8.7`.

## 3. Build (on the dev machine — JDK 17 + Android Studio / Android SDK 34)

```powershell
cd "C:\Users\calvi\Desktop\yt- mobile\yt-android"
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # runs FallbackResolverTest + ProgressParserTest
```

**First run / wrapper note:** the `gradle/wrapper/gradle-wrapper.jar` binary could
not be downloaded in the sandbox. Open the project in **Android Studio** (File →
Open → this folder) — it will download Gradle 8.7 and sync. For command-line
`./gradlew`, first generate the wrapper from Android Studio's Terminal:
`gradle wrapper --gradle-version 8.7` (or use Android Studio's Build menu).

## 4. Install on the phone

```powershell
# USB debugging on: Settings → Developer options → USB debugging
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or sideload the APK: enable **"Install unknown apps"** for your file manager /
browser, copy `app-debug.apk` to the phone, and tap it.

## 5. Xiaomi/HyperOS — required for background downloads

Xiaomi aggressively kills background apps. After install:

1. **Settings → Apps → YT Downloader → Battery saver → "No restrictions".**
2. **Settings → Apps → YT Downloader → Autostart → ON.**
3. Grant **Notifications** (the foreground service notification keeps downloads
   alive when the screen is off / app is backgrounded).

## 6. On-device test checklist (Definition of Done, ANDROID_PORT_PLAN §13)

1. YouTube tab loads in the WebView; tap a video → it downloads and plays in the
   Player tab, **no video ad**.
2. Tap the same video again → plays instantly from the library (dedup, no re-download).
3. Smart fallback: set quality to something unavailable (e.g. 4320p on a 1080p-only
   video) → **downgrade auto-accepts**; set a low quality (e.g. 144p on an HD video)
   → **upgrade prompts**.
4. Player: progress, fallback dialog, **Delete**, and **auto-delete-after-watch**.
5. Videos library lists downloads with **Play/Delete**.
6. Settings persist (quality, tap behavior, bitrate, auto-delete, PiP).
7. **Native PiP** (toggle in Settings): after a fresh video download completes, the
   video floats in a small window while you use other apps.
8. **MP3**: tap a video → bottom sheet → "Download as MP3" → it lands in a separate
   folder, appears in the **Music** tab, and supports **Play / Delete / Share**.
   Dedup is independent of videos (same video can exist in both libraries).
9. Start a download, press Home / turn the screen off → the foreground-service
   notification keeps the download running (after step 5's battery exemption).

## 7. Verified vs. not verified

**Verified here (by inspection only — no JDK/Android SDK in this environment):**

- `resolveTarget` and `parseProgressLine` are byte-for-byte logic ports of
  `ytproject/src/main/downloader.js`, covered by JUnit tests mirroring
  `ytproject/scripts/test-fallback.js` fixtures (8 + 7 cases).
- Click interceptor + autoplay guard injected JS is a faithful port of
  `ytproject/src/preload/youtubePreload.js` (capture-phase, no `preventDefault`).
- SPA fallbacks (`doUpdateVisitedHistory` + `shouldOverrideUrlLoading`) port the
  Electron `did-navigate-in-page` / `will-navigate` behavior.
- Serial queue, dedup-by-videoId (+file-exists re-check), `CompletableDeferred`
  fallback ask/answer, and delete-with-retry port `downloader.js`.

**Not verified (requires the physical phone / Android toolchain):**

- `./gradlew assembleDebug` / `testDebugUnitTest` — **compilation is BLOCKED here**
  (no JDK/Gradle/Android SDK). Kotlin was syntax-checked by inspection only.
- Real YouTube download, MP3 encode (ffmpeg), playback, native PiP, background
  service, WebView interception on the real site.

## 8. Deviations from ANDROID_PORT_PLAN.md

- **Video remux skipped.** ExoPlayer plays mp4/webm/mkv natively, so the Electron
  `ensurePlayable` (ffmpeg `-c copy` remux) is unnecessary; the MP3 encode still
  uses ffmpeg via yt-dlp's postprocessor. (`Mp3Converter` builds that command.)
- **JSON via platform `org.json`** instead of kotlinx-serialization (avoids the
  serialization compiler plugin; the plan explicitly allowed org.json).
- **Folders shown read-only** in Settings (SAF folder picker + MediaStore export
  are Phase 3). Defaults are app-specific `Movies/YTDownloader` / `Music/YTDownloader`.
- **Music tab icon** uses a star (core icon set); purely cosmetic.

## 9. Legal note

For personal use only. Downloading YouTube content may violate YouTube's ToS
and/or copyright law depending on content and jurisdiction.
