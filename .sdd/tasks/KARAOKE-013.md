# KARAOKE-013 — isolate offset preferences dependency

Status: APPROVED by coordinator, 2026-10-17.

Target: DesktopTransportBar.kt offset persistence declarations.

Function: construct DesktopSyncOffsetPreferences from injected Preferences with observable offset, getOffsetMs and setOffsetMs. Preserve production singleton node, key, default and API through delegation. Constructor must not touch global Preferences or initialize production singleton.

Contract: focused CR in .sdd/specs/media-ui/desktop_karaoke_fix.md.

QA: MemoryPreferences-backed persistence/recreation and active subscriber regression; existing transport test must also stop mutating production settings. No dependency changes.
