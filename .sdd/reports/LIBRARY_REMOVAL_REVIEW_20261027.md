# Review: library removal stage 1 — 2026-10-27

**FAIL — two state-consistency blockers; no runtime or Gradle/APK performed.** Reviewed main vs HEAD fa9d36a and canonical; scoped files main/canonical match report.

## P1 — failed commit leaks into later successful operations

`app/src/main/java/com/lirix/app/ui/library/LibraryExclusions.kt:25–37`: the code assumes `SharedPreferences.Editor.commit()==false` leaves `readKeys()` unchanged. Android SharedPreferences updates its in-memory map before disk write even if writing fails. Thus failed remove(A) returns false and does not publish, but the next remove(B) reads `{A}` from in-memory preferences and can persist/publish `{A,B}`, silently removing A despite the reported failure. `next==current` can itself report success without durable persistence. Tests contain no failure injection. Use a durable transactional store, or explicitly maintain/restore a checked authoritative snapshot and make error/retry semantics consistent even when rollback fails. Test false-commit mutation, subsequent successful independent add/restore and restart, including no false success.

## P1 — stale Undo reverses a later deletion of the same track

`LibraryScreen.kt:214–226`: remove A, restore A through the removed-items dialog while its first snackbar is still visible, remove A again, then select Undo on the first snackbar. `restoreTrack(track.trackKey)` unconditionally restores A, undoing the newer deliberate removal; a queued stale snackbar can produce the same result. Per-key operation generation/token (or equivalent invalidation of old action without dismissing unrelated messages) must be verified on restore/re-remove and interleaved tracks. UI/static source only; scenario has not been run.

## P2 — failed preferences exceptions bypass user-facing failure

`LibraryExclusions.kt:29–35` and `LibraryScreen.kt:223`: exceptional read/edit/commit errors propagate out of `scope.launch`, bypassing `library_remove_error` and potentially reach the coroutine exception handler. Catch expected persistence failures at boundary, preserve cancellation and avoid emitting success; add a failure test distinct from false.

## Checks and limits

- Common `visibleTracks` is built from the DAO stream and immutable removed-set StateFlow (`LibraryScreen.kt:153–167`); all track filters/search, artists and counts use it (`170–195`, `395–445`). No Room delete or filesystem deletion present in the new path. Exclusion applies regardless of playback updates to the same key.
- Menu is a per-row 48dp IconButton/DropdownMenu with track-specific TalkBack description; horizontal drag recognizer is removed. Parent `pointerInput` tap vs child IconButton interaction and font scaling require real Compose/device exercise; no conclusion from static code about hit-testing. Restore button remains visible with no tracks.
- Both backup rule files include `lirix_library_exclusions.xml` for legacy, cloud and transfer; manual LibraryArchive has no prefs field as correctly disclosed. RU/EN texts exist; verify language/rendering at runtime.
- Existing two Robolectric tests assert storage in same process across new manager and concurrent operations on one manager. They do not simulate process restart, failed commits, UI filter/update, queue races, other processes or recovery. Recreating a manager over the same SharedPreferences object is not proof of process restart. No tests were run.
- Track clicks, filter interactions, Snackbar/Undo, list recreation, Android backup, selection conflict and system gestures have not been verified by device tests. Existing docs claiming these checks must not be treated as runtime PASS.


## Second review — updated freeze

**FAIL / one remaining P1.** Per-removal token guards stale undo, serial mutex and `mutableRemoved` authoritative state prevent previously identified in-process false-commit contamination; expected exception returns failure and reports it. Changes reviewed statically only.

### P1: cancellation thrown after underlying commit skips compensation

`LibraryExclusions.kt:49–60`: `CancellationException` is rethrown before rollback. `FaultingPreferences.commit()` in the actual new test first calls `editor.commit()` and only then throws cancellation. After `store.remove("cancelled")`, the exposed flow remains empty but a new `LibraryExclusions(prefs)` immediately sees `cancelled`; after process death a successful disk write is also visible. Existing `cancellationIsNotReportedAsSuccess` checks only flow, then overwrites evidence via successful `remove("ok")`; it must check the new manager immediately after cancellation. Define and implement compensation or post-commit success/publish semantics, preserving cancellation, then test actual post-cancellation durable/manager state.

### Durability limitation

`write` attempts a compensating commit when the first commit returns false or throws, but ignores the compensating commit result. A failed initial write plus failed/interrupted compensation yields no confirmed durable result; the last published state is authoritative only for this live manager. A new process reconstructs from persistent/in-memory preferences, potentially divergent. The faulting wrapper calls real `editor.commit()` successfully before pretending both calls failed, which does not model failed disk I/O or restart; the tests cannot establish process-restart consistency after two real failures. Treat as explicit unresolved failure state/limitation, not a verified durable rollback; recovery or atomic storage mechanism needed if the spec requires a hard guarantee in that case. Recreating a manager over same preference object is not a process-restart test.
