<h1 align="center">Lirix</h1>

<p align="center">
  <img src="./docs/icon.png" width="128" alt="Lirix Logo" /><br/>
  <b>Words & Chords for Any Music Player on Android.</b><br/>
  An open-source Android companion that captures what's playing in <i>any</i> player (Spotify, VK Music, Yandex Music, YouTube ReVanced, Telegram, local MP3s) and gives you synced karaoke lyrics, guitar chords with smart autoscroll, and offline listening history.
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="License: GPL v3" src="https://img.shields.io/badge/License-GPLv3-blue.svg">
  <img alt="Local first" src="https://img.shields.io/badge/privacy-100%25%20on%20device-A855F7">
  <img alt="APK Size" src="https://img.shields.io/badge/APK%20Size-~10%20MB-success">
</p>

---

## ⚡ Highlights

Streaming apps in CIS and worldwide either lack synchronized lyrics, hide them behind subscriptions, or don't provide guitar chords at all. **Lirix** runs seamlessly alongside your favourite player:

- **Karaoke & Synced Lyrics** — Time-synced LRC display with tap-to-seek, ±10 s jumps, and ±50 ms timing calibration.
- **Guitar Chords with Smart Autoscroll** — Monospace chord sheets with tempo control (`[-] 1.00x [+]`, `[▶ / ⏸]`, reset), adaptively calculated based on track duration and chord length so you never have to scroll with dirty fingers while playing.
- **Smart Cascade & Instant Fix** — Not the right lyrics? Tap **✕ Not this text** to reject and cascade to the next source, or use **Undo**.
- **Interactive Teach Mode** — Built-in visual inspector. Open any lyrics or chords webpage in the app, switch between *Surfing* and *Inspector*, select multiple text blocks, and Lirix automatically creates an extraction rule for future tracks.
- **Listening History & Offline Wrapped** — Track stats, streaks, obsession detection, and shareable 9:16 Stories cards saved locally on your phone.
- **Zero Ads, Zero Trackers, Zero Accounts** — 100% offline-first Room database. No analytics SDKs.

---

## 🔒 Permissions & Privacy

| Permission | Why it's needed |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Reads now-playing media metadata (artist, title, player state) from your music player notifications. |
| `INTERNET` | Fetches lyrics and chords on-demand from public web providers (LRCLIB, AmDm, etc.). |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` | Keeps the background notification listener active and shows current status. |
| `RECEIVE_BOOT_COMPLETED` | Resumes listening service after device restart. |

All your listening history, favorite songs, notes, and scraper rules remain strictly in a local SQLite database on your device.

---

## ⚖️ Legal Disclaimer & Terms of Use

> **PLEASE READ CAREFULLY BEFORE USE OR CONTRIBUTION.**

1. **Clean Client / Automated Viewer**: Lirix is an independent open-source client utility and does not host, cache, store, scrape in bulk, index, or distribute any copyrighted music recordings, lyrics text, or chord sheets on any server. All queries are performed **locally and on-demand** by the user's client device directly from publicly accessible web endpoints (e.g., [LRCLIB](https://lrclib.net), [AmDm.ru](https://amdm.ru)).
2. **Personal & Educational Use**: This software is provided strictly for personal interoperability, accessibility, and educational purposes under the principles of fair use. Users are individually responsible for ensuring their usage complies with third-party service terms and local copyright laws.
3. **Non-Affiliation**: Lirix is not affiliated with, endorsed by, or associated with LRCLIB, AmDm, Spotify, Yandex Music, VK, Google, or any artist or record label. All artist names, track titles, and trademarks are the property of their respective owners.
4. **Warranty & Liability**: The software is provided "AS IS", without warranty of any kind. Under no circumstances shall the author(s) or copyright holders be held liable for any damages or legal claims arising from the use or distribution of this software (see [LICENSE](LICENSE) GNU General Public License v3.0).
5. **Takedown & Removal Requests**: If you are a copyright holder or webmaster and wish to request the removal of a specific default provider endpoint, please contact `dgandapas1@gmail.com` or open an issue on GitHub.

---

## 🛠️ Building from Source

Requirements:
- JDK 17 or JDK 21 (Eclipse Temurin recommended)
- Android SDK (API 35)

```bash
# Clone the repository
git clone https://github.com/Asm-o-Dan/lirix.git
cd lirix

# Build debug APK (~10 MB)
./gradlew assembleDebug

# Run unit test suite (150+ tests)
./gradlew testDebugUnitTest
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🇷🇺 По-русски

**Lirix («Лирикс»)** — легковесный (~10 МБ) open-source компаньон для Android, который перехватывает трек из уведомлений любого плеера (**VK Музыка, Яндекс Музыка, Spotify, YouTube ReVanced, Telegram, AIMP, Poweramp**) и в один тап открывает:

1. **Синхронное караоке**: подстрочник со скроллом, перемоткой по тапу и ручной калибровкой задержки (±50 мс).
2. **Аккорды с автоскроллом**: табулатуры и аккордовые сетки с AmDm с умным расчётом скорости под темп трека, ручным регулятором темпа (`[-] 1.00x [+]`) и паузой при касании пальцем экрана — идеально для игры на гитаре.
3. **Визуальный «Teach Mode»**: если песни нет в базе, можно открыть страницу в браузере приложения, выделить нужные блоки текста (куплеты, припев), и Lirix сохранит селектор для сайта.
4. **Приватность и оффлайн**: без рекламы, без аккаунтов, без телеметрии. База данных SQLite/Room хранится только на телефоне.

### Установка:
1. Скачайте свежий APK из раздела [Releases](https://github.com/Asm-o-Dan/lirix/releases).
2. Разрешите доступ к уведомлениям: **Настройки → Приложения → Специальный доступ → Доступ к уведомлениям → Lirix**.
3. Запустите музыку в любом плеере — откройте Lirix и наслаждайтесь словами и аккордами!

---

## 📄 License

Lirix is licensed under the [GNU General Public License v3.0](LICENSE).
