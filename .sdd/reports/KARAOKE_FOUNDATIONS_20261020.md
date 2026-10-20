# Existing Android karaoke foundations — 2026-10-20

User priority: fix existing offset, seek, timing persistence, clipped lyrics and duplicate Connect before implementing advanced Studio.

## Delivered scope

- Shared bounded setter returns exactly the legacy preference value displayed. No preference migration or device/song separation.
- Raw player clock and adjusted lyric clock are distinct. Negative adjusted time remains negative; inverse lyric seek converts once and clamps only outbound media time. Main progress sliders show raw media time. Paused local fallback recalculates correction without running time forward.
- Offset controls use +/-50 ms with hold repeat and accessible numeric calibration; existing +/-10 ms precision retained. Calibration body scrolls and invalid numeric input is shown explicitly.
- Existing Tap-to-Sync captures track/source and existing LRC on entry. Same song paused in a newer player captures the actual source; later song/source changes disable media actions. Saving awaits completion, blocks content changes while pending and retains retryable content on failure.
- Saving updated plain text and LRC uses one Room transaction, reads latest cache auxiliary fields, rejects conflicting lyrics, and updates displayed cache only for matching track. No advanced persistent drafts shipped.
- Karaoke rows use occurrence-safe keys and stable text metrics, maintain reachable wrapped first/last rows and no active row before first timestamp. Portrait transport's duplicate Connect removed, header action remains.

## Product and test files (explicit staging list)

1. `app/src/main/java/com/lirix/app/ingestion/LivePlaybackSnapshot.kt`
2. `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`
3. `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt`
4. `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt`
5. `app/src/main/java/com/lirix/app/ui/components/KaraokeTiming.kt`
6. `app/src/main/java/com/lirix/app/ui/components/TapSyncLyricsRepository.kt`
7. `app/src/main/java/com/lirix/app/ui/components/TimingCorrectionControls.kt`
8. `app/src/test/java/com/lirix/app/KaraokeFoundationsTest.kt`

No shared/desktop production change. New advanced Studio engine/draft sources and their tests were moved out of compiled sources to ignored `build/deferred-karaoke-studio`; future architecture/specs/KST cards are marked DEFERRED. User `.codex-remote-attachments/` inputs are neither committed nor deleted.

## Evidence

Focused command: `:app:testDebugUnitTest --tests '*KaraokeFoundationsTest' --offline --no-daemon --no-configuration-cache --max-workers=2` — SUCCESS, 6 tests passed, 0 failures/errors (before final bounded calibration/enable-state delta).

Exact coverage: pure signed/inverse clock, paused snapshot and seek reanchor arithmetic, duplicate occurrence key separation, source capture/identity guard, legacy offset bounds/reopen/isolation; actual in-memory Room save of A while B remains unchanged, edited plain/LRC and latest notes/chords retained; actual trigger failure on second table write rolls back first write and retry succeeds. The displayed-track conditional is verified as a pure guard, not a Compose interaction test. Tests do not certify external player's native seek response.

Independent read-only review found and verified fixes for edited-plain mismatch, mutation during pending save, reset after text editing, and stale stored source on pause. Final frozen delta review found no remaining confirmed defect. Reader layout assessment is code-level geometry review only.

Reviewer: `/root/karaoke_studio_architect/karaoke_foundations_reviewer`; final frozen-source review PASS on 2026-10-20, then final calibration/enable-state delta PASS on 2026-10-20. Reviewer performed no source edits or builds, and explicitly withheld physical/visual certification.

Full verification command: `:app:testDebugUnitTest :shared:desktopTest :desktop:test :app:assembleDebug --offline --no-daemon --no-configuration-cache --max-workers=2` — SUCCESS in 2m15s. Android final source compiled and all 204 tests passed; unchanged shared regression results restored from Gradle cache (173 passed); unchanged desktop tests up-to-date (125 discovered, 123 passed, 2 existing skips). Total 500 passed, 2 skipped, 0 failures/errors. No build remains running. No product source changed after this verification.

APK: `app/build/outputs/apk/debug/app-debug.apk`, 27,957,866 bytes; SHA-256 `77f944160400e3bbdd8ffdc42bfa6b00c184e97fcd8cc209e8c7cf6e50a4f895`. Built 2026-10-20. No PC package rebuilt because no desktop/shared product code changed.

Earlier advanced-Studio negative/crossing RED tests demonstrated an unvalidated legacy helper boundary; they are DEFERRED with that work, not acceptance evidence for this bounded package. The legacy helper remains unchanged.

## Physical/visual limit

ADB checked once: no devices connected. No installation, phone screenshot, real-player seek or listening acceptance performed. Android module has no existing JVM Compose screenshot harness; no new dependency/framework added. Small portrait, landscape, large fonts and actual external-source behavior remain physical acceptance tasks; neither desktop previews nor pure tests are presented as phone certification.
