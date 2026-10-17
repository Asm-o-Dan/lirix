# KARAOKE-006 — MainKt.main action wrapper

Status: APPROVED for execution, 2026-10-17. Coordinator Gate 2/4 PASS. Production paths relative to desktop/src/main/kotlin/com/lirix/app/desktop/ unless explicit test path.

Target: `Main.kt`; function: `MainKt.main action wrapper`.

Within this one existing main function remove Dispatchers.Main override from captured Compose coroutineScope.launch. Also collect DesktopSyncOffsetStore.offsetMs in main and pass observed value to HUD. No new dispatcher helper or dependency.

Contract: .sdd/specs/media-ui/desktop_karaoke_fix.md, Frozen implementation contracts. Product card owns one function and required declarations only; independent QA card KARAOKE-012 owns regression tests. DoD: exact contract implemented, no unrelated behavior changes; coordinator validation required.
