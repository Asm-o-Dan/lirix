# KARAOKE-001 — resolveDesktopLyricsContent

Status: APPROVED for execution, 2026-10-17. Coordinator Gate 2/4 PASS. Production paths relative to desktop/src/main/kotlin/com/lirix/app/desktop/ unless explicit test path.

Target: `ui/components/DesktopLyricsPresentation.kt`; function: `resolveDesktopLyricsContent`.

Add named DesktopLyricsMode/DesktopLyricsContent enums and the resolver with exact signature/precedence frozen in spec. This card owns only resolver plus its required enum declarations.

Contract: .sdd/specs/media-ui/desktop_karaoke_fix.md, Frozen implementation contracts. Product card owns one function and required declarations only; independent QA card KARAOKE-012 owns regression tests. DoD: exact contract implemented, no unrelated behavior changes; coordinator validation required.
