# DSK-READY-001 — DesktopLyricsService.onTrackChanged

Status: READY pending coordinator assignment. One product function.

Implement saved Room lyrics hydration according to `.sdd/specs/media-ui/desktop_offline_lyrics.md`. Touch onTrackChanged only except minimal constructor dependency seam needed for deterministic tests; request a separate card for reusable provider mapping if necessary. Preserve cancellation, currentTrackKey guard, USER priority, and explicit refresh. No archive UI or schema changes. Definition of done: acceptance examples covered by meaningful deterministic tests and desktop/shared suite green.
