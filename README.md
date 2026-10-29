# Night Player

Audiobook player for Android with a sleep timer, a slow fade-out, speed that stays the same between tracks, and per-file resume.

## Build with GitHub (no install)

1. Create a new private repository on github.com.
2. Click "uploading an existing file", then drag in everything inside this folder, including the hidden `.github` folder.
3. Open the Actions tab and wait for "Build APK" to finish (about 5 minutes).
4. Open the finished run and download `NightPlayer-apk`. Unzip it to get `app-debug.apk`.
5. Copy the APK to your phone, open it, and allow installing from that source when Android asks.

## Build with Android Studio

1. Open this folder in Android Studio.
2. Plug in your phone with USB debugging on and press Run.

## Behaviour

- Speed is a single setting, applied to every track.
- Sleep timer counts playing time only, across chapters. When it ends, volume fades to silence over the fade length, then playback pauses and the volume resets.
- The timer starts each time you press play while a timer length is set.
- End of track: "Pause" stops after each file; "Continue" plays the next file.
- Each file remembers where you stopped. Picking a file from the track list resumes it.
- Plays the audio files in the chosen folder, sorted by name with numbers in order (2 before 10). Subfolders are not included.
