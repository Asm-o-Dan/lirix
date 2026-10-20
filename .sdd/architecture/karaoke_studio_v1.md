# Karaoke Studio v1 — Android, 2026-10-20

Status: DEFERRED advanced implementation contract. Latest user steering requires existing karaoke foundations first; current delivery follows `.sdd/specs/media-ui/karaoke_foundations_fix_v1.md` and KBF cards. No product code changed by this document.

## Existing architecture and verified risks

- `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt` owns ephemeral `remember` lines parsed only from plain lyrics; existing LRC is not supplied. Playback reads `MediaSessionCollector` directly, reaction compensation defaults to 200 ms, undo clears the preceding stamp. There is no durable draft or redo. Opening advanced editing must not reparse existing timed lines through the plain-text sanitizer.
- `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt` opens the editor via a Boolean and recomputes its track from `currentTrack`. Save closes immediately, then performs two independent DAO writes and updates displayed cache unconditionally. Changing songs while editing can therefore switch the editing target. Capture track and baseline when opening, await transactional save, update displayed cache only if its track still matches.
- Android rendering currently uses `lyricPosition = rawPosition + syncOffsetMs`; seek inversely subtracts the offset. `SyncOffsetStore` (`app/src/main/java/com/lirix/app/ingestion/LivePlaybackSnapshot.kt`) falls back from per-track to global calibration. Screen buttons mutate unbounded state while the store clamps persisted values to +/-3000 ms: UI and saved values can disagree.
- Portrait contains Connect in both header and transport. Keep the header entry and remove the duplicate transport entry; leave landscape and empty-state access intact.
- `HighFidelityKaraokePlayer` uses timestamp as LazyColumn key (duplicates are legitimate), changes row font metrics with active state, and scrolls before/while those metrics animate. Use occurrence/index keys, stable row geometry, measured available height and safe first/last padding. Verify clipping with actual large fonts and long lines; screenshot reports are not proof of a single cause.
- `shared/.../feature/lyrics/LrcSyncEngine.kt` supplies `SyncLineItem`, stamping and LRC serialization. Its strict parser sorts timestamps; editor import must preserve source ordering and repeated occurrences instead of silently repairing invalid order.

## Ownership and bounded scope

One captured session owns track identity, initial original plain/LRC, current line list, selected index, undo/redo, cursor and viewport. Tap-to-Sync and Studio are two modes of that session. Switch modes without serializing/reparsing. Existing quick tapping remains the default. No desktop redesign, waveform decoding, word timings, romaji, translations or new player engine in this increment.

Pure editing model belongs to `shared/.../feature/lyrics/KaraokeStudioEngine.kt`. Android private persistence belongs to `app/.../ui/components/KaraokeStudioDraftStore.kt`; no Room migration is required. Android UI is split into `KaraokeStudioTimeline.kt`, `KaraokeStudioDialog.kt`, and adapted `LrcTapSyncStudioDialog.kt`. The root coordinator may choose equivalent file boundaries but must preserve contracts.

## Gates and handoff

1. G0 baseline fixes: KST-001..003; verify seek inverse, persisted bounds, one Connect entry and first/last long-line visibility.
2. G1 pure editing and durability: KST-004..007; tests before UI integration.
3. G2 Studio UI and shared quick mode: KST-008..010; no fake waveform, no unsupported seek.
4. G3 integration and acceptance: KST-011..012; transaction failure, track switch, process restart, interrupted save, long intro, single-line correction and chorus tail shift.

Specs: `.sdd/specs/lyrics-engine/karaoke_studio_model_v1.md`, `.sdd/specs/media-ui/karaoke_studio_android_v1.md`. Atomic tasks: `.sdd/tasks/KST-001.md` through `KST-012.md`. Each component/function card is independently reviewable; helpers within it require the same contract. Unit tests prove model/draft/save invariants; physical acceptance still required for MediaSession source seeking and rendering on phone.
