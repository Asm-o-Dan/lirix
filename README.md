<h1 align="center">Verselog</h1>

<p align="center">
  <b>Your listening, with the words.</b><br/>
  An Android companion that sees what's playing in <i>any</i> music app and gives you synced lyrics, guitar chords, and a year-round "Wrapped" for it.
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Local first" src="https://img.shields.io/badge/data-stays%20on%20device-A855F7">
  <img alt="Status" src="https://img.shields.io/badge/status-early%20beta-orange">
</p>

<!--
  TODO(before launch): add 3-4 screenshots/GIFs to docs/screenshots/ and embed them here.
  Suggestion: karaoke tab, chords tab, "Wrong lyrics?" undo, Wrapped share card.
  Avoid real album covers / copyrighted lyrics in public images.
-->

## Why

Streaming apps either don't show lyrics, hide them behind a paywall, or show them only inside their own player. Verselog sits *next to* your player instead of replacing it:

- You keep using Spotify, YouTube Music, Yandex Music, VK Music, a local player — whatever.
- Verselog reads the "now playing" metadata Android already exposes and shows lyrics, chords and stats for the current track.
- When the lyrics it found are wrong, **you** can fix it in two taps instead of living with it.

## Features

**Now Playing**
- **Karaoke** — time-synced (LRC) lyrics with tap-to-seek, ±10 s skip, and ±50 ms timing calibration per track.
- **Reading mode** — plain lyrics when no timestamps exist.
- **Chords** — chord sheets with chords kept aligned above the words (monospace), plus transposition.
- **Notes** — your own notes per track.
- Album art, vinyl-style player, transport controls that talk to the playing app.

**"Wrong lyrics?"**
- **✕ Not this text** rejects the current source, instantly cascades to the next one, and offers **Undo**.
- If every source is exhausted you get an honest empty state with *Search the web* and *Reset rejected sources*.
- **Share → Verselog** from your browser: send a link or paste lyrics text and attach it to the current track.
- **Teach mode** — open any lyrics site in the built-in browser, tap the lyrics block(s), and Verselog saves a declarative extraction rule for that site (supports selecting several blocks, e.g. verses + chorus).
- Chords are fetched independently, so you can get synced lyrics from one source and chords from another.

**History, Library, Wrapped**
- Listening history and a searchable library (favourites, tracks with lyrics).
- Wrapped-style stats over selectable timeframes, "obsession" detection, and 26 achievements with tiers.
- Shareable cards in Stories (9:16) and square formats.

## Privacy

- **No account, no analytics SDK, no ads.** Listening history lives in a local Room database on your phone.
- The only network traffic is **lyrics/chords lookups** for the track you're playing (see sources below) and, if you use it, the Teach-mode browser.
- Permissions, and why:

| Permission | Why |
|---|---|
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Read now-playing media metadata from your music app |
| `INTERNET` | Fetch lyrics and chords |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` | Keep the listener alive and show its status |
| `RECEIVE_BOOT_COMPLETED` | Resume tracking after reboot |

## Lyrics sources

Verselog does not ship or host any lyrics. Text is fetched **on your device, on demand** from third-party sources and cached locally. Sources are tried as a cascade and each can be rejected per track:

[LRCLIB](https://lrclib.net) (synced lyrics) · AmDm (chords) · Textpesni · Vse-pesni · Amalgama · Genius · LyricFind · your own custom rules

> **Heads-up:** lyrics are copyrighted and some sources restrict automated access. You are responsible for complying with each source's terms and the law in your country. If you are a rights holder or site owner and want a source removed, open an issue.

## Build

Requirements: JDK 17+ and the Android SDK (Android Studio is the easiest way).

```bash
./gradlew assembleDebug          # APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # 140+ unit tests
```

Then enable **Settings → Notifications → Device & app notifications → Verselog** (wording varies by vendor) so it can see what's playing.

## Project status

Early beta, one developer. Debug builds only so far; no store release yet. Expect rough edges — bug reports with the track name and the app you were playing from are the most useful thing you can send.

## How it's built

Kotlin, Jetpack Compose, Room (schema-versioned migrations with migration tests), coroutines/Flow. Developed spec-first: each feature starts as a written spec, gets failing tests, then an implementation — which is why the unit-test suite is unusually large for a project this size.

## Roadmap

- [ ] Rename the Android application id and publish a signed release
- [ ] Community-shared site rules (so Teach mode fixes help everyone)
- [ ] More chord sources
- [ ] Localization beyond English/Russian
- [ ] Optional encrypted backup/export of your history

## Contributing

Issues and PRs are welcome. Please open an issue first for anything bigger than a bug fix.

---

### По-русски

**Verselog** — Android-приложение, которое «слушает» что играет в *любом* плеере (Spotify, YouTube Music, Яндекс Музыка, VK Музыка…) и показывает синхронный текст (караоке), аккорды с транспонированием, заметки и годовой «Wrapped» по прослушиванию. Если текст не тот — нажмите **«✕ Не тот текст»**: приложение переберёт следующий источник, а действие можно отменить. Данные хранятся только на устройстве, аккаунта нет. Проект на ранней стадии (beta), сборка — `./gradlew assembleDebug`.
