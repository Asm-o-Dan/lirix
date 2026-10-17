# Desktop karaoke correction — 2026-10-17

Status: APPROVED v1, coordinator Gate 2/4 PASS. Product implementation assigned to karaoke_coder; QA established missing-API RED before implementation. Native acceptance pending.

## Confirmed problem and boundary

User clarified the primary failure: there is no Karaoke tab, only Text. DesktopDeckScreen currently exposes Text / Chords / About, and hides its existing synchronized teleprompter inside Text. This makes the requested mode undiscoverable. User cannot currently run manual checks; native capture timed out. No native acceptance is claimed.

Do not fabricate timestamps for plain lyrics. Do not change Android behavior or the shared LRC parser as part of this correction.

## Required behavior

1. Deck has four explicit tabs: Text, Karaoke, Chords, About. Use named mode values rather than shifting numeric meanings. Default to Karaoke so the playback use case is immediately visible. Mode stays selected across track changes.
2. Text always renders selectable, manually scrollable text. Use plain lyrics when present, otherwise join parsed timed lines with newlines. It never silently switches into a timed teleprompter.
3. Karaoke renders the existing timed teleprompter only when parsed timestamped lines exist. Its position must update every 50 ms while playing without depending on WASAPI meters, unrelated Compose recompositions, or native timeline events. Refresh immediately on new snapshot, pause, seek or track change. Cancel its coroutine on leaving composition. Reuse the same sampled position for the transport readout. Snapshot monotonic-clock contract is already correct.
4. When lyrics exist without timestamps, Karaoke explicitly says synchronization is unavailable and offers Tap-to-Sync and Text. Wire Tap-to-Sync to DesktopWorkspace's existing dialog callback for the current track. Disable only when no current track is available. A plain-only song remains valid Text content.
5. Keep loading, no playback, and unavailable lyrics states distinct. Before the first timestamp no line is active; do not highlight the first line early. Clicking a karaoke line seeks to `line.timestampMs + syncOffsetMs`, clamped to nonnegative and known duration, because displayed lyrics position subtracts that offset.
6. HUD already has its own 50 ms clock. Do not replace its native drag fix. Share or observe the sync-offset state so a changed offset reaches both Deck and HUD immediately, including while paused; reading Preferences in Main without observing updates is insufficient.

## Separate confirmed runtime defect

Main.kt explicitly launches hotkey callbacks on Dispatchers.Main, but desktop/build.gradle.kts has only coroutines-core; the current packaged image contains core and no coroutines-swing artifact. Runtime logs report missing Main dispatcher. Safe minimal fix: launch on the already captured Compose rememberCoroutineScope context (remove the unnecessary dispatcher override); that scope uses the Compose dispatcher and is already used for UI work. No new runtime dependency is needed. Review the actual captured-scope callsite directly and inspect packaged runtime logs if launch is available; do not introduce a mirror wrapper solely to manufacture a test. Old logs alone do not prove this caused the user's karaoke report.

## Secondary findings outside immediate fix

- Native GSMTC bridge emits timeline each second only with positive duration; a local UI clock prevents freezing when duration is unknown and improves existing one-second stepping.
- Lyrics service waits for chord scraper requests before publishing already-found LRC. Treat as a latency follow-up, not the confirmed missing-tab cause.
- LRCLIB search currently picks first nonempty result, possibly plain-only before a synced candidate. Improving match ranking is separate work; do not substitute unrelated synchronized lyrics merely to show karaoke.

## Validation

Meaningful regressions cover actual mode-to-content decisions for timed-only, plain-only, loading and absent playback; text fallback from timed lines; clock progress without incoming snapshots, pause, seek, resume; first-line boundary; positive/negative offset seek inversion and clamping; hotkey callback execution without a Main service provider. Compile and run desktop tests, package a separate output image without killing installed processes. Native visual gate remains unverified until a screenshot is obtained.

## Frozen implementation contracts (Gate 2)

All paths below are relative to desktop/src/main/kotlin/com/lirix/app/desktop/.

`ui/components/DesktopLyricsPresentation.kt` declares:
- `enum class DesktopLyricsMode { TEXT, KARAOKE, CHORDS, ABOUT }`
- `enum class DesktopLyricsContent { IDLE, LOADING, TEXT, KARAOKE, UNSYNCED, UNAVAILABLE, CHORDS, ABOUT }`
- `fun resolveDesktopLyricsContent(mode: DesktopLyricsMode, hasPlayback: Boolean, lyrics: DesktopLyricsState): DesktopLyricsContent`
- `fun desktopPlainLyrics(lyrics: DesktopLyricsState): String`
- `fun activeDesktopLyricIndex(lines: List<LrcLine>, positionMs: Long): Int`
- `fun desktopLyricSeekPosition(timestampMs: Long, syncOffsetMs: Long, durationMs: Long): Long`

Resolver routes CHORDS/ABOUT directly. For TEXT/KARAOKE first return IDLE without playback, then LOADING while loading. TEXT returns TEXT when desktopPlainLyrics is nonblank, else UNAVAILABLE. KARAOKE returns KARAOKE for nonempty parsed lines, UNSYNCED for any plain/chord content without parsed lines, otherwise UNAVAILABLE. desktopPlainLyrics uses nonblank plain first, then joined parsed-line text. Active index is last timestamp <= position, else -1; timestamps are sorted by parser. Seek adds offset, clamps nonnegative and to duration only when positive; protect addition from Long overflow.

`ui/components/DesktopPlaybackClock.kt` declares `fun desktopPlaybackPositions(playback: LivePlaybackSnapshot?, tickMs: Long = 50L, nowMs: () -> Long = ::getSystemElapsedRealtimeMs): Flow<Long>`. Cold flow emits position immediately; null/paused/nonpositive speed emits once then completes. Playing snapshot samples at tickMs until cancellation. Require tickMs > 0. nowMs is the monotonic clock passed to currentPositionMs. Compose LaunchedEffect(playback) cancellation replaces fixed-snapshot flow after each snapshot change. This is the production clock seam, not a mirror test abstraction.

Existing `DesktopSyncOffsetStore` adds `val offsetMs: StateFlow<Long>` initialized from existing preference/default; `setOffsetMs` persists then publishes. Existing `getOffsetMs` returns current value. Deck and Main collect that flow.

Existing `DesktopDeckScreen` adds trailing `onOpenTapSync: () -> Unit = {}` and `canTapSync: Boolean = false`; Workspace supplies existing current-track dialog activation. Default mode KARAOKE is intentional to expose the specifically requested feature; Text remains one click away and never impersonates timed content.

## Focused CR: isolated offset persistence verification

Approved by coordinator after independent review. Add `internal class DesktopSyncOffsetPreferences(private val preferences: Preferences)` in ui/components. Its API is `val offsetMs: StateFlow<Long>`, `fun getOffsetMs(): Long`, and `fun setOffsetMs(offsetMs: Long)`. Construct initial observable value from injected preferences key `teleprompter_sync_offset_ms`, default `-300L`; setter persists that same key and publishes the new value. Recreating the class against the same preferences recovers the saved value.

Existing public `object DesktopSyncOffsetStore` retains its constants and get/set/offsetMs API, delegates to this class using the unchanged `Preferences.userNodeForPackage(DesktopSyncOffsetStore::class.java)`. Constructing the injectable class must not initialize the production singleton or access global user preferences; avoid referring to an object field for its default. No new dependency or OS storage migration.

QA uses in-memory AbstractPreferences for both the existing offset persistence test and new subscriber test. No real-user preference writes, even temporarily. Add explicit cold clock cancellation verification. This CR changes dependency construction only; production offset behavior remains the frozen contract.
