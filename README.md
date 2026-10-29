# Night Player

A simple Android audiobook player for playing a folder of audio files at night. It has a sleep timer with a slow fade-out, a playback speed that stays the same between tracks, and it remembers where you stopped in every file.

## Features

- **Folder playback.** Choose a folder once. The audio files in it play in order, sorted by name with numbers in order (2 before 10). Subfolders are not included.
- **Sleep timer with fade-out.** Set how long to play before the fade starts (off, 15, 30, 45, 60, 90 or 120 minutes) and how long the fade lasts (off, 5, 10, 15, 20 or 30 minutes). The volume then falls to silence, playback pauses, and the volume resets. The timer counts playing time only, so it keeps running across chapters and does not count time spent paused. It restarts each time you press play.
- **Speed that persists.** One speed setting (0.5× to 2×) applies to every track and is remembered between sessions.
- **End of track.** Choose "Continue" to play the next file or "Pause" to stop after each file.
- **Resume.** Each file remembers its position, and the app reopens on the last folder and track.
- **Reorderable track list.** Tap "Track X of Y" to see all tracks. Tap a track to play it, drag the handle to reorder, or tap "Sort by name" to reset. Files are never renamed, and the order is saved per folder.
- **Controls.** Previous and next track, back 15 seconds, forward 30 seconds, and a draggable progress bar.
- **Background playback.** Keeps playing with the screen off, with lock-screen and headphone controls. Pauses if headphones disconnect.

## Install

1. Open the **Actions** tab of this repository and click the latest successful **Build APK** run.
2. Download the `NightPlayer-apk` artifact and unzip it to get `app-debug.apk`.
3. Copy the APK to your phone and open it. Android will ask you to allow installing apps from that source.

The build runs automatically on every push. You can also start it by hand with **Run workflow** on the Actions tab. Every build is signed with the same key, so a new APK installs over the old one and keeps your settings.

## Build it yourself

- **Android Studio:** open this folder and press Run with a phone connected and USB debugging on.
- **Command line:** with Java 17 and the Android SDK (platform 35) installed, run `./gradlew assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

- `PlaybackService.kt` holds the player (Media3 ExoPlayer), the media session for lock-screen controls, and the sleep timer and fade.
- `PlayerScreen.kt` is the whole interface, built with Jetpack Compose.
- `Store.kt` saves settings, positions, and track order.
- `Library.kt` reads the folder and sorts file names.

## Limitations

- Only files directly inside the chosen folder are played.
- The lock-screen layout is set by Android. The app only chooses which buttons appear.
- The app is signed with a shared debug key, so it is intended for personal use rather than the Play Store.
