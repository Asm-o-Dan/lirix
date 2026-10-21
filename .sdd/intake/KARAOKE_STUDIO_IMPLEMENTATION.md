# Full line-level karaoke studio — active execution

User resumed final studio implementation and explicitly chose line-level studio + real local-audio waveform. Word timing is outside this delivery; Japanese/Chinese romanization is researched separately in LYRICS_ROMANIZATION_JA_ZH.md. Android only; preserve existing quick Tap-to-Sync and prior UX work. Direct Codex execution authorized.

## Scope and current implementation
- One captured song/session for quick and precise modes, ordered occurrence IDs, raw media timestamps, bounded undo/redo, exact numeric time and ±50 ms, whole-tail shifts, original restoration and validated LRC export. No runtime offsets baked into LRC; legacy calibration is preserved without migration.
- Center cursor, horizontal ruler/markers, zoom, one history entry per marker drag, independent cursor and explicit source seek. Accessible numeric/line alternatives to dragging.
- Source controls guard actual controller metadata and action flags. A–B loop waits for actual seek acknowledgement and disables on source mismatch/timeout.
- Private AtomicFile drafts retain original, current document, expected saved baseline, cursor/mode and selected audio URI. Conflict/recovery choices are explicit; starting over archives the prior file. Existing Room transaction still guards Apply and preserves current notes/chords. Applying does not close the studio or destroy original recovery.
- OpenDocument audio selection, read-only URI permission, real PCM decoding with MediaExtractor/MediaCodec, finite peak data, bounded duration/bins and cancellation. No fake waveform or external-stream scraping. File/version mismatch is visible. Native codec validation still requires a phone.
- No release/tag changes. Existing unrelated working edits remain outside this task's commits; stage the NowPlaying entry/save changes separately.

## Required evidence before declaring the goal complete
- Model/reducer regressions: import ordering/equal times, malformed input, atomic shift/overflow, drag history, redo, reaction once, restore and export precision.
- Draft roundtrip/track isolation, atomic interruption/corruption, original retention after apply, explicit archive, failed writes.
- PCM channel/encoding/timestamp aggregation; loop acknowledgement/timeout/no seek storm.
- Android compile and relevant suites; then real phone: long intro, correction, chorus shift, switch modes, close/reopen/process restart, conflict/save failure, actual waveform decode, seek/loop, all controls/long lines in both orientations/themes.
- Earlier Android UX goal still needs physical acceptance: 1500 ms Wrapped tempo, screen transitions/system animations disabled, poster preview/save/share.

Status: AUTOMATED/REVIEW PASS; physical acceptance pending. Android 260/shared 200 tests and final release lint pass. Do not report the studio as fully device-verified until phone gates pass.

## Verification checkpoint — 2026-10-21
- Isolated debug assembly succeeded. Local test APK: `build/karaoke-studio-validation/lirix-karaoke-studio-preview.apk`; SHA-256 `c6f2327097efce6213cb343cef2127c22f288db195dfaa9f9d0dda0d965e52f3` (intermediate build; refresh after final verification).
- APK signature verified with apksigner; local Android debug certificate matches the previously installed test build. No uninstall, installation or publication performed at this checkpoint.
- Android tests: 251 executed, 250 passed, one failed in the separate listening-time work (`MonotonicListeningTimeTest.latePauseAfterNewerHeartbeatStillStopsAccrual`). That test file changed after this run, so a fresh run is required. Shared desktop-target tests: 200 passed.
- Studio-specific suites passed: reducer 12, loop 4, drafts 7, PCM aggregation 5. Backup policy 6 passed. Draft directory is explicitly included in cloud/device-transfer rules; chosen audio files are not copied by system backup. Actual backup/restore remains untested.
- Release lint and independent review pending. ADB returned no connected device. Native codec waveform, gestures, actual seeking/looping, draft recovery on device and earlier motion/poster UX remain acceptance gates.

## Final automated/review evidence — 2026-10-21
- Fresh combined build PASS: `:app:assembleDebug :app:testDebugUnitTest :shared:desktopTest` in the private Gradle environment. Android 260 tests, zero failures/errors; shared 200 tests, zero failures/errors (shared unchanged and up-to-date in this run). The earlier listening-time fixture failure is superseded by this fresh result.
- Independent review found one P2 duration-provenance issue. A separate executor corrected it and added seven regressions; the original reviewer verified the correction and found no remaining blocker in that scope. See reports/KARAOKE_STUDIO_REVIEW_20261021.md and tasks/KST-DURATION-001.md. The equal-time marker tie preference remains a non-blocking P3 with numeric/list alternatives.
- Final debug APK: `build/karaoke-studio-validation/lirix-karaoke-studio-preview.apk`, SHA-256 `1f40439ccdfaac798d8a183e62cd1b1f3e8c931a8d54cd6f87531eb1879fc182`. apksigner verification PASS; local debug certificate `971ecda293eb39469b2c9419f9e04b5db2515276de433869914eb73179774192` matches the previous local phone test build.
- Physical acceptance remains pending: ADB lists no connected devices. No install, data wipe, release/tag edit or actual backup/restore has occurred. Studio waveform/seek/loop/gestures and earlier motion/poster UX still require a phone.
- Final `:app:lintVitalRelease` PASS after the duration correction (57 tasks, 5 executed, 52 up-to-date). No release publication.
