# Desktop karaoke independent review — 2026-10-17

Status: PASS for approved product source and regression design. Native acceptance is unverified; final suite/package results are recorded separately by the coordinator.

## Product integration

Reviewed the approved spec/cards, actual Deck/Workspace/Main/Transport/Teleprompter/HUD changes and all three new production components. Four named tabs include explicit Karaoke, selected by default and remembered across track snapshots. Text remains selectable, manually scrollable plain content with timed-line fallback. Karaoke only mounts the teleprompter for parsed timing; plain/chord-only content shows honest synchronization guidance and contextual Tap-to-Sync/Text actions. Workspace supplies the existing current-track studio route.

The production cold flow samples the existing monotonic snapshot clock every 50 ms without requiring native ingress or audio-meter events. Deck collects it in LaunchedEffect(playback), so snapshot replacement and leaving composition cancel the previous collector. Transport and lyrics use the same sampled position. Pause/null/nonpositive speed complete after immediate emission. The active-index helper no longer highlights before the first timestamp. Line seeking adds the display offset, saturates Long addition, and clamps to known playback bounds.

Main and Deck observe the same StateFlow offset; HUD receives the observed value, including paused updates. The HUD change only removes effective-lyrics-time zero clamping, preserving the native WindowDraggableArea drag fix. Main hotkey callbacks launch directly on the captured Compose rememberCoroutineScope; no Dispatchers.Main override or artificial test wrapper remains.

## QA correction and coverage

The initial QA finding was resolved before approval: the offset-observer and existing transport persistence tests had written the production user Preferences node. The reviewed correction introduces internal DesktopSyncOffsetPreferences with injected Preferences; the singleton delegates to it with the unchanged production node, key, default, and public API. Both tests now exercise this actual production storage class through MemoryPreferences, an in-memory AbstractPreferences implementation that never opens the user registry. Persistence is checked by reopening the same in-memory node. No product-only test abstraction or artificial dispatcher wrapper was added.

The regression suite meaningfully covers timing/static decisions, loading/idle absence precedence, first-line/backward boundaries, offset seek inversion/overflow, autonomous clock progression, new pause/seek/resume baselines, and an explicit canceled collector no longer reading the clock after cancelAndJoin. The observable offset test proves existing subscribers receive updates without playback events. No unresolved actionable source findings remain within the approved correction.

## Acceptance limits

No Gradle process was launched by this reviewer. Full test results are owned by the implementation/QA agents and coordinator. Native screenshots previously timed out; this review does not establish on-screen layout, real media playback highlighting, or live native hotkey execution. Saved non-USER offline LRC hydration/source ranking remain previously documented follow-ups outside the approved correction.

