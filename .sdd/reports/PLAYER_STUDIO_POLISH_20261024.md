# Player / studio polish: completion audit, 2026-10-24

Overall goal: ACTIVE, not complete. This turn made product-code/test/artifact progress; hardware acceptance is missing. Frozen scope: ignored build/ui-studio-polish/source-manifest.json. Builds use the isolated baseline plus committed repairs/updater and this scope; unrelated working edits are preserved, not claimed tested.

## Delivered changes
- Native library search/metadata grow with text. Four player mode targets wrap labels, have selected/tab semantics and minimum 48 dp targets. Quick stamping and precise mode still share the same captured document/history.
- Reading modes crossfade over 280 ms; main tabs keep saved state, bottom navigation expands/shrinks rather than abruptly changing scaffold padding. System animator setting is observed and refreshed on foreground return, not cached forever.
- Studio controls wrap; timeline uses a sticky header only on tall portrait at normal font scale. Exact ruler coordinates, selected equal-time occurrence, fractional labels, measured spacing, scaled-font baselines and accessible cursor actions. Drag starts from actual pointer down and includes touch-slop movement; release commits once, cancellation does not commit.
- Studio LRC export retains all three millisecond digits. General playback parser accepts representable 100+ minute stamps, rejects huge/overflowing values, and retains legacy 1/2/3-digit fraction support. Legacy quick timestamp formatter is unchanged.
- Real original opposite-phase stereo WAV corpus exercises silence, low/high amplitude and timestamp binning. Instrumentation APK contains exactly the checked-in fixture bytes. Updater's installation-specific last-check preference is explicitly classified as device state; shipped backup allowlists already exclude it.

## Verified evidence
- Full Android unit suite: 329 tests; full shared JVM suite: 249 tests; zero failures/errors/skips. Log ui-studio-verified.log, BUILD SUCCESSFUL in 3m25s. Full corpus export/reimport/playback cases and overflow rejection pass. Initial full run found the missing updater preference classification; it was corrected and the full run repeated.
- Final debug APK, instrumentation APK and release lint PASS after the final scaled-font ruler-baseline/edge-label adjustment: ui-studio-artifacts.log, BUILD SUCCESSFUL in 2m52s. That final adjustment only changes canvas text placement, not timing algorithms; the full unit results above precede that rendering-only adjustment.
- Native gesture/menu/large-font and waveform tests compile. They have NOT run on a phone this turn; compilation is not a runtime pass.
- App APK: build/ui-studio-polish/lirix-player-studio-preview.apk, SHA-256 6204e619f6011f6bc34de2c7b761d2fd01f904bf9fd7d41225d5f2280569a130. apksigner verify PASS; existing local debug certificate 971ecda293eb39469b2c9419f9e04b5db2515276de433869914eb73179774192.
- Test APK: build/ui-studio-polish/lirix-ui-studio-androidTest.apk. No release/tag/version change or device installation/data deletion.

## Requirement-by-requirement remaining acceptance
| Requirement | Current evidence | Still needed |
|---|---|---|
| Unclipped reading / spacing | Adaptive source layout compiles; existing scrollable long-line implementation retained | Actual long lines and final line, portrait/landscape, 140/200% fonts, two themes; review remaining fixed-height landscape headers and full error readability |
| Context menu | Existing real anchored Box and mode policy; new rendered-policy test compiled | Actual parent-window anchor, RTL, scrolling/last items, no duplicate Connect; execute test |
| Smooth screen transitions | Live preference read test, observer lifecycle implementation, compiled crossfade/size transitions | Actual enabled/disabled/rapid switches and state retention, toggling setting with app alive |
| Precise line timeline | Seven geometry tests, reducer/loop/draft suites, exact full-song roundtrips pass | Execute touch-slop drag / cancel test; numeric correction, undo/redo, source seek/loop on actual media |
| Real waveform | Complete PCM container corpus unit test passes; exact test-asset bytes verified | Execute actual MediaExtractor/MediaCodec decode test; file picker/access/duration mismatch UX and cancellation |
| Quick sync and saving | Shared stamping/history/export tests and real atomic draft suites pass | Real quick stamping, Apply, close/reopen/process restart and note/chord preservation |

ADB remained empty at verification. A device connection/hands-off request is pending. Keep the complete goal active; do not replace these gates with code inspection or compiled tests.

Gesture reference: AndroidX's [horizontal drag detector](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/DragGestureDetector.kt) starts callbacks after touch slop. Our first-down displacement avoids losing that initial movement; the actual gesture test remains a device gate.

## Text-size button consistency follow-up
- Replaced portrait 36 dp and landscape 32 dp controls with a shared 48 dp component, matching timing correction surfaces and corners. Added RU/EN accessibility labels and rounded percentage display.
- assembleDebug PASS (text-size-controls.log, 1m23s). Device verification remains pending: ADB has no connected devices.
- Refreshed preview APK SHA-256: 9193a6363f4ccde987e2fcd79f63d4b080a34836f78c2fc165b242a6cd2ac574. The earlier APK hash and source manifest describe the preceding build; the instrumentation APK was not rebuilt for this UI-only follow-up.

## Current artifact reconciliation (after 870d038)
- Previous goal turn: progress (committed button consistency fix). This continuation: verification progress; hardware gate remains open.
- assembleDebugAndroidTest PASS in 30s, log text-size-instrumentation.log. Refreshed test APK is byte-identical to the earlier compiled test APK (SHA-256 c5de94d3e90094bc59ce68c064fafe11ee00eaf8b6126831b0615864e439f179); it has not run on a device.
- Source manifest refreshed: all 26 scoped canonical source files match HEAD after line-ending/trailing-whitespace normalization. APK signature verifies against local debug certificate 971ecda293eb39469b2c9419f9e04b5db2515276de433869914eb73179774192. Exact WAV bytes in the test APK verified again.
- build/ui-studio-polish/artifact-manifest.json records current APK sizes and hashes. App hash remains 9193a6363f4ccde987e2fcd79f63d4b080a34836f78c2fc165b242a6cd2ac574.
- ADB rechecked with configured ANDROID_USER_HOME: no devices. SDK emulator executable and local AVD configuration were not found. Do not equate artifact verification with visual or device acceptance.

### Next device acceptance sequence
1. Inspect installed package version/certificate and preserve data before installing the matching debug preview. Never uninstall to overcome a signature mismatch.
2. Run PlayerSurfaceUiTest and StudioWaveformDeviceTest; retain instrumentation output and failure screenshots. Compiled tests are still unexecuted.
3. Inspect narrow portrait and landscape in RU/EN, two themes, font scales 1.4/2.0. Scroll to the final lyric/error line; check metadata, equal-size controls, last menu item, anchor and unique Connect. Restore original settings afterward.
4. For each player mode inspect available actions and dismiss the menu by changing mode/track/orientation. Check rapid main-tab/mode transitions and saved editor state; toggle system animation scale and return to the live app.
5. Open precise studio with a matching local audio file; check measured duration, silence/amplitude regions, seek, A–B loop, numeric timing, marker drag/cancel, undo/redo and tail shift. Exercise wrong-file/access/decode errors without substituting generated bars.
6. Stamp a line in quick mode, refine it in precise mode, Apply, close/reopen and restart the process. Verify exact millisecond timestamps and preserved text/notes/chords. Use a disposable test document; preserve the user's library.
The complete goal remains ACTIVE. All visual and end-to-end gates above require device evidence.
