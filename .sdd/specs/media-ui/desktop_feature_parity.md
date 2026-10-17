# Desktop feature parity with Android 1.4.0

Status: APPROVED by the user's request, 2026-10-17. Implementation: Codex directly; no Antigravity coding delegation.
Baseline: published v1.4.0; branch codex/desktop-feature-parity.

## Scope and acceptance

| Phone capability | Windows equivalent | Acceptance |
|---|---|---|
| Current playback, lyrics, karaoke, chords/transposition | Existing Deck, HUD, shared chord components | Preserve transport, seeking, offset and Connect |
| Library / search / favorites / notes | Persistent Room library with search/filter/detail | Survives restart; notes/favorites editable for stored and current tracks |
| Listening history | Persist actual playing intervals and sessions | Pause/seek/idle do not inflate listening time; metadata updates do not create duplicate plays |
| Wrapped / achievements | Shared analytics and gamification engines | Real desktop history, all periods, no placeholder statistics |
| Lyric editor and offline text | Persistent plain/LRC/chords edits | New playback loads saved edits; user edits beat online results |
| Tap-to-Sync | Desktop studio, keyboard/tap stamps, undo/adjust/preview/save | Shared LrcSyncEngine; use current playback clock; save/reload LRC |
| Poster export | Theme-aware PNG Save As for track and Wrapped, Square/Stories | Actual selected theme; bounded long text; no shifted covers |
| Themes | All six themes, persisted selection | Applies to Deck/HUD/dialogs and exports after restart |
| Blacklist | Shared matching, rule editor, preview/enable/remove | Excludes matching source/title/artist from recording and library flows |
| Source rejection and re-search | Persistent source exclusions, refresh | Rejected source not immediately returned; cancellation cannot publish stale track |
| Teach mode / custom sources | Windows browser/manual URL extraction + declarative rule management | Preview before attaching, save/enable/import/export rules; no fabricated search success |
| Android overlay / media notification | Existing HUD/tray/hotkeys | Platform adaptation, no Android permission dialogs on Windows |
| Android share/import | File chooser and clipboard on Windows | Plain/LRC import, PNG export, rule import/export |
| Connect audio | Preserve validated phone→PC route | No regression in PCM tests; do not claim reverse casting until implemented/verified |

## Architecture

Desktop gets a repository over existing shared Room entities/DAOs, not a second JSON history. Shared analytics, blacklist matcher, LRC and chord engines remain the source of truth. A serialized recorder counts elapsed playing intervals and flushes sessions; a UI snapshot must not create synthetic playback. DesktopLyricsService becomes repository-aware, distinguishes plain text from timed lyrics and guards stale asynchronous results. Platform file dialogs/bitmap export stay desktop-only.

Each UI exposes errors and empty/loading states. Export and database/network IO run off the UI thread. No destructive database migration is introduced. Existing Android behavior and release tag remain unchanged.

## Verification

Domain tests: pauses/resume, repeated metadata, track changes/replay, source blacklist, lyrics precedence and stale fetch cancellation. Desktop database integration covers save/reopen. Bitmap export checks dimensions/theme/text bounds. Compile both platforms, desktop/shared tests, launch desktop for visual checks. Commit all task changes; do not alter published v1.4.0.

## User refinement: overlay and keyboard, 2026-10-17
HUD must move freely, resize and scale text, retain geometry across hide/restart and offer an accessible unlock/reset. Ctrl+Alt+L toggles edit/click-through; H toggles visibility. Central catalog drives13 global registrations and visible F1 help. Registration failures must be shown. Local editing/studio shortcuts use Ctrl modifiers; no bare global text keys. Hover hints show relevant keyboard alternatives.

