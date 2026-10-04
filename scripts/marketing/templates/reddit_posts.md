# Reddit Promotional Package for Lirix ({version})

---

## 1. r/androidapps

**Title:**  
[DEV] Lirix — An open-source lyrics & chords companion that floats over Spotify, VK, and YouTube (Zero ads, offline-first)

**Post Body:**  
Hey r/androidapps!

A common frustration when listening to music on Android is trying to follow lyrics or play chords along with your favorite tracks. You usually have to split your screen, pay for Spotify Premium, or deal with ad-heavy lyric apps that track your data.

I built **Lirix**, a 100% open-source, AMOLED-black music companion for Android.

### Key Highlights:
* 🎯 **Floating Lyrics Overlay (`SYSTEM_ALERT_WINDOW`):** A draggable, magnetic snap widget that stays on top of Spotify, YouTube, or your local player. Tap to expand into full synced karaoke / chords.
* 🎸 **Musician Suite:** Automatic chord detection above lyrics, live transposition (`[-1] [0] [+1]`), tempo-based autoscroll, and interactive guitar fretboard diagrams.
* 💿 **Collapsible Vinyl & Kinetic Tonearm:** Sleek procedural vinyl deck built on Jetpack Compose Canvas. Tap once to collapse it and gain +200dp of space for chords.
* 🔒 **Zero Telemetry / Offline First:** No analytics, no ads, no account required. All lyrics and chords are cached in local SQLite Room for instant offline access.
* 🌐 **Community Scraper Rules:** Built-in web inspector (TeachMode) that lets you visually extract lyrics from any website and export/import rules as portable JSON.

The project is completely free and licensed under MIT.

* **GitHub:** https://github.com/Asm-o-Dan/lirix
* **APK Download:** Available under Releases on GitHub

Would love to hear your thoughts, feedback, and feature requests!

---

## 2. r/opensource / r/fossdroid

**Title:**  
Lirix: An offline-first, telemetry-free lyrics & chord companion for Android written in Kotlin & Jetpack Compose

**Post Body:**  
Hi everyone,

Sharing **Lirix** — an open-source Android utility designed to display synchronized LRC lyrics and guitar chords for any music playing on your device.

### Tech Stack & Features:
* **Architecture:** Kotlin, Jetpack Compose, Room SQLite, MediaSessionCompat.
* **Non-intrusive:** Detects playing tracks via standard Android `MediaSession` APIs without root or proprietary hooks.
* **Floating Window:** Low-overhead foreground service displaying synced lyrics over any media player with magnetic edge-snapping physics.
* **Offline-First:** All fetched lyrics and chord sheets are stored locally in Room.
* **No Trackers:** 0 advertising SDKs, 0 analytics frameworks.

Check out the source code and pre-compiled APKs:  
GitHub: https://github.com/Asm-o-Dan/lirix

Feedback and contributions are very welcome!

---

## 3. r/guitar

**Title:**  
I built a free tool that shows guitar chords & fretboard fingering diagrams right over Spotify / YouTube on Android

**Post Body:**  
Hey r/guitar!

When practicing songs by ear or playing along with backing tracks, constantly tabbing out between Spotify and chord sheets was driving me crazy.

So I wrote an Android app called **Lirix** that automatically detects what track is playing on your phone and overlays the chords:
* **Live Transposition:** Change pitch up/down by semitones in real-time.
* **Fretboard Diagrams:** Tap any chord name (e.g., Bm7b5, F#m) to open a visual fretboard chart showing exact finger positions.
* **Floating Window:** Runs on top of Spotify or YouTube so you don't lose your playback controls.
* **Auto-Scroll:** Scrolls the chords automatically so you don't have to take your hands off the guitar neck.

Completely free, open-source, and has zero ads.

GitHub repo: https://github.com/Asm-o-Dan/lirix

Hope it helps your practice sessions!
