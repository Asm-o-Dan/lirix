# Desktop readiness: independent QA — 2026-10-17

Status: **not release ready**. This audit inspected source, existing tests and validation reports; it did not run Gradle or operate the native application. Baseline evidence is 51 desktop and 173 shared tests passed in `DESKTOP_KARAOKE_VALIDATION.md`. These are not 224 native acceptance scenarios. The older parity progress report predates subsequent changes.

## Evidence matrix

| Scenario | Existing evidence | Acceptance still missing |
|---|---|---|
| Core desktop karaoke | User explicitly confirmed working; presentation, playback clock and offset regressions pass | Native seek/offset, pause/resume and theme edge cases |
| HUD drag | WindowDraggableArea implementation and independent static review; earlier 42-test run | User has not confirmed fix; native drag/resize, screen/DPI transitions and reopen |
| Hotkeys | Catalog uniqueness; persistence tests; captured Compose scope now replaces the previously broken Main dispatcher dispatch | Actual registration/callbacks with one running copy, busy-key feedback, focus behavior |
| Library/history | Two temporary real-Room integration tests: interval accounting, notes/favorite/LRC reopen and first-sample blacklist; four pure ledger tests | Service loading from reopened data; new/paused-track editing, migration fixture and active-session lifecycle |
| Saved/offline lyrics | Room preserves a USER LRC string | Service tests cover six chord matching cases, not saved-cache precedence, cancellation or concurrent edits; concrete defect below |
| Posters | Renderer checks all themes/formats, dimensions and background colors | Human review of long multilingual titles/lyrics, content bounds and actual exported preview |
| Database merge | Shared archive encoding, planning and transactional write implementation exists | No platform callsites, user interface, automatic backup/restore or archive tests found; unavailable to users |

## Confirmed code defects and bounded risks

### P1 — persisted non-USER lyrics are ignored by the desktop service

`DesktopLyricsService.onTrackChanged` reads `repository.savedLyrics(trackKey)` but returns saved content only for `provider == "USER"` (current lines 114–120). Persisted LrcLib/custom-rule text or LRC therefore falls through to custom network sources, a separate filesystem cache, then online search. A pre-existing disk cache can conceal this; a Room-only offline library cannot rely on its saved text. This also blocks useful future archive imports.

First regression should seed real temporary Room with timed/plain/chord non-USER content, start the real service with isolated disk/network dependencies, and require exact saved content without an external lookup. USER precedence including forced refresh, rejected providers, empty cache, track switching and cancellation must be specified explicitly rather than inferred.

### P1 — a network result can overwrite an explicit edit during persistence

`saveUserLyrics` persists USER data before cancelling the active fetch (current lines 268–274). A fetch which has already passed its `ensureActive`/key check can wait for the repository mutex, then overwrite the USER record with its non-USER result. The saving coroutine can resume and show the edit in memory while Room retains the fetched version. Repository `saveLyrics` currently unconditionally replaces the cache, so it provides no USER protection independently of service scheduling.

This is a code-level race, not an observed user data-loss report. Regression must control fetch completion and the persistence boundary, check both published state and reopened Room, and prove cancelled/stale work cannot write or publish after the edit. Editing another track must not cancel an unrelated current-track lookup.

### P2 — source rejection has misleading behavior for saved edits

`rejectCurrentSource` maps the displayed label “Сохранено вами” to `builtin:web`; the subsequent forced refresh immediately reloads the same USER cache. The action can reject an unrelated provider and appear ineffective. Decide whether to disable rejection for USER or offer an explicit remove-edit action; do not silently erase edits.

### Archive merge: do not expose the existing helper as complete

There are no `LibraryArchives` callsites outside its own implementation and no archive tests. No user-selected file has been merged during this audit. Concrete pre-exposure issues:

- `plan` recomputes track counters only while iterating incoming tracks. Its accepted input can contain new sessions for an existing local track without that track in `incoming.tracks`; merged history changes while the local track counters remain unchanged. Either reject incomplete snapshots or reconcile every affected track.
- `plan` reduces sessions through `associateBy(trackKey, sourcePackage, startTimeMs)`. Existing duplicate identities would be collapsed in the plan, but `merge` never deletes their database rows; preview and post-merge database can diverge. Define identity policy and test against a real database.
- Maximum cumulative counters cannot reconstruct disjoint histories when session rows are absent. Decide whether complete session history is required; do not claim lossless aggregation from the current `max` policy.
- Backup, preview, conflict selection, transaction rollback and repeat-import idempotence are unverified. Same-key USER lyric conflict, favorite OR, note union, rejections and incoming session IDs need deterministic tests before UI work. Cover URI strings do not transfer device-local image files.
- A future desktop merge must share repository serialization and reload `activeSession`; otherwise the recorder can later write its pre-merge in-memory duration over merged session data.

## First approved-fix test contract requested

Coordinate with the readiness architect before changing production APIs or tests. Prioritize saved/offline service hydration and protection of USER edits. Use real temporary Room, controllable provider suspension and isolated filesystem dependencies; never external HTTP, real user preferences or source-text assertions. Verify cancellation and persistence, not only a pure cache-selector helper. No product/test edits or Gradle tasks were performed by this audit.

After that bounded fix, the highest-value archive gate is repeat import into real Room followed by close/reopen: same row count, stable counters/notes, USER choice retained, and failure leaves the original snapshot unchanged. Native HUD/hotkey acceptance remains separate and cannot be replaced by catalog tests.

## Offline fix: approved regression gate — 2026-10-18

Root approved the exact constructor seams in `desktop_offline_lyrics.md`. QA added eleven tests in `DesktopOfflineLyricsTest.kt`, exercising the real service with temporary Room and cache directory. Deferred gates control lookup, read and write boundaries; no external HTTP, user preferences, application cache or arbitrary sleep is used. Scoped jobs terminate before database close; noncancellable completion gates are released during cleanup.

Coverage: Room-only timed/rule/chord reopen, USER forced refresh and automatic refresh bypass, blank/NONE miss handling, canonical rejection aliases, rejected memory and actual disk cache, saved-read cancellation, cancellation/join before USER persistence, automatic commit returning after a track switch, held USER save during a new lookup, editing an unrelated library track, and durable repository USER protection including explicit clearing and both lyrics tables after reopen.

Initial command: `:desktop:test --tests '*DesktopOfflineLyricsTest' --offline --no-daemon --no-configuration-cache --max-workers=2`.

Result: expected `compileTestKotlin` failure in 27 seconds because the four approved constructor parameters do not yet exist. Existing product compilation was up to date. This is an absent-API contract RED, **not a behavioral reproduction of either defect**. No test body executed. Implementation was explicitly released to proceed only after this Gradle process exited. The additional post-persistence switch test was finalized before implementation's build gate; QA has made no product edits. Implementation owns subsequent full tests and packaging.
