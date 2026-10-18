# DSK-READY-004 — DesktopLyricsService.lookupAutomatic

Status: READY, coordinator approved extraction seam. One product function.

Extract the existing built-in LrcLib/chords/web search from onTrackChanged into private suspend lookupAutomatic(rawTitle, rawArtist, cleanTitle, cleanArtist, rejected). Return DesktopLyricsState with actual plain/LRC/chords/source and no persistence, publication or cache writes. Existing search order/verification stays unchanged. onTrackChanged calls the injected automaticLookup when present, otherwise this production function, then applies the shared cancellation/persistence/publication contract. No network in deterministic tests.
