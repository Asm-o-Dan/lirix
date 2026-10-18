# DSK-READY-005 — DesktopLyricsService.canonicalSourceId

Status: READY, approved helper for DSK-READY-001. One product function.

Map persisted/display provider aliases to rejection identities: LrcLib variants/LRCLIB to builtin:lrclib; AmDm / MyChords/AMDM to builtin:amdm; Web Scraper to builtin:web; rule:* and unknown provider identities unchanged. Use consistently for Room/memory/disk acceptance and rejectCurrentSource. USER remains authoritative. Blank and NONE are not positive automatic cache providers. Tests exercise the production service with existing alias rejections rather than only asserting this helper.
