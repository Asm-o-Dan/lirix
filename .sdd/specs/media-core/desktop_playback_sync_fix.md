# Desktop playback synchronization correction

Status: implementation specification, Windows only.

## Invariants

1. An accepted karaoke/slider/hotkey seek reanchors and publishes the native position immediately. Rejected seek must not fabricate movement.
2. A stale GSMTC timeline sample must not rewind a newer accepted seek. A newer real timeline sample must reconcile external seeks, including while paused.
3. Interpolation uses monotonic elapsed time; GSMTC `LastUpdatedTime` is a source freshness marker and is converted to a current position once, with bounded nonnegative elapsed time. Repeated identical samples do not repeatedly add elapsed time or reset the anchor.
4. Pause freezes position, duration clamps known timelines, closed/session change clears old anchors. An old asynchronous callback cannot publish into a newer current session.
5. Thumbnail paths identify immutable bytes. Equal bytes reuse the same path; unequal bytes never share a path, even for the same track. Publish only a fully written file. Missing artwork publishes null, never a previous track's path.
6. Existing protocol fields and Kotlin consumers remain compatible. Do not reset song offsets or modify lyric timestamps to hide clock errors.

## Atomic functions / ownership

| Card | Function | Acceptance |
|---|---|---|
| SYNC-WIN-001 | TimelineAnchor.Observe | Apply newer source sample, preserve accepted seek against stale samples, freeze paused timeline |
| SYNC-WIN-002 | TimelineAnchor.SeekAccepted | Clamp target and replace monotonic baseline after success only |
| SYNC-WIN-003 | TimelineAnchor.Position | Deterministic interpolation/clamping without wall-clock drift |
| SYNC-WIN-004 | ThumbnailStore.SaveAsync | Hash bytes, atomically persist immutable file, reuse equal content |
| SYNC-WIN-005 | GsmtcWatcher.ExecuteCommandAsync seek branch | Honor native bool result, check current session identity, publish accepted anchor |
| SYNC-WIN-006 | GsmtcWatcher timer/event reconciliation | Poll source freshness, guard session identity, emit coherent position |
| SYNC-WIN-007 | GsmtcWatcher full session publication | Guard async ordering and refresh artwork content without mutable track-key cache |
| SYNC-WIN-008 | prepareBridgeResources | Stage tracked native executable into Compose windows resource directory and require it before packaging |

Native implementation owner: sync_fix_coder. Tests owner: sync_regression_qa. Coordinator owns review and sequential final builds. No Gradle overlap and no termination of a running Lirix.

## Exact helper contracts

- `TimelineAnchor.Observe(long position, long duration, DateTimeOffset updatedUtc, bool isPlaying, DateTimeOffset utcNow, long monotonicMs): void`. Caller holds watcher state lock. Negative duration becomes unknown (zero); position is nonnegative and displayed within known duration. Timestamped samples at or before an accepted seek barrier cannot replace the anchor. A changed undated sample is accepted only after a 1500 ms monotonic grace following seek: source ordering without timestamps remains best effort. An identical sample never resets the anchor. Source age is added once while playing, bounded to nonnegative `int.MaxValue` milliseconds.
- `TimelineAnchor.ApplySeek(bool accepted, long position, long monotonicMs, DateTimeOffset acceptedUtc): void`. False leaves all state unchanged. True clamps target and sets source freshness barrier. Production uses this overload; three-argument overload is a pure reanchor compatibility seam without UTC barrier.
- `TimelineAnchor.Position(long monotonicMs): long`. No mutation; paused clock does not advance and negative elapsed is zero.
- `ThumbnailStore.SaveAsync(byte[] content, string directory): Task<string>`. SHA256 filename, immutable content, same-directory staging and atomic move. Concurrent equal writes reuse winner. I/O errors propagate; watcher catches them and publishes null artwork. Watcher rejects empty and greater-than-16 MiB streams before allocation. Staging files removed in finally.
- `GenerationGate.Current: long`, `Next(): long`, `IsCurrent(long generation): bool`. Thread-safe generation read, increment and comparison; watcher holds its state lock across final checks and publication. Manager selection-pending barrier rejects old callbacks until session refresh installs current selection. A distinct manager generation is captured before reading the selected session and rechecked before assignment, subscription, closed publication and clearing the pending barrier.

Thumbnail lifecycle: persistent `%LOCALAPPDATA%/Lirix/cache/thumbnails`, no premature deletion because library stores these paths. Per-item size and byte dedup are bounded; aggregate cache is intentionally not capped without a reference-aware garbage collector. Existing historical references to the old mutable temp file cannot be reconstructed automatically.

Packaging correction: Compose resource roots consume platform subdirectories, not loose files in the root. `prepareBridgeResources` stages the tracked executable into `build/generated/bridgeResources/windows`; `prepareAppResources` depends on it and native distributions consume the generated root. Result must contain `Lirix/app/resources/lirix-media-bridge.exe`, resolved by the existing `compose.application.resources.dir` lookup. Hash verification is mandatory because the prior successful image omitted the executable and could fall back to an older developer/installed bridge.

## Required regression cases

- Playing at 7000 ms, accepted seek to 56000 ms: immediate emitted position 56000, next tick advances from it.
- Rejected seek preserves old baseline.
- Identical pre-seek source timestamp after accepted seek cannot reset to 7000.
- New source timestamp after external forward/backward seek wins; paused external seek remains stationary.
- Repeated sample does not freeze playback or double count elapsed time; duration bounds and negative elapsed are safe.
- Session replacement during thumbnail await cannot publish old metadata or change current anchor.
- Artwork bytes A then B yield distinct valid files; A again returns same path; same track with B is refreshed; concurrent equal writes leave one valid immutable file.
- Existing desktop/shared tests remain green, and published native executable equals the packaged executable by SHA256.

## Runtime gate

On the current browser: seek by karaoke row, slider, browser timeline, backward seek, pause/seek/resume, next/previous track with visibly different artwork. Compare browser and Lirix within approximately one second; confirm active lyric follows source. These checks require the fresh packaged bridge and are not replaced by screenshots of an older running build.
