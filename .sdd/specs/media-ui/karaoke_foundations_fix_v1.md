# Existing Android karaoke foundations — 2026-10-20

User steering supersedes advanced Studio delivery: first verify/fix offset, seek, timing save, clipped rows and duplicate Connect. Advanced engine, timeline, persistent drafts, additive device/song migration and new entry points are DEFERRED. Preserve unfinished worker changes outside compiled sources; no broad reset, no user input deletion.

## Frozen bounded contracts

1. `boundedSyncOffset(value: Long): Long` returns value clamped to existing SyncOffsetStore MIN/MAX; every portrait/landscape offset change uses this result for both UI and old per-track setter. Preserve existing persisted preference keys, global fallback and effective meaning. No migration. Positive offset advances lyric clock: `lyricTime=raw+offset`; seek inverse `raw=lyricTime-offset`, clamped to0 and known duration. Centralize mapping as pure `karaokeLyricPosition(rawMs,offsetMs):Long`, `karaokeMediaSeekPosition(lyricMs,offsetMs,durationMs):Long` or equivalently existing helper methods with these signatures; avoid long overflow. Preserve negative adjusted lyric positions internally so a time-zero line is not activated prematurely by negative calibration; presentation progress may clamp separately. Paused snapshots still update on seek/offset reset.
2. `TapSyncEditingTarget(trackKey,title,artist,plainLyrics,initialLrc)` captured when opening the existing dialog, never derived from subsequent currentTrack recomposition. Existing editor remains quick Tap-to-Sync. Extend callback to suspend `onSaveLrc: suspend (String)->Unit`, show busy, await commit before success/close; preserve lines on errors, retry enabled. On source/track switch disable new stamps and transport using wrong-song time. Existing working timed lines must not be silently destroyed merely by reopening.
3. `saveTapSyncLyrics(target,generatedLrc): LyricsCacheEntity` reads latest cache and writes cache plus track lyrics in one Room transaction. Preserve latest chords and notes. Transaction failure rolls back both writes; returned success updates on-screen cache only if current track key still matches captured target. Do not swallow exception or display false success. Existing save path should be testable via repository/DAO integration.
4. `HighFidelityKaraokePlayer`: stable occurrence-safe list keys (index+timestamp or equivalent); stable row font metrics while alpha changes; full first/last wrapped text reachable in available viewport. Before first timestamp no line is marked active. Long press seek still uses inverse mapping. Do not hide lyrics to fit transport or use ellipsis for active karaoke text.
5. Portrait header retains one Connect entry; remove only redundant portrait transport copy. Landscape/empty-state access retained. No navigation redesign or unrelated controls removed.

## Tests and gates

- Offset limits/reopen/global fallback/per-track isolation; positive and negative inverse mapping, known duration bounds and paused seek.
- Save failure and retry, transaction rollback, preserve latest notes/chords; captured A while playback changes to B must save only A and leave B display unchanged.
- Duplicate timestamp and repeated lyric occurrence keys; long intro no premature active line; first/last multiline at small portrait/landscape and larger fonts.
- Existing Tap-to-Sync, shared/desktop regression suites, Android debug build. Native source seek/phone screenshot only if actually available, otherwise explicitly pending.

One component/function per card: KBF-001 mapping/bounds, KBF-002 reader geometry, KBF-003 captured transactional save, KBF-004 quick dialog await/identity guard, KBF-005 portrait Connect deduplication. No new advanced functionality in this package.
