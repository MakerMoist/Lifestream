# Lifestream

A standalone Android music player for Final Fantasy soundtracks. Audio is streamed straight from YouTube's media servers, but everything else — the catalog, search, queue, player engine, notification and lock-screen controls — is Lifestream's own. There is no YouTube app, embedded web player, Spotify SDK, or API key involved.

## Features

- **Soundtrack catalog** — 25 games and collections in three sections: the VII Remake series (Remake, INTERmission, Rebirth), the main series I–XVI including X-2, and spin-offs (Tactics, Crisis Core and Reunion, Distant Worlds, Piano Collections), each with a Tracks tab and an Albums tab.
- **Search** with an optional "Final Fantasy only" filter, songs and albums tabs, and quick-pick suggestions.
- **Own player** built on Media3 ExoPlayer: background playback, media notification, lock-screen and headset controls, audio focus, pause on headphone unplug.
- **Now Playing** screen with seek bar, shuffle, repeat (all / one), favorite, and an editable queue.
- **Queue tools** on every track: play next, add to queue, add to favorites.
- **Library**: favorites and listening history, saved on the device.
- **Recovery**: expired or rejected stream URLs are re-resolved and retried automatically.

## How streaming works

1. `MusicRepository` uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) to search YouTube Music (songs and albums), falling back to regular YouTube search.
2. Each queued track is just `lifestream://track/<videoId>`. Right before it plays, `StreamResolver` asks the extractor for the best original-language audio-only stream and caches the URL until it expires.
3. `YouTubeStreamDataSource` downloads that audio in 1 MiB chunks using the same `range`/`rn` request style YouTube's own clients use, which avoids throttling. ExoPlayer sees one continuous stream.

## Getting the APK

**Option A — GitHub (no tools needed):** push this folder to a GitHub repository. The included workflow (`.github/workflows/build.yml`) builds `app-debug.apk` on every push and attaches it to a release called `latest`. Download it on your phone and install it (allow "install unknown apps" when asked).

**Option B — Android Studio:** open this folder, let Gradle sync, then Run, or use Build › Build APK(s).

**Option C — command line** (JDK 17 and the Android SDK installed): `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

Requires Android 8.0 (API 26) or newer.

## If playback stops working

YouTube changes its internals from time to time. When that happens, bump the NewPipeExtractor version in `app/build.gradle.kts` to the newest release from its [releases page](https://github.com/TeamNewPipe/NewPipeExtractor/releases) and rebuild.

## Notes

- Streaming YouTube content outside YouTube's own apps isn't permitted by YouTube's Terms of Service. This app is intended for personal use.
- "Final Fantasy" is a trademark of Square Enix. Lifestream is an unofficial fan project and uses no Square Enix artwork or logos; album art comes from YouTube at runtime.

## Project layout

```
app/src/main/java/com/lifestream/player/
├── LifestreamApp.kt          app init (extractor + library)
├── MainActivity.kt           navigation, bottom bar, mini player, now-playing overlay
├── net/Http.kt               OkHttp client + extractor downloader
├── data/                     catalog, YouTube repository, favorites/history
├── playback/                 resolver, chunked data source, media session service, UI connection
└── ui/                       theme, shared components, screens
```
