# DSK-READY-003 — DesktopLibraryRepository.saveLyrics

Status: READY. One product function. Contract `specs/media-ui/desktop_offline_lyrics.md`.

Under existing repository mutex, prevent non-USER save from replacing USER. USER edits/clears remain explicit and permitted. Both lyrics_cache and music_tracks must expose the effective persisted lyrics. DoD: real Room integration test USER → non-USER preserves USER in both tables after reopen; non-USER → USER and ordinary automatic saves remain functional. No schema change.
