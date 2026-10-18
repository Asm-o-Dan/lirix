# Desktop saved lyrics hydration

Status: APPROVED and implemented 2026-10-18. Desktop 62 tests and shared 173 tests passed (shared task UP-TO-DATE); independent source review found no blocking issues. Native verification remains separate.

Problem: DesktopLyricsService.onTrackChanged ignores Room saved text unless provider USER. Saved rule text and future imported provider text cannot reliably reload offline.

Contract:
- On ordinary track selection, after cancellation and track identity setup, accept a nonempty persisted lyrics record before custom-rule or built-in network access. Existing USER content remains authoritative even on explicit refresh.
- For non-USER records, explicit forceRefresh bypasses saved results. An explicitly rejected provider must not reappear from Room or memory/disk cache. Canonicalize existing display/provider aliases consistently with rejectCurrentSource: LrcLib variants/LRCLIB → builtin:lrclib; AmDm / MyChords/AMDM → builtin:amdm; rule:* unchanged; Web Scraper → builtin:web. Unknown provider stays usable unless explicitly rejected by its identity; NONE/empty-content is not a positive hit.
- Preserve plain text, exact LRC, chords, title/artist/key and source identity. Parse real LRC; do not synthesize timestamps for plain text. No stale publication after track switch/cancellation.
- Do not mutate DB or call network when a usable saved hit resolves ordinary selection. Persistence errors remain visible; cancellation propagates.
- Every asynchronous branch must recheck cancellation and current track after its last suspension, including repository save, before publishing state or caching an automatic result. Network memory/disk cache must not be written before the relevant repository acceptance; cancelled stale results must not remain as a later cache hit. Rule and built-in branches follow the same rule.
- Keep existing constructor callsites source compatible. Use narrowly scoped injected lookup/network seams if deterministic verification needs them; default production behavior unchanged outside this contract.

Acceptance: fresh service loads rule and LRCLIB Room records with no disk file/network; USER still wins refresh; rejected provider is skipped; explicit refresh bypasses non-USER; blank/NONE records continue lookup; late previous-track lookup cannot publish; chords-only and genuine LRC preserved. Existing karaoke/Connect suites remain green.

## User-edit concurrency contract (selected by coordinator)

`saveUserLyrics` must cancel and join the relevant active lookup before suspending in its persistence write, then save USER and publish only if still current. Saving a different library track must not cancel the current track lookup. Capture the job to cancel/join; do not accidentally cancel a newer unrelated job after suspension. If playback changes during save, persist the edit but do not replace that new track's visible state.

`DesktopLibraryRepository.saveLyrics` owns a second invariant inside its existing mutex: an automatic/non-USER write cannot replace an existing USER record. USER writes remain allowed (including deliberate clearing), and automatic writes remain allowed when no USER exists. No read-before-lock check. This protects durable content even if another automatic caller escapes cancellation. Keep cache and music_tracks lyrics consistent with the effective stored version. Existing callers must not publish stale automatic results over a USER edit; cancellation/join and current-track guards are required in addition to the storage invariant.

Frozen deterministic regression protocol:
1. Use a real temporary Room database, isolated disk cache path and injected automatic loader (or equivalent narrow service dependency) returning Deferred-controlled results; no live HTTP.
2. Start lookup A; hold automatic result before publication/write. Save USER for A. Verify relevant lookup cancellation completes before USER repository write and final UI+Room+Track all contain USER after releasing the held loader. Use a cancellation-cooperative loader; also exercise repository automatic write after USER directly to prove defense independent of job cancellation.
3. Hold USER repository save for A, switch playback to B, finish A save; A persists and B UI remains B. A save must not cancel B lookup.
4. Save library track C while current lookup B is pending; B completes normally.
5. Reopen database and a fresh service; USER and non-USER offline hydration assertions hold. Ensure scoped jobs terminate before database close.

Testing seams may expose suspend saved lookup/automatic lookup and isolated cache directory as constructor defaults. Production still uses repository/network/disk adapters. Do not create tests that merely call a new helper unused by the production service. Test cancellation and write ordering using Deferred/barriers, never arbitrary sleeps. QA owns tests; implementation and QA agree the exact seam before concurrent edits to the same file.

## Exact constructor seam agreement — 2026-10-18

QA and implementation agree the following optional trailing constructor parameters, approved by the root coordinator on 2026-10-18:

```kotlin
cacheDirectory: File? = null,
savedLookup: (suspend (String) -> LyricsCacheEntity?)? = null,
persistLyrics: (suspend (String, String?, String?, String?, String) -> Unit)? = null,
automaticLookup: (suspend (String, String, String, String, Set<String>) -> DesktopLyricsState)? = null
```

The writer arguments are key/plain/LRC/chords/provider; automatic lookup arguments are rawTitle/rawArtist/cleanTitle/cleanArtist/rejected canonical IDs. Defaults use the real repository, existing filesystem cache location and existing built-in provider search. The automatic override replaces only built-in network work after saved/custom-rule/disk checks; tests create no active network rules. The service normalizes lookup-result key/title/artist to the request and retains production guards and persistence around injected results. An empty-content result means a miss. Minimal extraction of the existing built-in lookup into a private suspend member is allowed solely to provide this shared execution path; provider behavior must not otherwise change.

Tests use temporary Room and a temporary explicit cacheDirectory. Saved-read and writer wrappers delegate to the actual repository, with CompletableDeferred gates where suspension ordering is the assertion. The direct repository protection test uses no injected writer. No real HTTP, user preferences or application cache files are touched. QA owns initial RED compile/test run and signals its completion before implementation edits begin.
