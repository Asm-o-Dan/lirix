# Desktop readiness — architecture audit, 2026-10-17

Conclusion: functional preview, not release accepted. Code evidence below is separate from user verification (karaoke works) and from visual QA, which this audit did not perform. No product code changed by this audit.

## Feature matrix

| Capability | Code status | Acceptance gap |
|---|---|---|
| Current playback, text, karaoke, chords | Implemented; karaoke confirmed by user | Full keyboard/seek/pause/restart pass still needed |
| Library, history, favorites, notes | Implemented in DesktopLibraryRepository and DesktopWorkspace | Real app restart and long playback checks pending |
| Lyrics/LRC editor, Tap-to-Sync | Implemented in DesktopFeatureDialogs | Unsaved editor changes are discarded on window close; no dirty-close prompt |
| Offline text | Partial | Room non-USER cache is not hydrated by the lyrics service |
| Wrapped, achievements | Shared analytics wired to desktop persisted sessions | Visual and representative-data acceptance pending |
| Track/Wrapped PNG | Implemented native renderer | Actual exports with long Cyrillic text/covers require visual inspection |
| Six themes | Persisted theme store wired | All dialogs/contrast/layout not visually accepted |
| HUD | Native drag fix exists; free geometry, settings, lock/reset | Drag, resize, DPI, restart/recovery still need device acceptance |
| Hotkeys and help | Central catalog and failure reporting implemented | One running instance and actual registration/keyboard pass pending |
| Custom source teaching | Partial platform adaptation | Manual URL/CSS extraction, not Android WebView visual inspector |
| Source rejection | Implemented with provider mapping | Saved USER source still wins refresh; UX should not offer an ineffective reject action |
| Blacklist | Recording exclusion and rule management | Existing-library purge/undo available on Android, absent on desktop; UI explicitly says old records retained |
| Independent databases + optional merge | Independent local storage exists; merge missing as user workflow | Shared merge core has no platform callsites, no archive UI, backup workflow, or merge tests |
| Connect | Preserve validated phone-to-PC audio | Reverse casting is not validated parity |

## Confirmed findings

1. **P1, offline source text unavailable after cache loss/import.** `desktop/.../lyrics/DesktopLyricsService.kt:113-142`: Room `savedLyrics` is accepted only when `provider == "USER"`; all other saved records are ignored. Custom rules are fetched before disk cache. Rule results are saved to Room but not the `.cache` file. Consequently a rule result saved during one run has no offline reload path on a fresh service; imported provider text will have the same problem. Fix this first, independent of merge UI.
2. **P1 for the promised merge capability, missing workflow.** `shared/.../storage/LibraryArchive.kt` defines encode/decode/plan/snapshot/merge, but repository-wide Kotlin search finds no callsites outside that file. Neither platform can export/import the archive through its UI. There are no LibraryArchive tests. This is incomplete implementation, not a discoverability problem.
3. **P2, silent editor draft loss.** `desktop/.../ui/DesktopFeatureDialogs.kt:31,58-93`: DialogWindow close calls onDismiss directly; text fields are in-memory. Native close discards an edited draft. Add a save/discard/cancel prompt before calling this release accepted.
4. **P2, rejected user edits do not change result.** `DesktopLyricsService.kt:278-289` maps saved-user display source to builtin:web, inserts that rejection and forces refresh. At lines116-121 the USER record wins again. Disable reject for USER content or give a distinct reset-user-edit action with explicit semantics.

## Merge core review

Positive: JSON version/checksum/size checks; identity `(trackKey, sourcePackage, startTimeMs)` deduplicates session imports; incoming numeric IDs are discarded; one database transaction writes the plan; favorites combine, notes deduplicate, USER conflict policy explicit. These are implementation observations, not tested guarantees.

Before exposure:
- `plan`, lines66-78, updates aggregate counters only while iterating incoming tracks. A valid accepted snapshot containing incoming sessions for an existing local track but no incoming track does not update its stored counters; validation permits this. Either validate complete references or recompute every affected track. Also orphan session/lyrics references are accepted (schema has no foreign keys).
- Backups are absent from `merge`; no serialized desktop wrapper coordinates its in-memory activeSession with writes. UI must use a repository wrapper, snapshot a backup before changes and reload the active session after commit; otherwise a subsequent recorder tick can overwrite imported progress for that session.
- Only URI strings are serialized for cover art. Android content:// or another machine's local path does not transfer image bytes. Define missing-cover fallback honestly; do not promise cover portability.
- Add real Room transaction rollback, repeated-import idempotence, collision, USER conflict and malformed archive tests before enabling import. Preview must be recomputed when applying, never trusted as a stale write plan.

## Migration evidence

Desktop DatabaseFactory uses schema11 and no destructive fallback, but registers no migration path. Existing current-schema reopening is covered by repository tests. A pre-11 desktop database would fail to open; this audit did not establish that such a version was distributed, so it is an upgrade acceptance gap rather than a reproduced release regression. Do not reintroduce destructive fallback. Android retains its existing migration registrations/fallback, unchanged by this work.

## Recommended order

1. Bounded saved-lyrics hydration fix and deterministic offline/rejection tests (spec `specs/media-ui/desktop_offline_lyrics.md`).
2. Shared merge contract/tests, repository backup/apply lifecycle, then desktop and Android archive UI.
3. Editor dirty-close protection and explicit USER-source actions.
4. Runtime visual/keyboard/HUD/restart acceptance and packaged-build check; no release readiness declaration from unit totals alone.
