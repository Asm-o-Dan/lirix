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





