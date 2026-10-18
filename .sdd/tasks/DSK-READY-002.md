# DSK-READY-002 — DesktopLyricsService.saveUserLyrics

Status: READY. One product function. Depends on frozen concurrency contract in `specs/media-ui/desktop_offline_lyrics.md`.

Cancel and join only the relevant active lookup before USER persistence; capture job identity, retain track-switch publication guard, and preserve unrelated lookup when editing a library track. Persist a successful edit regardless of later playback switch. DoD: deterministic held-lookup/held-save regressions prove final USER state and no cancellation of another track. No UI changes.
