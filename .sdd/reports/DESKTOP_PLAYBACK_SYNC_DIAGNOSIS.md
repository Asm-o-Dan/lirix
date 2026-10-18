# Windows playback synchronization diagnosis

Status: source defects corrected and independently reviewed; 24 native tests passed. Packaging and executable/jar hash verification passed (see `DESKTOP_PLAYBACK_SYNC_BUILD.md`). Physical browser acceptance pending.

User evidence: clicking a karaoke line seeks YouTube to approximately 56 seconds while Lirix still displays approximately 7 seconds. Artwork remains from another track. No song text reproduced.

## Position pipeline

`DesktopDeckScreen` sends the timestamp through `DesktopGsmtcIngressProvider.seekTo` and `BridgeProcessManager.sendCommand`. Native `GsmtcWatcher.ExecuteCommandAsync` awaits `TryChangePlaybackPositionAsync` but discards its success result and never updates `_baselinePositionMs` / `_lastPositionTimestamp`. The native periodic timer only extrapolates that old baseline; it does not reconcile the actual source timeline. Recovery depends entirely on a `TimelinePropertiesChanged` event. When the browser accepts seek without delivering a timely usable event, the application keeps publishing its pre-seek position.

Kotlin ingress updates its snapshot when timeline messages arrive. Shared desktop monotonic time and ingress both use `System.nanoTime()/1_000_000`; no differing epoch was found. UI clock interpolation is downstream of the incorrect native anchor.

Further race: asynchronous full-state thumbnail reads are neither serialized with timeline updates nor guarded against a switched session. An old read can publish stale metadata/position after a newer selection.

## Artwork pipeline

`SaveThumbnailAsync` overwrites one global temporary filename `lirix_thumb.png` for every track. `DesktopDeckScreen` and `DesktopCompactWidget` decode inside `remember(playback?.albumArtUri)`. Because the URI remains unchanged, Compose retains the old decoded image. Persisted library tracks also reference the same mutable file, so merely adding title to the UI remember key would leave historical/poster artwork wrong.

The native track-key cache also skips reading changed thumbnail bytes for unchanged title/artist. This misses delayed browser artwork and same-track media-property updates.

## Minimal correction boundary

Native watcher plus independently testable clock and immutable thumbnail helpers; regression tests in the existing native test project. Keep the existing JSON schema and Kotlin UI. Content-addressed paths naturally invalidate both existing UI remember caches and preserve history references. Rebuild and bundle the native executable; a fresh Kotlin jar alone does not ship this fix.

Native bridge test success and packaged executable hash must be recorded before marking verified. Browser acceptance remains a physical runtime check; source diagnosis does not constitute that check.

## Implemented correction and reviewed limits

Successful native seek immediately publishes a clamped anchor; failed seek leaves it unchanged. Source polling continues while paused, and timestamped source observations cannot undo a newer accepted seek. Undated sources use an explicit 1500 ms best-effort grace. Session selection, metadata and seek generations prevent delayed callbacks from publishing stale state; session reassignment and event subscription occur together under the state lock.

Artwork now uses immutable SHA256 content paths under LocalAppData, refreshed even for unchanged track metadata. Historical references are not evicted blindly; per-image size is limited to 16 MiB and equal bytes deduplicate. Previously corrupted historical references to the shared temporary path cannot be reconstructed.

Independent review: `DESKTOP_PLAYBACK_SYNC_REVIEW.md`, final native suite 24 passed, 0 failed/skipped (17 new regression cases plus 7 protocol cases). Native GSMTC/device acceptance remains pending.
