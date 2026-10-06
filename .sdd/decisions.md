# SDD Decisions Log

## ADR-001: Adoption of Spec Driven Design (SDD) Workflow
- **Date:** 2026-09-26
- **Status:** APPROVED
- **Context:** Transition project governance to strict Spec Driven Design model.
- **Decision:** All features must proceed through intake, architecture, specification, contract sync, test design, single-function implementation, and review gates.
- **Consequences:** Coordinator does not read/write product code directly; tasks are partitioned to 1 function per task.

## ADR-002: Pivot and Descope to Pure Music & Lyrics Hub with Gamified Wrapped
- **Date:** 2026-10-01
- **Status:** APPROVED (Gate 0 Passed via /grill-me)
- **Context:** The user explicitly requested to completely narrow the application scope to media/music playback tracking and lyrics fetching. Eliminate all financial, banking, study, and non-media features. Everything not related to media must be ignored.
- **Decisions (agreed in /grill-me):**
  1. **Ingestion & DB Clean Slate:** Drop all non-media notifications at `NotificationListener` ingress. Purge finance/study tables from Room; retain only music & lyrics schema (`music_tracks`, `music_listening_sessions`, `lyrics_cache`, `achievements`).
  2. **Legacy Code Archive:** Create git branch `legacy-full-engine` to freeze the prior multi-domain implementation, then delete dead finance/study code from master/main.
  3. **Media Sources:** Ingest any audio/video with identifiable (Artist - Track) metadata (Spotify, Yandex Music, VK, YouTube Music, ReVanced, AIMP, audiobooks).
  4. **Network-Aware Lyrics Engine:** Automatic background lyrics pre-fetching and caching when on Wi-Fi; on-demand (lazy) when on cellular mobile data. Supports synced LRC karaoke, plain lyrics, and AmDm guitar chords.
  5. **4-Tab AMOLED UI:**
     - Tab 1: 🎵 «Сейчас играет» (Player, cover art, synced LRC karaoke, AmDm chords).
     - Tab 2: 📜 «История» (Chronological stream of listened tracks with search and play counters).
     - Tab 3: 📚 «Библиотека» (Offline cached lyrics, favorites, artist catalogue).
     - Tab 4: 🏆 «Итоги & Ачивки» (Music Wrapped stats: total time, top artists, listening personality, gamification badges, local privacy-first computation + Share Card image export).
- **Consequences:** Codebase size reduced by ~40%, zero financial/banking complexity, focused 100% on music tracking, lyrics, and gamified listening stats.

## ADR-003: Approval of Architectural Baseline ARCH-001 (Gate 1 Passed)
- **Date:** 2026-10-01
- **Status:** APPROVED (Gate 1 Passed)
- **Context:** Completion of `ARCH-001` by Architect. Review of architecture specification `.sdd/architecture.md`.
- **Verdict:**
  1. Five distinct zones identified and bounded without overlap (media-ingress, media-core, lyrics-engine, wrapped-analytics, media-ui).
  2. Safe purge registry of 49 files verified against domain requirements.
  3. Git safety net verified: branch `legacy-full-engine` created.
  4. Room v6 migration path (`MIGRATION_5_6`) verified.
  5. Inter-zone integration points mapped.
- **Next Phase:** Transition to Phase 2 (Specification per zone) and Phase 3 (Contract Sync).

## ADR-004: Approval of Specifications & Frozen Contracts (Gate 2, 3 & 4 Passed)
- **Date:** 2026-10-01
- **Status:** APPROVED (Gates 2, 3, 4 Passed)
- **Context:** Completion of zone specifications (`.sdd/specs/media-ingress/`, `media-core/`, `lyrics-engine/`, `wrapped-analytics/`, `media-ui/`), freezing of 4 cross-zone contracts in `.sdd/contracts/` (`FROZEN v1`), and task decomposition into `.sdd/tasks/`.
- **Verdict:**
  1. Gate 2 (DoR Specifications): All 5 zone specifications conform strictly to SDD §5 template.
  2. Gate 3 (Contract Sync): Inter-zone contracts are synchronous, strongly typed, and frozen.
  3. Gate 4 (Decomposition DoR): Task cards partitioned to 1 deterministic function per file. No cyclic dependencies in DAG.
- **Next Phase:** Transition to Phase 5 (QA Test Design: red tests) and Phase 6 (Coder Implementation: starting with `TASK-PURGE-01`).

## ADR-005: Comprehensive Verification (Gates 6 & 7 Passed) & Release Build
- **Date:** 2026-10-01
- **Status:** APPROVED (Gates 6 & 7 Passed, Gate 8 Ready)
- **Context:** Completion of all tasks across 5 zones (Ingress, Core, Lyrics, Wrapped, UI), execution of full unit test suite (64/64 tests green), and successful compilation of debug APK.
- **Verdict:**
  1. Gate 6 (Implementation DoD): All 10 development tasks verified DONE. 64 out of 64 unit tests passed (100% success rate, 0 failures).
  2. Gate 7 (Review): Code matches frozen contracts and specifications without regressions or extraneous changes.
  3. Release Artifact: `app/build\outputs\apk\debug\app-debug.apk` built successfully with Java 21 Temurin.
- **Next Phase:** Phase 8 Acceptance & Device Deployment.

## ADR-006: Production Release v1.1.1 Signing, Permission Onboarding Flow, and Play Protect Hardening
- **Date:** 2026-10-04
- **Status:** APPROVED (Gates 6, 7 & 8 Passed)
- **Context:** User reported that sideloaded release APK did not prompt for notification permissions on initial launch, preventing now-playing media detection, and was blocked/warned by Google Play Protect and Xiaomi Security due to debug keystore signing and cleartext traffic flags.
- **Root Cause:**
  1. `release.yml` mistakenly built `assembleDebug` and renamed `app-debug.apk` with `1.0.0-debug` version and default Android debug key (`CN=Android Debug`).
  2. `NotificationListener.isPermissionGranted` was never checked on app launch to prompt the user, leaving the app in IDLE state without guidance.
  3. Android 13+ (API 33+) marks sideloaded notification listeners as "Restricted Settings" by default, preventing toggle without unlocking in App Info.
- **Decisions:**
  1. **Release Keystore & Versioning:** Generated `app/lirix-release.jks` (RSA 2048, 10,000 days validity, v1/v2/v3 signatures). Bumped `versionCode = 2`, `versionName = "1.1.1"`. Configured `signingConfigs.release` in `app/build.gradle.kts`.
  2. **Security Hardening:** Removed `android:usesCleartextTraffic="true"` from `AndroidManifest.xml` (all network calls use HTTPS).
  3. **Permission Onboarding UX:** Built `NotificationPermissionDialog` and interactive IDLE card in `NowPlayingScreen` with direct intents to notification settings and step-by-step guidance for Android 13+ "Restricted Settings" unlock. Added lifecycle resume re-check.
  4. **CI/CD Workflow Update:** Updated `.github/workflows/release.yml` to compile `assembleRelease` and package signed `app-release.apk`.
- **Verdict:** All unit tests green. Production release build succeeds and passes `apksigner` verification with v2/v3 schemes.

## ADR-008: Gesture-Driven Navigation and Playback Control (Gate 0 Passed)
- **Date:** 2026-10-06
- **Status:** APPROVED (Gate 0 Passed via /grill-me)
- **Context:** User requested natural swipe and touch gesture support to reduce reliance on small click targets.
- **Decisions:**
  1. **Mode Tabs Swipe:** Migrate `NowPlayingScreen` content container to `HorizontalPager(state = pagerState)`. Mode tabs in segmented control mirror pager state bidirectionally with haptic tick.
  2. **Track Skipping Gesture:** Horizontal swipe gestures across the vinyl turntable dispatch `skipToNext()` (swipe left) and `skipToPrevious()` (swipe right).
  3. **Double-Tap Playback:** Double-tap on the lyrics/chords content card toggles `Play/Pause`.
  4. **IPC Ingress Safety:** Implement `skipToNext()` and `skipToPrevious()` in `MediaSessionCollector` using `resolveTargetController()` with single-controller targeting and `dispatchMediaButtonEvent` fallback.
- **Next Phase:** Transition to Phase 1 (Architecture) & Phase 2 (Specification).


