# Desktop parity progress — 2026-10-17

Baseline published1.4.0/9ef5bba. Branch codex/desktop-feature-parity. Product changes below were written before the latest coordinator-only instructions; further work follows `.sdd/reports/DESKTOP_ACCEPTANCE_HANDOFF.md`.

Implemented, verification in progress:
- Persistent Room desktop library and interval-based listening ledger, favorites/notes/history.
- Shared analytics and26 achievement definitions. New optional persisted-duration mode avoids estimating time across pauses on desktop; Android defaults unchanged.
- Main navigation, library/artist/filter/search/detail, Wrapped with export, settings/blacklist.
- Persistent plain/LRC/chord edits, imports/exports, desktop Tap-to-Sync with keyboard/undo/adjust.
- Native PNG export in all themes/formats, local cover mapping, preview for current track.
- Custom sources: URL inspection, DOM block selection/CSS preview/manual text, attach, declarative source rules, Android-compatible checksummed import/export.
- Lyrics service user-edit precedence, source rejection, stale/cancelled result guards; removed invented5-second karaoke timing for plain text.
- HUD movable/resizable/edit/click-through, font/opacity/position/size persistence, reset;13 global hotkeys, F1 help and hover hints.

Checks:
- Initial desktop compile passed before latest integration changes.
- Latest sequential `:desktop:test :shared:desktopTest` run passed on 2026-10-17 (3m 26s), including the corrected source line-break handling. A fresh distributable and interactive acceptance are still pending.
- Unit/integration tests added for listening pauses/seeks/replay/sleep, real Room persistence/blacklist, HUD settings, shortcut uniqueness, PNG dimensions/theme and custom source parsing/checksum.

Pending review/QA:
- Compile and all tests; launch fresh desktop, inspect main screens/help, move/resize HUD and reopen.
- Verify persisted-duration mode and Android regressions; check database migration keeps user data.
- Check hover/keyboard hints and source editor errors.
- Ensure header remains one line at minimum HUD width; help global shortcut restores full window.
- Add blacklist purge/undo with complete session backups if matching Android cleanup functionality is exposed.
- Finish honest parity matrix, commit clean worktree, provide desktop build. Published1.4.0 must remain immutable.
