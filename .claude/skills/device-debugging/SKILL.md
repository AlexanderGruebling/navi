---
name: device-debugging
description: Run Dotwave on the Dotwave_API_36 emulator and drive or inspect playback from adb (DebugPlaybackReceiver actions, NaviProbe/NaviDebug logcat tags, screenshots). Use when testing on a device or emulator.
---

## Emulator

An AVD `Dotwave_API_36` exists: Pixel 7 profile (1080×2400), Android 16 (API 36), Google APIs image, KVM accelerated. Use it when no phone is connected:

```sh
~/Android/Sdk/emulator/emulator -avd Dotwave_API_36 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &   # headless; drop -no-window to see it
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
~/Android/Sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p > screen.png
```

With a phone and the emulator both attached, pick one with `ANDROID_SERIAL` / `-s`.

## Debugging

The debug build includes `DebugPlaybackReceiver` for driving playback from adb. Actions: `PLAY_PLAYLIST` (optional `--ei index N`), `TOGGLE`, `NEXT`, `PREVIOUS`, `SEEK`, `STATE` and `LYRICS_SCAN` (logs which songs of a playlist have lyrics, tag `NaviDebug`).

```sh
adb shell am broadcast -n dev.gruebling.navi.debug/dev.gruebling.navi.debug.DebugPlaybackReceiver -a navi.debug.PLAY_PLAYLIST --es name "'FM4 Charts'"
```

Debug logs use the logcat tag `NaviProbe`.
