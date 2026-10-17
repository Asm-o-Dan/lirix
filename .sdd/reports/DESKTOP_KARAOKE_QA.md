# Desktop karaoke QA — 2026-10-17

## Confirmed user scenario

The user clarified that the desktop player offers “Текст” but has no “Караоке” tab. This is confirmed in DesktopDeckScreen: its right-side tabs are Text, Chords, and About. The Text branch silently chooses DesktopTeleprompterView for parsed LRC and a static scrollable Text for plain lyrics. A user cannot explicitly select karaoke or understand why timing is unavailable. The generic toolbar Tap-to-Sync action exists, but the lyrics panel has no contextual action for this case.

## Actual coverage

- DesktopLyricsServiceTest currently exercises six chord search/matching cases, not karaoke modes, source selection, loading, or playback timing.
- DesktopLibraryRepositoryTest proves a saved LRC string survives Room reopen; it does not prove service hydration or presentation.
- DesktopTransportBarTest proves offset formatting/preferences, not application of offset to highlighted lines or seek conversion.
- Shared LrcSyncEngineTest covers parser and tap-sync domain operations. It does not mount desktop player/HUD or the packaged native application.
- Previously passing 42 desktop and 173 shared tests do not establish that the native karaoke user flow exists or works.

## Independent defects / limitations to assess

1. **Mode discoverability (confirmed primary symptom).** No dedicated karaoke selection, no visible capability status, no contextual route from untimed lyrics to the studio.
2. **Clock cadence.** DesktopDeckScreen computes currentPositionMs once per composition. It has no periodic lyric clock. The native bridge sends timer timeline updates every 1000 ms; therefore line changes can lag until the next event. HUD separately samples every 50 ms. A deterministic boundary test should cover elapsed time without another bridge event.
3. **Saved source handling.** DesktopLyricsService hydrates Room saved lyrics immediately only for provider USER. Non-USER saved LRC is ignored before custom-source/network search. File cache can hide this gap on an existing install; imported/offline Room data may expose it.
4. **Source priority.** A matching custom plain-text rule returns before the timed-source lookup. This can legitimately leave a song with static text even when a timed source might exist. Product contract must explicitly choose whether mode selection searches again or explains missing timings; tests must not invent fake timestamps.
5. **First-line behavior.** Teleprompter activates the first line before its timestamp, whereas HUD has no active index until that timestamp. Decide whether this is preview styling or actual highlight; test the chosen contract consistently.
6. **Offset / seeking.** Lyrics time is playback time minus offset. Line-click currently sends the unadjusted lyric timestamp directly to ingress. A positive offset can therefore seek to a point before the selected line becomes active. Contract should define inverse conversion.
7. **Packaged runtime risk (separate).** Main dispatches global-hotkey callbacks through Dispatchers.Main while desktop explicitly depends on coroutines-core. An observed parity-smoke log contains missing-Main-dispatcher at Main.kt:68, followed by application-scope cancellation. This is relevant to whole-app stability; the log alone does not identify the user's currently running build or prove it caused the missing tab.

## Regression suite proposed, pending approved contract

- Explicit mode set includes Text and Karaoke independently; mode remains selectable with no timed source and reports an honest empty state.
- Timed, plain-only, loading, error, and no-playback states map to distinct karaoke presentations with a contextual studio callback where applicable.
- Sampling one playing snapshot advances across an LRC boundary without a new bridge event; paused state stays fixed; a seek replaces the baseline; duration is clamped.
- Positive/negative offset semantics and line seek inverse remain consistent between player and HUD.
- Saved USER / offline non-USER LRC hydrates into timed content without network where the approved source contract requires it; stale prior-track work cannot publish into the new track.
- Packaged hotkey callback does not throw or cancel the application scope when requesting a UI action.

No product or test code changed during this audit. No Gradle tasks launched. Await architect contract before adding regression tests.

## Approved regression gate

Root approved the architect contract and real production APIs. Added eight tests in DesktopKaraokePresentationTest and DesktopPlaybackClockTest, without adding dependencies or changing product code. The tests cover separate timed/static presentation, plain/only-chords untimed states, idle/loading/unavailable precedence, first-line boundary and backward movement, offset seek inversion/clamping including overflow, a sampled playing snapshot crossing a lyric boundary without further ingress, paused/null completion and new seek/resume baselines, duration bounds, and an existing offset subscriber receiving updates without playback changes.

RED evidence: `:desktop:compileTestKotlin --offline --no-daemon --no-configuration-cache --max-workers=2` failed in 24 seconds on missing production presentation/clock APIs and observable offset property. Existing product compilation was up to date. This establishes the absent contract; it is not a behavioral test failure against implemented helpers. No Gradle task remains running. Implementation agent was notified and owns the next full build/test run. Main dispatcher fix stays direct captured-scope code and native/runtime verification; no artificial wrapper or source-text assertion was added.

## Isolated focused GREEN gate

Independent review rejected temporary writes to actual user offset preferences. Coordinator approved focused CR KARAOKE-013: an injected Preferences-backed store with the same production singleton API, node, key, and default. QA added in-memory MemoryPreferences and moved both the new subscriber regression and existing DesktopTransportBarTest persistence check to it. The tests never construct the production singleton. An explicit collector cancellation test now checks that clock sampling stops after cancelAndJoin.

Focused command: `:desktop:test --tests '*DesktopKaraokePresentationTest' --tests '*DesktopPlaybackClockTest' --tests '*DesktopTransportBarTest' --offline --no-daemon --no-configuration-cache --max-workers=2`.

Result: BUILD SUCCESSFUL in 1m12s, fresh product and test compilation. Eleven tests executed across the three classes (5 presentation, 4 clock/offset, 2 transport), zero failures/errors. Cosmetic EOF whitespace was normalized after completion; no behavioral changes after the run. Root owns final full tests and distributable packaging. Native visual acceptance remains a separate open gate.
