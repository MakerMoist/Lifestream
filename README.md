# Lifestream

A standalone Android music player for Final Fantasy soundtracks. Audio is streamed straight from YouTube's media servers, but everything else — the catalog, search, queue, player engine, notification and lock-screen controls — is Lifestream's own. There is no YouTube app, embedded web player, Spotify SDK, or API key involved.

## Features

- **Soundtrack catalog** — 26 soundtracks, each with its own original card art. Main series: I–XVI with X-2 and XIII-2, plus VII Remake, INTERmission and Rebirth right after VII. Spin-offs & more: Tactics, Crisis Core –Reunion–, Chrono Trigger, Chrono Cross, Lost Odyssey. Tapping one opens its original soundtrack track list.
- **Multi-select**: press and hold a soundtrack, tap more (or "Select all" for a section), then Play or Shuffle them as one queue.
- **Official soundtracks first**: each game plays its official original soundtrack album(s) — no arrangements, covers or spin-off albums, and originals before remasters/remakes. Where no official album exists on YouTube Music (currently X-2, Crisis Core –Reunion– and Lost Odyssey) it uses the most complete upload of the game's own soundtrack, with track names cleaned up.
- **Extra songs**: a game can list songs that aren't on its soundtrack album — e.g. Leona Lewis's "My Hands", XIII's Western theme song — found as the original recording (no covers, live or karaoke versions).
- **Search inside a soundtrack**: every game page has its own search box.
- **English titles**: many official albums only carry Japanese track names. A built-in glossary maps Final Fantasy names and well-known titles to their official English (魔晄炉 → Mako Reactor, 片翼の天使 → One-Winged Angel), and anything left is translated on the device (Google ML Kit, a one-time ~30 MB model download).
- **One version per song**: remasters, piano/orchestral takes and duplicates collapse to a single entry (the original soundtrack's version wins).
- **Strict search**: searches only the songs in Lifestream's own soundtracks (an on-device index built in the background) and the catalog's game names — e.g. "Prelude", "FF7", "Rebirth". Nothing comes from open YouTube search.
- **Own player** built on Media3 ExoPlayer: background playback, media notification, lock-screen and headset controls, audio focus, pause on headphone unplug.
- **Now Playing** screen: swipe the album art left or right to skip or go back, plus seek bar, shuffle, repeat (all / one), favorite, and an editable queue.
- **Queue tools** on every track: play next, add to queue, add to favorites.
- **Library**: favorites and listening history, saved on the device.
- **Recovery**: expired or rejected stream URLs are re-resolved and retried automatically.

## How streaming works

1. `MusicRepository` uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) to search YouTube Music. A game's tracks come from its official soundtrack albums: album titles are checked against that specific game by `Matching` (so VII never picks up Remake or Rebirth, X never picks up X-2, and covers, lo-fi and similar filler are dropped), with the original soundtrack ranked first. Song search is only a filtered fallback.
2. Each queued track is just `lifestream://track/<videoId>`. Right before it plays, `StreamResolver` asks the extractor for the best original-language audio-only stream and caches the URL until it expires.
3. `YouTubeStreamDataSource` downloads that audio in 1 MiB chunks using the same `range`/`rn` request style YouTube's own clients use, which avoids throttling. ExoPlayer sees one continuous stream.

## Getting the APK

**Option A — GitHub (no tools needed):** push this folder to a GitHub repository. The included workflow (`.github/workflows/build.yml`) builds a non-debuggable release `Lifestream.apk` (plus its SHA-256 checksum) on every push to `main` and attaches it to a release called `latest`. Download it on your phone and install it (allow "install unknown apps" when asked).

Releases are signed with Lifestream's own key, so each update installs over the last. The key is committed only encrypted (`app/signing/release.jks.enc`, AES-256) and is decrypted in CI with the repository secrets `LIFESTREAM_KEYSTORE_PASSWORD`, `LIFESTREAM_KEY_PASSWORD` and `LIFESTREAM_KEY_ALIAS`. Plain key files are git-ignored.

**Option B — Android Studio:** open this folder, let Gradle sync, then Run, or use Build › Build APK(s).

**Option C — command line** (JDK 17 and the Android SDK installed): `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

Requires Android 8.0 (API 26) or newer.

## If playback stops working

YouTube changes its internals from time to time. When that happens, bump the NewPipeExtractor version in `app/build.gradle.kts` to the newest release from its [releases page](https://github.com/TeamNewPipe/NewPipeExtractor/releases) and rebuild.

## Security

- The playback service only accepts connections from Lifestream itself and trusted system controllers (System UI media controls, Bluetooth/car via the platform); other apps are rejected.
- Track ids are validated as YouTube video ids before they're used in any URL; queued items can only point at Lifestream's own `lifestream://` tracks, and artwork must be a plain HTTPS URL.
- Audio is fetched over HTTPS only, from `*.googlevideo.com` only, and redirects can't downgrade to HTTP. Cleartext traffic is disabled app-wide and only system CAs are trusted.
- Favorites, history and the soundtrack index stay on the device (no cloud backup or device-to-device transfer).
- CI runs with read-only tokens; only the publish job (pushes to `main`) can write, using the `gh` CLI rather than third-party actions. Workflow inputs are passed as data and validated.

## Card art

The illustrations in `app/src/main/res/drawable-nodpi/art_*.webp` are original, procedurally painted scenes (no game artwork, logos or characters). Each one is built around that game's signature setting (VII's reactor-ringed plate city under Meteor, X's water-sphere arena, XIII's crystal pillar beneath the shell-world, XIV's cracked red moon, …). They're generated by `tools/art/scenes2.py` (shapes and helpers in `kit.py` / `scenes.py`); edit a scene and run `python3 tools/art/scenes2.py <id>` to re-render it.

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
tools/art/                    card-art generator (Python, numpy + Pillow)
```
