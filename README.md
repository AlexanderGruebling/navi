# Dotwave

A native Android client for [Navidrome](https://www.navidrome.org/) (and other Subsonic / OpenSubsonic servers),
written in Kotlin with Jetpack Compose and Media3.

Built for a personal Navidrome server: fast startup, smooth scrolling, works with what is cached when the
network is slow, and streams in a sensible quality on mobile data.

## Features

- **Home**: playlists grouped into *Charts*, *Für dich* (smart playlists) and *Meine Playlists*, with a "new"
  badge when a playlist's tracks change.
- **Library**: albums (paged grid, sort by recently added / name / artist), artists with letter headers,
  favorites. Artist pages with "shuffle all", top songs, biography and similar artists (from Last.fm via the
  server).
- **Search**: local results instantly, server results after a short debounce.
- **Player**: mini player plus a full player with a waveform seek bar, queue sheet, shuffle / repeat, favorite.
  Drag down or use predictive back to close.
- **Look**: black, monochrome and one red accent, after the "Dotwave × NothingOS" design: Space Grotesk for text,
  Doto (dot matrix) for titles and numbers, Space Mono for small caps labels. Dark only.
- **Playback** (Media3 `MediaLibraryService`): notification and lock screen controls, Bluetooth, queue and
  position restored after restart, 500 MB audio cache.
- **Streaming quality**: separate presets for Wi-Fi and mobile data (Original, MP3 320, Opus 192/128/96).
  Seeking works in transcoded streams too (see below).
- **Volume levelling** from the files' ReplayGain tags: per song, per album, or automatic (album gain while the
  neighbouring songs are from the same album). It only turns songs down; untagged songs play at full volume.
- **Instant mix**: a radio from an artist (artist page) or from the playing song (full player), built from the
  server's similar songs. Near the end of the queue it adds more songs similar to the one playing.
- **Offline downloads** of albums and playlists (download button next to shuffle, list in Library → Downloads).
  Downloaded playlists stay in sync with the server: new songs are fetched, dropped ones deleted. Own quality
  preset, Wi-Fi only unless allowed otherwise, resumes after interruptions. Downloaded songs play without network;
  songs that aren't downloaded are then skipped.
- **Scrobbling** to the server the way Last.fm counts plays: "now playing" on start, a play after half the track
  or 4 minutes of actual listening.
- **Own playlists**: add songs from any song's menu (or start a new playlist there); rename, reorder, remove
  songs or delete a playlist you created. Imported and smart playlists stay read-only.
- **Home screen widgets**: a 4×2 player, a 2×2 light player with the song's progress in dots, and a 4×2
  media card with the server, favorite heart, elapsed time and a waveform progress bar.
  Play also resumes the last queue when the app isn't running.
- **Settings**: quality presets, volume levelling, downloads, per-playlist group override, pinning and ordering, logout.

UI strings are in English and German.

## Requirements

- Android 8.0 (API 26) or newer. Compiled against API 37.
- A Navidrome (or other Subsonic-compatible) server reachable from the phone.

## Building

The project needs **JDK 21** (AGP doesn't run on newer JDKs yet) and the Android SDK with platform 37.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

./gradlew :app:assembleDebug        # debug build, package dev.gruebling.navi.debug
./gradlew :app:installDebug         # install on a connected device
./gradlew :app:assembleRelease      # minified (R8) release build with the baseline profile
./gradlew testDebugUnitTest         # unit tests
```

Debug and release builds have different package names, so both can be installed side by side, each with its
own login. The release build is currently signed with the debug key; a real signing key is still to do
(`*.jks` / `keystore.properties` are git-ignored for that).

## Project structure

```
app/                  Activity, navigation host, Hilt application
core/
  model/              Plain Kotlin models (Song, Album, Playlist, ids, settings)
  network/            Subsonic API (Retrofit + OkHttp + kotlinx.serialization), auth, stream URLs
  datastore/          Encrypted credentials, streaming settings (DataStore)
  database/           Room database: library cache, playlists, saved queue
  data/               Repositories (cache first, refresh from server), session / login / logout
  playback/           PlaybackService, controller, stream resolver, scrobbler, queue persistence
  navigation/         Navigation 3 routes
  ui/                 Theme and fonts, shared components (tiles, chips, track rows, seek bar), image loading
feature/
  login/  home/  library/  search/  detail/  player/  settings/  addtoplaylist/  widget/
benchmark/            Macrobenchmarks and baseline profile generator
build-logic/          Gradle convention plugins (Android, Compose, Hilt, feature modules)
```

Feature modules depend on `core` only, never on each other. Wiring happens in `app/`.

## How some things work

**Login and credentials.** The app uses Subsonic token auth (`t = md5(password + salt)`, a fresh salt per
request), so the password never goes over the wire. The password is stored encrypted with AES-256-GCM, using a
non-exportable key in the Android Keystore. If the key is lost (app data cleared, device restored), the app
asks for the login again.

**Cache first.** Screens read from Room and refresh from the server in the background, so they show content
right away and still work when the server is slow. Logout clears the database.

**Seeking in transcoded streams.** A transcoded stream has no known length, so ExoPlayer treats it as live and
disables seeking. `TranscodeSeekingPlayer` wraps the player: it enables seeking for the app's streams, restarts
the current item from the server with `timeOffset` when the user seeks, and reports positions with that offset
added. Original-quality streams seek normally.

## Performance

The release build ships a baseline profile (`app/src/release/generated/baselineProfiles/`), so startup and the
main screens are compiled ahead of time on install.

Measured on a Nothing Phone 2a (Android 16), without → with the profile:

| | without | with |
|---|---|---|
| Startup until Home shows data (median) | 940 ms | 774 ms |
| Home scroll, frame CPU time P99 | 17.9 ms | 8.6 ms |
| Album grid scroll, frame CPU time P99 | 8.6 ms | 8.3 ms |

To run the benchmarks or regenerate the profile, connect a phone and **log in once by hand in the release
build** (package `dev.gruebling.navi`). The benchmarks reuse that login: installs keep app data
(`android.injected.androidTest.leaveApksInstalledAfterRun` in `gradle.properties`).

```sh
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   # startup + scroll benchmarks (~10 min)
./gradlew :app:generateReleaseBaselineProfile               # regenerate the profile (~10 min)
```

Results land in `benchmark/build/outputs/connected_android_test_additional_output/` (JSON plus Perfetto
traces). The scroll benchmarks use their own `FrameMetric` instead of `FrameTimingMetric`: on some devices the
on-device trace processor can't name the RenderThread when the app started before the trace, and
`FrameTimingMetric` then fails with "Observed no renderthread slices".

## Development notes

- Debug builds include `DebugPlaybackReceiver` for driving playback from adb, e.g.
  `adb shell am broadcast -n dev.gruebling.navi.debug/dev.gruebling.navi.debug.DebugPlaybackReceiver -a navi.debug.PLAY_PLAYLIST --es name "'FM4 Charts'"`
  (also `TOGGLE`, `NEXT`, `PREVIOUS`, `SEEK`, `STATE`; `STATE` includes the levelling volume and the song's tags). The debug build logs to the logcat tag `NaviProbe`.
- Unit tests cover the API client and the repositories (JUnit, Turbine, Robolectric). Robolectric on JDK 21
  needs extra `--add-opens` / `--add-exports` flags, which the convention plugin sets.
- UI elements that the benchmarks use have test tags, exposed as resource ids (`testTagsAsResourceId`).

## Not done yet

- Android Auto (the browse tree is a stub).
- Release signing key.
- Gapless playback not yet verified with a gapless album.
- Offline mode beyond downloads: covers only show if the image cache still has them. Downloaded playlists are
  synced when the app starts, not periodically in the background.
