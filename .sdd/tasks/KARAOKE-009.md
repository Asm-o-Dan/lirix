# KARAOKE-009 — DesktopDeckScreen

Status: APPROVED for execution, 2026-10-17. Coordinator Gate 2/4 PASS. Production paths relative to desktop/src/main/kotlin/com/lirix/app/desktop/ unless explicit test path.

Target: `ui/DesktopDeckScreen.kt`; function: `DesktopDeckScreen`.

Integrate frozen helpers in this one existing composable. Four named tabs default Karaoke; Text static/selectable; Karaoke timed teleprompter or explicit availability component. Add trailing onOpenTapSync/canTapSync arguments. Collect observable offset. LaunchedEffect(playback) collects production clock into transport/karaoke state; offset seek uses helper. Preserve Chords/About content and Connect callbacks.

Contract: .sdd/specs/media-ui/desktop_karaoke_fix.md, Frozen implementation contracts. Product card owns one function and required declarations only; independent QA card KARAOKE-012 owns regression tests. DoD: exact contract implemented, no unrelated behavior changes; coordinator validation required.
