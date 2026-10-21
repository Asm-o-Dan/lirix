# Android player context actions v1

Status: APPROVED v1 — bounded implementation authorized by root against the user screenshot requirements.

Design read: preserve native Compose Material 3, current Lirix themes; quiet reading surface, readable attribution and context-specific commands. No new dependencies.

## Evidence

- NowPlayingScreen.kt:1426 mounts NowPlayingOverflowMenu as a sibling of the entire deck, outside every MoreVert button (605/670/1033). Material DropdownMenu anchors to its parent layout, so popup uses the deck bounds rather than the top-right button. Fix placement, not hard-coded screen coordinates.
- NowPlayingOverflowMenu.kt has unconditional song actions, including default no-op reject-source; its sole caller is NowPlayingScreen. AppScaffold switches NowPlaying/Library/Wrapped (History is under Library) and does not share this popup. Do not invent a Library global overflow or put current-track commands there. Existing per-track Library actions stay intact.
- Portrait topbar Connect at 1022 duplicates overflow Connect; landscape already has transport Connect at 767; empty player repeats Connect in header and primary CTA.
- RenderTeleprompterContent LYRICS at 1771 renders Edit/Sync toolbar; shared TranslationPanel renders language/import/display controls plus repeated artist/title and explanatory paragraph. Shared panel is also used by desktop: new presentation options must default to existing desktop behavior.
- Current working TranslationPanel already conditionally shows some attribution only while importing. Android compact mode must restore always-accessible source/author attribution, not hide it further.

## Contract/action matrix

| Context | Context actions | Existing global actions |
|---|---|---|
| Text + song | Edit lyrics; Synchronization studio; Translation settings (language, Original/Translation/Together, import URL); Teach/search | Overlay; theme; blacklist |
| Karaoke + song | Edit lyrics; Synchronization studio; timing calibration; Teach/search | Overlay; theme; blacklist |
| Chords + song | Teach/search for chords; existing transpose/scroll controls remain in chords | Overlay; theme; blacklist |
| Notes + song | Save notes (same existing callback); no unrelated lyric/calibration/translation commands | Overlay; theme; blacklist |
| Empty player | No track-only commands | Overlay; theme; blacklist |
| Library/History/Wrapped | Keep existing local actions; do not add player overflow | Existing screen controls unchanged |

Poster remains reachable by transport Share. Source rejection is hidden until an actual callback exists; current no-op menu must not ship. Connect is not an overflow item: portrait transport order Connect/Previous/Play/Next/Share, landscape existing single transport Connect, empty player single primary Connect CTA. All tap targets >=48dp, play larger.

## Bounded function cards

1. `NowPlayingOverflowMenu`: nullable callbacks define actual supported actions; remove Connect/no-op source rejection, add Sync/Translations/Save notes, preserve global actions. Material DropdownMenu supports scrolling and viewport bounds, bounded width with wrapping text; render beside its IconButton inside an anchored Box.
2. `NowPlayingScreen`: factor anchored overflow trigger used in all layouts; callbacks filtered by mode/actual track. Dismiss on mode, track, orientation changes. Remove top Connect; add portrait transport Connect; retain landscape transport; empty single CTA. Remove standalone deck popup.
3. `RenderTeleprompterContent`: remove Android Text toolbar; pass remembered Android translation presentation state to shared panel. Overflow opens a modal settings sheet for language/display/import. Same live document/selection retained while toggling sheet.
4. `TranslationPanel`: opt-in compact Android presentation only. Existing desktop default unchanged. Android reading view: no duplicate track title, no white explanatory paragraph; one readable source/author action (bodySmall/labelLarge), ellipsis plus details sheet containing full author/source URL and Open source. Import preview may retain title/version validation. Existing search errors/retry/candidate selection/import confirmation/offline persistence remain reachable.
5. Android translation settings sheet: bounded height/scroll, native insets and font scaling; dismiss on context change; does not restart network searches merely on open. Language/display/import retained after sheet closes.

## Verification criteria

- Actual Compose popup test: click visible MoreVert in small portrait/landscape and RTL; popup bounds stay within root and adjacent to trigger; large font items scroll and remain clickable. Existing Robolectric present; Compose test dependency currently androidTest only, so prefer instrumentation without adding dependencies. If device unavailable record this gate explicitly.
- Actual rendered tree context reachability: Text exposes translations/edit/sync; Karaoke calibration/sync; Chords no translation/calibration; Notes save, no lyrics commands; empty no track actions. Context switch dismisses popup and sheet. Verify callback invocation, not a duplicated enum matrix.
- Exactly one visible Connect action in each layout, and five symmetric portrait transport actions; no header/overflow Connect duplicates.
- Compact translation screenshot: no red toolbar, no white explanation, no duplicate title, readable single attribution action; full long author + source accessible; original/translated/both, language, URL import, variants, retry and offline save still work.
- Desktop TranslationPanel callers compile with default behavior unchanged; existing Android/shared tests and release lint pass. Physical phone UI acceptance pending device.

## Baseline isolation

Before edits snapshots: build/player-ui-baseline/{NowPlayingScreen.kt,NowPlayingOverflowMenu.kt,TranslationPanel.kt,AppScaffold.kt}. Preserve all pre-existing clock/energy/source changes. Root owns isolated staging, build, commit, push. Product baseline snapshots retained for isolated review.
