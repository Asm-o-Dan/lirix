# Karaoke Studio model v1

Status: DEFERRED for current delivery. Latest user steering prioritizes existing karaoke foundations; no new reducer, migration or draft feature is shipped until separately resumed.

## Session and clock contract

Session: captured `trackKey`, title/artist, baseline plain/LRC, original baseline hash, ordered stable line IDs, nullable timestamps, selected line, undo/redo stacks, cursor ms, viewport span, optional loop. Existing valid LRC is preferred over plain parsing. Preserve duplicate text and equal timestamps. Plain-only import uses existing sanitizer once. Do not silently sort malformed timed input; show conflict and block Apply until resolved. Blank lines are not timed vocal entries.

All editor timestamps use raw media time, never the currently adjusted lyric clock. Tap reaction delay is applied exactly once to a newly tapped stamp; direct numeric, drag and +/-50 ms edits do not apply it. Preview uses the session line model and raw time. Runtime per-song offset is separate and must not be baked into exported LRC.

Retain Android's sign: positive advance displays text earlier; inverse seek subtracts effective offset. Separate song correction and device compensation additively. Compatibility migration: for a legacy explicit per-track offset L and legacy global G, store song correction L-G; absent per-track means 0. Effective correction must remain L (or G if absent). Migration is idempotent. Keep legacy calibration limits initially; long intros are handled by actual line timestamps, not arbitrarily expanded offsets. Device setting describes this device/output calibration, not automatic detection of every Bluetooth route.

## Reducer and history

### Frozen implementation API

`KaraokeStudioEngine.createSession(trackKey: String, plainText: String, lrcText: String?, durationMs: Long): KaraokeStudioState` returns immutable ordered state. `reduce(state: KaraokeStudioState, edit: KaraokeStudioEdit): KaraokeStudioResult` returns `Accepted(state)` or `Rejected(reason)`; invalid edits return the original state unchanged. `exportLrc(state): String?` returns null unless every line is marked and chronology/bounds are valid. Reducer actions are `SetTime(index,timeMs)`, `Stamp(index,rawPositionMs,reactionLagMs)`, `Nudge(index,deltaMs)`, `ShiftTail(index,deltaMs)`, `RestoreOriginal`, `Undo`, `Redo`. Selection/cursor/mode updates are explicit state copies outside content history. `parseTimeInput(value: String): Long?` is pure and rejects malformed/overflow input.

`SyncOffsetStore.getSongOffset(trackKey: String): Long`, `setSongOffset(trackKey: String,value: Long): Long`, `getDeviceOffset(): Long`, `setDeviceOffset(value: Long): Long`, `getEffectiveOffset(trackKey: String): Long` expose clamped actual values. Preserve legacy `getOffset`/`setOffset` as effective-value compatibility wrappers (set maps effective minus device into song correction). On first per-track access, if no versioned song key exists, calculate legacy effective from existing explicit key or global; persist versioned correction = legacyEffective - device, in the same edit as migration marker. Keep global as device setting; clamp effective by established limits and allow internal difference up to +/-6000 to preserve endpoints. Do not eagerly migrate on constructor, because later access may encounter more legacy tracks. New song setter clamps effective desired sum without altering device calibration.

`KaraokeStudioDraftStore.load(trackKey): DraftLoadResult`, suspend `save(draft): Result<Unit>`, suspend `remove(trackKey): Result<Unit>`; payload includes schema version, exact trackKey, original baseline and current state. Saves use Mutex per repository plus AtomicFile or equivalently verified replacement. A draft store instance is scoped outside recomposition and shared by both editing modes. UI reports save error and keeps current memory state.

`applyKaraokeStudioSession(session, expectedSavedLrc, generatedLrc): ApplyResult` runs both DAO changes in Room transaction. `ApplyResult` distinguishes Success, Conflict and Failure. UI session captures title/artist/album-derived key and source package; controls require metadata identity match, not just package match. Verify action flags with actual controller before seek. Do not treat collector's optimistic local seek snapshot as player acknowledgement.

One pure edit reducer applies set timestamp, +/-50 ms, stamp, shift tail, restore baseline, undo and redo. Successful content mutations push one previous state, clear redo; selection, playback ticks and viewport changes do not enter undo. A drag pushes exactly one history item on completion; cancellation restores the pre-drag state. Bound history to 100 entries.

Reject invalid indices, overflow and negative results with a typed user-visible error. Marked times must be nondecreasing in source order and within known duration. A move across neighbors is rejected rather than silently moving another line. Tail shift applies one exact delta to all marked lines from selected index onward; null timestamps remain null. Reject the entire tail operation if any resulting time/boundary is invalid. No per-line clamping which changes relative rhythm.

Numeric time accepts `mm:ss.SSS` (seconds 00..59; optional fraction 1..3 digits), not locale-dependent floats. Empty/malformed input does not mutate. LRC export reuses existing engine centisecond format; acknowledge up to 9 ms serialization truncation. v1 adjustments use 50 ms steps. Apply requires nonempty all-marked lines and valid chronology. Partial work remains a draft.

## Draft and Apply contract

Private versioned JSON keyed by SHA-256 track key, atomic temp-write/replace off main thread, serialized per track; original key verified inside payload. Persist original baseline, current lines, selection and UI mode. In-memory history need not survive process death. Cap payload (2 MB, 5000 lines); corrupt/unknown versions fail safely with recoverable message and never overwrite working lyrics. Autosave content changes with short debounce plus immediate flush on explicit close/mode transition. Preserve draft on failed persistence and expose retry. Do not depend solely on Activity onStop for durability.

Restore original copies captured baseline into draft and is undoable; does not modify library until Apply. Preserve baseline after applying so restore remains available on later reopening. Explicit Apply compares current saved lyrics with the session opening baseline (or last successfully applied snapshot). On conflict require reload/explicit replacement rather than overwriting another edit. A Room transaction updates both lyrics cache and track lyrics. Read current cache inside transaction to retain latest chords/notes; no stale captured fields. Failure leaves dialog, lines and draft intact; success acknowledges and marks clean only after commit. Post-success display cache update is guarded by matching track key. Never delete the only recoverable original during cleanup.

## Required verification

Repeated text/equal times; valid and malformed imports; negative/overflow/tail-boundary rejection; drag coalescing; redo invalidation; reaction applied once; per-track migration exact preservation; JSON corruption and interrupted replacement; draft isolation A/B; failed/conflicting apply; restoration before and after apply. No content network calls.
