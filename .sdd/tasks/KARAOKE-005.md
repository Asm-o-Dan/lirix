# KARAOKE-005 — DesktopSyncOffsetStore.setOffsetMs

Status: APPROVED for execution, 2026-10-17. Coordinator Gate 2/4 PASS. Production paths relative to desktop/src/main/kotlin/com/lirix/app/desktop/ unless explicit test path.

Target: `ui/components/DesktopTransportBar.kt`; function: `DesktopSyncOffsetStore.setOffsetMs`.

Modify this one existing setter to persist then publish backing MutableStateFlow. Add required read-only offsetMs flow initialized from existing preference/default. Retain getOffsetMs compatibility, reading current flow value. No preference key/default changes.

Contract: .sdd/specs/media-ui/desktop_karaoke_fix.md, Frozen implementation contracts. Product card owns one function and required declarations only; independent QA card KARAOKE-012 owns regression tests. DoD: exact contract implemented, no unrelated behavior changes; coordinator validation required.
