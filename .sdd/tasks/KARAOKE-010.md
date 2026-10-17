# KARAOKE-010 — DesktopWorkspace

Status: APPROVED for execution, 2026-10-17. Coordinator Gate 2/4 PASS. Production paths relative to desktop/src/main/kotlin/com/lirix/app/desktop/ unless explicit test path.

Target: `ui/DesktopWorkspace.kt`; function: `DesktopWorkspace`.

Modify this one existing composable call site: supply onOpenTapSync = { syncTrack = currentTrack } and canTapSync = currentTrack != null to Deck. No editor or persistence changes.

Contract: .sdd/specs/media-ui/desktop_karaoke_fix.md, Frozen implementation contracts. Product card owns one function and required declarations only; independent QA card KARAOKE-012 owns regression tests. DoD: exact contract implemented, no unrelated behavior changes; coordinator validation required.
