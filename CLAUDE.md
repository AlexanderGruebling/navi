# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Dotwave (code name and package: Navi, `dev.gruebling.navi`) is a native Android client (Kotlin, Jetpack Compose, Media3) for Navidrome and other Subsonic/OpenSubsonic servers. `README.md` covers the features, the benchmark numbers and the "not done yet" list.

## Commands

AGP needs **JDK 21**. Newer JDKs don't work.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

./gradlew :app:assembleDebug                 # package dev.gruebling.navi.debug
./gradlew :app:installDebug
./gradlew :app:assembleRelease               # R8 + baseline profile, package dev.gruebling.navi (signed with debug key)
./gradlew testDebugUnitTest                  # all unit tests
./gradlew :core:data:testDebugUnitTest --tests "dev.gruebling.navi.core.data.repository.AlbumRepositoryTest"
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   # needs a device, ~10 min
./gradlew :app:generateReleaseBaselineProfile               # needs a device, ~10 min
```

Benchmarks and profile generation reuse a login made **by hand in the release build**. Installs keep app data (`android.injected.androidTest.leaveApksInstalledAfterRun` in `gradle.properties`), so don't remove that setting. Results go to `benchmark/build/outputs/connected_android_test_additional_output/`.

There is no lint, ktlint, detekt or spotless setup.

`adb` is not on `PATH`; use `~/Android/Sdk/platform-tools/adb`. The emulator setup and adb playback controls are in the `device-debugging` skill.

## Architecture

**Modules.** The `app/` module holds the Activity, the Navigation 3 host (`app/.../ui/NaviApp.kt`) and the Hilt application. `core/*` holds shared layers and `feature/*` holds the screens. Feature modules depend on `core` only, never on each other. All wiring between screens happens in `NaviApp.kt` through callbacks such as `onBack` and open-album lambdas. The one exception is "Add to playlist": `SongActionsSheet` is used on many screens, so `NaviApp` provides `LocalAddToPlaylist` (core:ui) once and shows the `feature:addtoplaylist` picker itself.

**Convention plugins** (`build-logic/convention`) set up every module: `navi.android.library`, `navi.android.compose`, `navi.hilt`, `navi.android.feature` and `navi.jvm.library`. `navi.android.feature` already pulls in `core:model`, `core:navigation`, `core:ui`, lifecycle/ViewModel/Hilt-Compose, navigation3 runtime and immutable collections. A new feature module only adds what it needs beyond that, usually `core:data` and/or `core:playback`. Versions live in `gradle/libs.versions.toml`.

**Navigation.** Navigation 3 uses `@Serializable` `NavKey` routes in `core/navigation/Routes.kt`. There are three top-level tabs (Home, Search, Library), and each keeps its own back stack (`NaviAppState`).

**Data flow (cache first).**
- `core:network`: Retrofit Subsonic API, token auth (`md5(password + salt)` with a fresh salt per request) and stream/cover URL building.
- `core:database`: the Room cache.
- `core:datastore`: credentials and settings. The password is encrypted with AES-256-GCM using an Android Keystore key. If the key is lost, the user has to log in again.
- `core:data`: repositories. Screens observe Room flows while the repositories refresh from the server in the background.
- Use `suspendRunCatching` (`core/data/util/Results.kt`) instead of `runCatching`, so coroutine cancellation is not swallowed.
- The Room DB uses `fallbackToDestructiveMigration`, so a schema change only needs a version bump. Schemas are exported to `core/database/schemas/`. Room is a cache only: anything the user sets lives in DataStore (`PlaylistSettingsStore`, `DownloadSettingsStore`), or a schema change wipes it.
- Logout is `core:playback`'s `SignOut`. It stops playback, clears the DB, then deletes the credentials, and that last step switches the UI to login.

**Playback** (`core:playback`):
- `PlaybackService` is a Media3 `MediaLibraryService`.
- The UI talks to it only through `PlaybackController`, a lazily connected `MediaController` whose state is mirrored into a `StateFlow`. Commands are fire-and-forget.
- Media items carry `navi://stream/<songId>` and `navi://cover/<id>` URIs (`NaviUris`), never real URLs. `StreamResolver` turns them into authenticated URLs when they are opened, using the current quality preset (Wi-Fi vs. mobile data). Its cache key has no auth parameters. As a result, persisted queues (`QueuePersistence`) never hold credentials.
- `LibraryCallback` fills in URIs for items that controllers send and resolves bare ids from the cache. The Android Auto browse tree is a stub.
- `TranscodeSeekingPlayer` wraps ExoPlayer because transcoded streams have no known length, so ExoPlayer treats them as live. On seek it restarts the item with the `timeOffset` stream parameter (needs the server's OpenSubsonic `transcodeOffset` extension, checked by `ServerFeatures`; without it transcoded songs are not seekable) and adds that offset to the positions it reports.
- Offline downloads live in `core:download`: the downloaded albums/playlists in `DownloadSettingsStore` (DataStore, so Room's destructive migrations don't lose them), the songs as files in `filesDir/downloads/<songId>` (`DownloadFiles`; a file that exists is downloaded). `DownloadWorker` (WorkManager, Hilt worker factory set up in `NaviApplication`) runs `DownloadSync`, which only deletes unneeded files when every target was reloaded from the server. `StreamResolver` serves a downloaded file instead of the stream. Without network, `OfflineSkipper` jumps over songs that aren't downloaded.
- `Scrobbler` sends "now playing" on start. It submits a play after half the track or 4 minutes of actual listening.

**Look.** The UI follows the "Dotwave × NothingOS" design (a Claude Design canvas): dark only, no dynamic color.
- Colors: `NaviColors` (`core/ui/.../theme/Color.kt`), with `#D71921` as the only accent.
- Text: Space Grotesk via the Material type scale. Use `NaviType.dotTitle`/`dotNumber` (Doto) for screen titles and big numbers, and `MonoLabel` (Space Mono caps) for small labels.
- Fonts are bundled in `core/ui/src/main/res/font`, with their OFL licences in `assets/licenses`.
- Prefer the shared pieces in `core/ui/.../component/Nothing.kt` (`ScreenTitle`, `NaviChip`, `AccentPlayButton`, `StatusLabel`, `ScreenPadding` = 20 dp) over raw Material components.
- Icons are 1.75-stroke vectors on a 24 grid. Favorites are shown as a heart.
- Brand: name `app_name` lives in `core:ui` (not translated). The launcher icon (`app/.../drawable/ic_launcher_*.xml`) and `DotwaveLogo` draw the same 9×9 dot waveform; keep them in sync.

**Compose stability.** `compose-stability.conf` marks `core.model.*`, `core.playback.model.*`, `kotlin.time` types and immutable collections as stable. Keep domain models immutable data classes, and use `kotlinx.collections.immutable` for lists in UI state.

## Testing

- Unit tests (JUnit, Turbine, Robolectric) cover `core:network`, `core:datastore` and `core:data`.
- Repository tests use `FakeSubsonicServer` (MockWebServer) and `TestDatabase` from `core/data/src/test/.../testing/`.
- Robolectric on JDK 21 needs extra `--add-opens`/`--add-exports` flags. The convention plugin sets them.
- UI elements that the macrobenchmarks use have test tags, exposed as resource ids (`testTagsAsResourceId`). Keep them when you change those screens.

## Strings

UI strings exist in English and German. Add every new string to both.
