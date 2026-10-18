# Desktop playback synchronization: independent review

Date: 2026-10-18. Reviewer: sync_regression_qa; implementation author: sync_fix_coder.

## Verification

`dotnet test native/LirixMediaBridge.Tests/LirixMediaBridge.Tests.csproj --no-restore -p:PublishAot=false --logger "trx;LogFileName=playback-regressions-final.trx" --results-directory desktop/build/playback-sync-qa`

Result after the final manager-generation and atomic-subscription corrections: **24 passed, 0 failed, 0 skipped**, 249 ms reported test duration. The native watcher compiled with this run. Seven existing protocol cases and seventeen new regression cases executed. No Gradle or running Lirix process was touched by this reviewer.

New cases verify source sample age; monotonic interpolation; repeated sample continuity; pause/resume with an unchanged sample; immediate accepted seek; rejected seek unchanged; unchanged pre-seek sample rejection; delayed timestamped sample older than accepted seek rejection; newer forward/backward external seek; paused external seek; duration bounds; undated sample grace and later reconciliation; immutable image paths for different bytes; equal-byte reuse and changed-byte replacement; concurrent atomic image writes; asynchronous generation invalidation.

The first undated fallback test failed because a default timestamp could never supersede a prior dated sample, even after the grace interval. The implementation author corrected the predicate. The regression assertion was retained and passed in the final run. Test results are ignored build artifacts under `desktop/build/playback-sync-qa/`.

## Implementation review

- Accepted native seek success reanchors under `_stateLock`, verifies session identity and seek generation, and publishes immediately. A rejected seek does not fabricate a new position. Timestamped source samples at or before the acceptance wall-clock barrier cannot undo it.
- `TimelineAnchor.Observe` preserves monotonic progress for identical snapshots, freezes while paused, and resumes from the frozen anchor. Aged playing source positions receive their nonnegative bounded age once. The timer polls even when paused, enabling external paused seeks.
- Playback events trigger a full session message in addition to reconciliation, preserving the existing protocol's `isPlaying` propagation. Timer messages still contain position only; missed source playback events are not independently repaired as playback-state messages by polling.
- Full metadata publication checks the metadata generation, current session identity, pending selection and disposal under the same state lock used for publishing. Playback and timeline are reread after thumbnail I/O. Timer output is gated until a full session revision has been published.
- Artwork is read on each full metadata refresh and stored under its content hash via a separate staging file followed by an atomic move. Different contents do not overwrite a previously published path. Missing or failed artwork produces null rather than reusing another track's image. Same-track artwork replacement no longer depends on track title equality.
- `_selectionPending` prevents callbacks from an old current session starting a fresh metadata revision while an earlier refresh is awaiting thumbnail I/O and a manager refresh is queued. This closes the initially identified callback-during-await race.

## Source-level findings resolved

**Manager selection read and pending reset were not generation-coupled.** The reviewed intermediate implementation could read B, receive a newer manager event selecting C, then clear the newer pending marker and publish B before the queued C refresh. The same-session early-return branch had an analogous pending reset.

**Resolved in final source:** manager events advance `_managerRevision` under `_stateLock`; refresh captures `Current` before reading the manager and verifies that generation under `_stateLock` before both same-session pending reset and changed-session assignment. Obsolete selections return with the pending marker intact for the queued refresh.

The first correction introduced a second gap: assignment and subscription were in separate lock sections, so a newer manager event could interrupt them and leave the newly assigned current session unsubscribed permanently through the same-session branch. **Resolved in final source:** old-session unsubscribe, new-session assignment, pending reset, clock reset and new-session subscription (or closed publication) now occur in one token-checked state-lock section with no await. Independent source review confirms both findings addressed. Neither scenario was reproduced against a physical GSMTC session.

## Limits and runtime gate

The 1500 ms monotonic grace for undated source samples is an explicit fallback: it suppresses an immediate stale echo, then permits changed unversioned positions. After the grace period an unversioned sample cannot be proven newer than a local seek. This is not equivalent to timestamped freshness guarantees.

The asynchronous generation test exercises the production gate primitive with delayed completions; it does not instantiate Windows GSMTC or simulate every watcher event interleaving. The manager-generation and subscription findings above were verified through independent source review and final compilation; the reviewer has no remaining blocking source-level finding within this correction's scope.

No physical browser/Lirix seek, pause/resume, artwork transition, installation or packaging hash check is asserted by this report. Those remain coordinator/runtime gates with the newly packaged native executable.

## Packaging source review

The final package gate found that placing the executable directly at `appResourcesRootDir` did not include it in the Compose application image. The build-script correction was independently reviewed without starting a build or launching the app:

- `prepareBridgeResources` is a `Sync` task staging `desktop/resources/lirix-media-bridge.exe` into `desktop/build/generated/bridgeResources/windows/`; its source is declared as input and missing source fails explicitly.
- `prepareAppResources` depends on the staging task. `nativeDistributions.appResourcesRootDir` points to the generated root, with the executable inside the Windows-specific directory expected by Compose's resource selection.
- The existing source resource directory contains only this executable, so the new root does not omit other currently present resources.
- `BridgeProcessManager.findBridgeExecutable` checks `compose.application.resources.dir/lirix-media-bridge.exe` before development/AppData/install fallback locations. Thus the expected image location is `Lirix/app/resources/lirix-media-bridge.exe`, subject to the packaged `Lirix.cfg` resource-directory setting.

Source review passes. Coordinator must still verify the executable actually exists at that image location, inspect the resource-directory setting, and compare its SHA256 with the newly published native executable. This section does not assert artifact/hash success or a runtime launch.
