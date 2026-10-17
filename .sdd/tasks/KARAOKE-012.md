# KARAOKE-012 — regression verification

Status: APPROVED for execution, 2026-10-17. Coordinator Gate 2/4 PASS. Production paths relative to desktop/src/main/kotlin/com/lirix/app/desktop/ unless explicit test path.

Target: `desktop/src/test/kotlin/com/lirix/app/desktop/ui/DesktopKaraokeRegressionTest.kt`; function: `regression verification`.

QA owns test work independently of product cards. Test frozen production resolver/plain/active/seek functions and cold clock progression without input events, pause/seek replacement/resume/cancellation. Offset persistence/isolation test uses existing store only if preferences isolated safely. No mirrored Main callback test or new UI dependency. Root runs compile/test/package and records native gate honestly.

Contract: .sdd/specs/media-ui/desktop_karaoke_fix.md, Frozen implementation contracts. Product card owns one function and required declarations only; independent QA card KARAOKE-012 owns regression tests. DoD: exact contract implemented, no unrelated behavior changes; coordinator validation required.
