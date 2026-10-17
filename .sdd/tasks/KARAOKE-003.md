# KARAOKE-003 — DesktopKaraokeUnavailable

Status: APPROVED for execution, 2026-10-17. Coordinator Gate 2/4 PASS. Production paths relative to desktop/src/main/kotlin/com/lirix/app/desktop/ unless explicit test path.

Target: `ui/components/DesktopKaraokeUnavailable.kt`; function: `DesktopKaraokeUnavailable`.

Add @Composable fun DesktopKaraokeUnavailable(content: DesktopLyricsContent, canTapSync: Boolean, onOpenTapSync: () -> Unit, onShowText: () -> Unit, modifier: Modifier = Modifier). Render loading/idle/unavailable/unsynced states; UNSYNCED exposes enabled-dependent Tap-to-Sync and always usable Text action. No source requests or invented timestamps.

Contract: .sdd/specs/media-ui/desktop_karaoke_fix.md, Frozen implementation contracts. Product card owns one function and required declarations only; independent QA card KARAOKE-012 owns regression tests. DoD: exact contract implemented, no unrelated behavior changes; coordinator validation required.
