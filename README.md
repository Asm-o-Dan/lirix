<h1 align="center">Lirix</h1>

<p align="center">
  <img src="./docs/icon.png" width="128" alt="Lirix Logo" /><br/>
  <b>Synced Karaoke & Guitar Chords for Any Music Player on Android.</b><br/>
  Lightweight (~19 MB), privacy-first companion capturing media from Spotify, Yandex Music, VK Music, YouTube ReVanced, Telegram, AIMP, and local files.
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Version 1.5.0" src="https://img.shields.io/badge/Version-1.5.0-blue">
  <img alt="License: GPL v3" src="https://img.shields.io/badge/License-GPLv3-blue.svg">
  <img alt="Privacy: 100% on device" src="https://img.shields.io/badge/privacy-100%25%20on%20device-A855F7">
  <img alt="APK Size" src="https://img.shields.io/badge/APK%20Size-~19%20MB-success">
</p>

---

## ⚡ Highlights

Streaming services frequently gate synchronized lyrics behind subscriptions, lack accurate timing, or omit guitar chords altogether. **Lirix** captures playback from system notifications and provides a dedicated prompter:

- **Karaoke & Synced Lyrics** — Time-synced LRC prompter with tap-to-seek, ±10 s jump controls, millisecond calibration, and live search progress indicator.
- **Guitar Chords with Smart Wrap** — Monospace sheets with tempo-adjusted autoscroll, interactive fingering diagrams (`GuitarChordDictionary`), natural semitone transpose (`[♭] Key [♯]`), and word-boundary syllabic pair wrapping preventing horizontal truncation.
- **Tap-to-Sync Studio** — Built-in 5-step studio to timestamp unsynced lyrics in real-time with ±100 ms micro-adjustments and local database storage.
- **Dynamic 6-Palette Theme Engine** — Real-time switching between Spotify Mood, Yandex Music, Obsidian Pulse, Pastel Serenity, Graphite & Chalk, and Paper & Ink with automated WCAG contrast clamping.
- **Floating Lyrics Overlay** — Compact floating bubble (`SYSTEM_ALERT_WINDOW`) displaying current lyrics line directly over games, navigation, or third-party apps.
- **Interactive Teach Mode** — In-app visual web inspector. Select lyrics or chord blocks from any webpage to save custom DOM extraction rules for future tracks.
- **Offline History & Music Wrapped** — Room v12 database tracking play counts, listening streaks, favorite songs, and shareable 9:16 Stories cards locally without remote accounts.
- **Zero Ads, Zero Trackers, Zero Telemetry** — 100% offline-first. No third-party analytics SDKs.

---

## 🗺️ Roadmap: Version 2.0.0

- **Lirix Desktop (Windows 10/11)**: Standalone Compose Multiplatform client capturing system audio via native WASAPI Loopback bridge without virtual cables.
- **Lirix Connect**: Direct peer-to-peer device pairing over Iroh QUIC with Ed25519 authentication for queue Handoff and real-time audio casting to PC.

---

## 🔒 Permissions & Privacy

| Permission | Technical Justification |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Reads now-playing playback metadata (artist, title, player state) directly from Android MediaSession notifications. |
| `INTERNET` | Fetches lyrics and chords on-demand from public web providers ([LRCLIB](https://lrclib.net), [AmDm.ru](https://amdm.ru)). |
| `SYSTEM_ALERT_WINDOW` | Renders the optional floating lyrics overlay bubble over active applications. |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` | Maintains persistent background capture service without OS killer eviction. |
| `RECEIVE_BOOT_COMPLETED` | Restores background notification listener after device reboot. |

All playback history, favorite tracks, notes, and scraper rules remain exclusively in a local SQLite database (`app_database.db`) on your device.

---

## ⚖️ Legal Disclaimer & Terms of Use

1. **Clean Client / Automated Viewer**: Lirix is an independent open-source client utility and does not host, cache, store, scrape in bulk, index, or distribute copyrighted music recordings, lyrics, or chord sheets on any server. All queries are performed **locally and on-demand** by the client device directly from publicly accessible web endpoints.
2. **Personal & Educational Use**: Provided strictly for personal interoperability, accessibility, and educational purposes under the principles of fair use. Users are responsible for compliance with third-party service terms and local copyright legislation.
3. **Non-Affiliation**: Lirix is not affiliated with, endorsed by, or associated with LRCLIB, AmDm, Spotify, Yandex Music, VK, Google, or any artist or record label. All artist names, track titles, and trademarks are property of their respective owners.
4. **Warranty & Liability**: The software is provided "AS IS", without warranty of any kind. Under no circumstances shall the author(s) or copyright holders be liable for any claims or damages (see [LICENSE](LICENSE) GPLv3).
5. **Takedown Requests**: To request removal of a default provider endpoint, contact `dgandapas1@gmail.com` or submit an issue on GitHub.

---

## 🛠️ Building from Source

Requirements:
- JDK 17 or JDK 21 (Eclipse Temurin recommended)
- Android SDK (API 35, Build-Tools 35.0.0)

```bash
# Clone the repository
git clone https://github.com/Asm-o-Dan/lirix.git
cd lirix

# Build release APK
./gradlew :app:assembleRelease

# Run test suite (160+ unit tests)
./gradlew :shared:desktopTest :app:testDebugUnitTest
```

Compiled APK path: `app/build/outputs/apk/release/app-release-unsigned.apk` (or `app-debug.apk` for development).

---

## 🇷🇺 По-русски

**Lirix («Лирикс»)** — легковесный (~19 МБ) open-source плеер-компаньон для Android. Перехватывает трек из уведомлений любого плеера (**VK Музыка, Яндекс Музыка, Spotify, YouTube ReVanced, Telegram, AIMP**) и открывает:

1. **Синхронное караоке**: точный построчный скролл, перемотка по клику на строчку, калибровка таймингов и экран статуса поиска.
2. **Аккорды с автоскроллом и транспонированием**: сетки аппликатур из словаря, транспонирование тональностей полутонами (`[♭] Key [♯]`), умный перенос пар аккордов по границам слов без обрезания строк.
3. **Студия разметки Tap-to-Sync**: встроенный инструмент расстановки таймкодов песни с точностью до 100 мс в 5 шагов.
4. **6 динамических тем**: Spotify, Яндекс Музыка, Obsidian, Пастель, Графит и Бумага со строгим контролем контрастности WCAG.
5. **Плавающий оверлей**: вывод текущей строки песни поверх игр, навигаторов и других приложений.
6. **Приватность и оффлайн**: без рекламы, без аккаунтов, без сетевой телеметрии. Вся история и кэш хранятся в локальной базе Room v12.

### Установка:
1. Скачайте свежий APK из раздела [Releases](https://github.com/Asm-o-Dan/lirix/releases/tag/v1.5.0).
2. Разрешите доступ к уведомлениям: **Настройки → Приложения → Специальный доступ → Доступ к уведомлениям → Lirix**.
3. Запустите музыку в любом плеере — откройте Lirix и текст подтянется автоматически.

---

## 📄 License

Lirix is licensed under the [GNU General Public License v3.0](LICENSE).

## Song translations / Переводы песен

Automatic translation discovery supports Russian, English, Spanish, Portuguese and Chinese through Amalgama and LyricsTranslate. Saved translations remain available offline.

Перед переустановкой APK с другой подписью сохраните важные данные. В [заметках к Android 1.5.0](.sdd/releases/v1.5.0.md) описано ограничение обновления с опубликованной 1.4.0.

## Project documentation

See the [documentation index](docs/README.md) for architecture, UI design and archived planning reports. Download packaged applications from [GitHub Releases](https://github.com/Asm-o-Dan/lirix/releases).
