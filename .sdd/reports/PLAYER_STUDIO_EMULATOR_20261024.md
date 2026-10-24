# Android emulator acceptance — 2026-10-24

## Environment

Phone unavailable. Installed an isolated Android SDK/AVD under ignored `.gradle/`, reusing the already accepted local SDK licenses. Android 15 / API 35 AOSP x86_64, Pixel 4 profile, WHPX acceleration, emulator-5556. No system driver installation, physical-device data changes, release or version bump.

## Runtime evidence

- App and instrumentation APKs installed successfully on the virtual device.
- Initial four-test run: three passed. Contextual menu actions/dismissal, marker drag including touch slop/cancellation, and actual MediaExtractor/MediaCodec decoding of the known opposite-phase WAV passed. The large-font test failed on the English label `Lyrics` at 320 dp / font scale 2.
- First correction exposed duplicate sizing labels in the unmerged semantics tree; the unchanged test rejected this.
- Removing sizing composables eliminated duplicate nodes, but a Russian-language run still failed on `Текст`. Diagnostic measurement is in progress; do not claim the label correction passed yet.
- Real main-screen popup inspected in screenshots and UI hierarchy: adjacent below the top-right trigger, all five empty-track actions visible; no duplicate Connect action. At 320 dp / font scale 2, EN and RU labels wrap and the final blacklist action is reachable. Language switches immediately to RU. Evidence: ignored `build/ui-studio-polish/emulator-menu.png`, `emulator-menu-large.png`, `emulator-menu-ru.png`, and corresponding XML dumps. These screenshots cover the empty-track main screen, not all player contexts.
- Temporary system font/size overrides were restored to 1.0 and physical resolution. RU app language remains selected on this disposable emulator.

## Remaining gates

Full lyric/final-line scrolling, populated-track parent-window menu/RTL, themes, screen transitions and state retention, studio file mismatch/permissions, seek/loop, numeric edit/undo/redo, Apply/reopen/process restart and preserved notes/chords still require runtime coverage. A real-device media-player integration check remains separate from emulator evidence. The full goal is not complete.

## Final correction and verification
- Diagnostics isolated width overflow: Russian Text label box 182 px versus paragraph 185 px, with sufficient height. Actual labels now occupy their allocated width; measured height is based on the same styles and width. No sizing semantics nodes remain.
- assembleDebug, assembleDebugAndroidTest and lintRelease PASS in 3m7s (mode-label-width-build.log).
- The same four test criteria PASS under actual RU app language (14.977s) and actual EN app language (13.921s): emulator-polish-tests-width-ru.log / emulator-polish-tests-width-en.log. No assertion was relaxed; failure diagnostics were expanded.
- English resource transition independently confirmed in the live UI hierarchy (Nothing is playing), not merely by inspecting saved preferences.
- Current artifacts: {"lirix-player-studio-preview.apk": {"sha256": "b5f63a9186f903073c6a58c8725d656f856c227028f27dfc9210a167c310c6f1", "bytes": 43934528}, "lirix-ui-studio-androidTest.apk": {"sha256": "26c2d5a7ee6c52f7c1a193f88cc13f973f66598320e53445dde1e4ebdaa5243b", "bytes": 1465170}}
- Current corrected preview installed on the emulator. Goal remains incomplete; all other gates above are still open.
