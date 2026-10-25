# Landscape player fit — 2026-10-25

## Cause and narrow contract

The old 280 dp landscape panel stacked metadata, seek, transport, a wrapping timing/text-size control row and mode tabs inside verticalScroll. Its summed height exceeded a short landscape viewport, so bottom controls could disappear until manually scrolled. Parent status-bar/cutout/navigation paddings were also applied separately instead of consuming safe insets once.

The user explicitly requests the left player to fit without scrolling and permits removal of its timing-offset and text-size steppers. Only those landscape steppers are removed. Portrait steppers, playback offsets, stored text scaling, right karaoke content and existing calibration menu are unchanged. No new text-size settings are invented.

## Implementation

The actual production `LandscapePlayerPanel` is a fixed, non-scrolling 280 dp deck. Header, seek row and transport each reserve at least 48 dp. Album art is 32 dp in compact space and 48 dp otherwise; Play is 48 dp short / 56 dp tall. Remaining height belongs to track metadata. Short/large-font decks use one native mode dropdown with the same four selectable modes; taller normal-font decks retain the existing equal-target selector. Five transport targets require 240 dp (248 dp with the larger Play) plus the 24 dp panel padding: the production fixed width 280 dp fits both and is not a screen-width percentage.

The strict supported test viewport is 280 x 248 dp after actual safe insets. On 200% fonts the compact metadata shows the title, with the complete title, artist and source available by tapping its accessible details target. Normal compact fonts also show the artist inline. Ellipsized metadata never discards the underlying full text. Back, source, actual overflow, connect, previous/play/next, share and raw-media seeking are preserved. No favourite action existed in the old landscape block; the existing portrait favourite action remains untouched.

The landscape Row now consumes WindowInsets.safeDrawing once. Main-tree clock/source edits are preserved: `PlaybackClockContent` still wraps only the seek slot, with raw clock anchor updates on seek/toggle. The isolated canonical integration retains its original playback-position conversion. No whole-file copy from canonical to main was performed.

## Exact scoped paths

Mirrored new files:
- app/src/main/java/com/lirix/app/ui/components/LandscapePlayerPanel.kt
- app/src/androidTest/java/com/lirix/app/ui/LandscapePlayerPanelUiTest.kt

Equivalent scoped hunks, preserving pre-existing main-only work:
- app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt

Report (main tree only): .sdd/reports/LANDSCAPE_PLAYER_FIT_20261025.md

No resources, dependencies, updater files, release version or signing keys changed.

## Verification boundary

Native test class: `com.lirix.app.ui.LandscapePlayerPanelUiTest`.
- `shortDeckShowsEveryControlWithoutScrolling`
- `shortDeckAtTwoHundredPercentKeepsControlsAndFullTrackDetails`

Both tests render the production panel, production seek bar and actual context-overflow trigger within the real safe-inset viewport, imposing the strict 280 x 248 dp panel. They assert actual bounds of every transport, back, mode and seek target (>=48 dp), no vertical scroll semantics, absence of timing/text-size steppers, no visual overflow of time/duration/current-mode labels, clickable callbacks, actual seek state change, native mode change and access to the complete long title/artist in the metadata dialog.

QA screenshots are captured after Compose draw from the production panel node (captureToImage), not from an early blank UiAutomation frame, into target external files `qa/landscape-player-1.png` and `qa/landscape-player-2.png`. These are production-renderer fixtures, not proof of a live track/media-session end-to-end run.

This executor did not invoke Gradle or claim runtime success. Coordinator gates: assembleDebug/assembleDebugAndroidTest/lintRelease; focused native tests with emulator landscape; screenshots at normal and 200% font, plus a real player session when available. A physical phone is not connected; JPEG dimensions alone do not establish device dp/density. Viewports below the 248 dp height floor (for example under an open IME) require separate interaction policy and are not silently claimed as validated here.

## Runtime correction after first native run

Both initial tests reported seek semantics height 121 px (44 dp) against the required 132 px (48 dp). The panel remained 682 px / 248 dp high, so this was not viewport clipping or rounding. The Material Slider's inner layout exposed the 44 dp visual/semantics bounds inside a 48 dp row. A single explicit accessible progress action/range now belongs to its 48 dp container; the inner Slider semantics are cleared to avoid duplicate accessible controls while keeping the native pointer/drag handling and raw range/callback unchanged. Native acceptance still requires >=48 dp, and additionally taps the top (y=1 px) and bottom (height-1 px) edges to prove that the full advertised region actually seeks; a wrapper that merely changes reported bounds cannot pass those checks.

The test now requests actual Activity landscape orientation and waits for Configuration.ORIENTATION_LANDSCAPE before composing. It never sets portrait or system user_rotation itself. Earlier UiAutomation captures were blank and are not accepted as visual evidence; panel.captureToImage replaces them. All renderer/source invariants remain pending the coordinator's repeated runtime run.

## Time-label correction after repeated native run

The repeated run passed the >=48 dp control bounds, then failed the unchanged `hasVisualOverflow` assertion for `landscape-time` at both normal and 200% fonts. The captured production-panel fixtures show the complete compact deck and readable times; visual plausibility is not accepted in place of the layout assertion. Each native time string now receives a fixed width from its actual TextMeasurer layout using the same fully merged style and density as the displayed Text. The duration receives the same treatment, and the weighted mode label explicitly fills its assigned width. Only the slider consumes the leftover width. No smaller font, modified timestamp, truncation or weaker criterion is used. All three text assertions now report native constraints, text-box/paragraph dimensions and width/height overflow separately if another mismatch remains. Runtime acceptance is still pending the next coordinator run, including the edge-touch checks not yet reached in the failing run.

## Coordinator runtime acceptance
- Debug app/test APK and release lint PASS (landscape-player-final-build.log, 3m18s). This precedes the final time-label measurement-only correction. Final app/test build PASS again (landscape-player-time-build.log, 1m29s).
- Final production-panel tests PASS under actual EN resources (landscape-player-native-time-en.log,2tests14.655s) and actual RU resources (landscape-player-native-final-ru.log,2tests13.956s). Both normal and200%font cases retain the strict280x248dp panel, no vertical scroll, >=48dp bounds, actual upper/lower edge seek taps, native mode/transport callbacks and reachable full metadata. Earlier failing runs are retained as diagnostics, not passes.
- Final screenshots reviewed: landscape-panel-normal-ru.png visibly contains ВSpotify and Аккорды; English screenshots show InSpotify and Chords. Files in ignored build/landscape-player-fit. These are production-renderer fixtures, not a live external-player session. Exact physical media seek still belongs to phone acceptance.
- Prior updater5 scoped files and player/studio25 scoped files excluding NowPlayingScreen were byte-verified unchanged before this correction. Main-tree unrelated source/clock changes remain outside this scoped staged commit.
- APK local debug signature and app version1.5.1 unchanged. SHA-256: 361ce1a5d5199720b62c6bcbd34c7e39c5f9b44ca75e960a0ff5639070c14b16. Phone unavailable; no phone data changed and no public stable release changed. This closes this scoped landscape layout correction, not the entire player/studio goal.
