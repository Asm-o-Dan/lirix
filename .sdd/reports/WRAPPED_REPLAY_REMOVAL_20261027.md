# Android Wrapped replay removal — 2026-10-27

The screenshot-requested replay button is removed from canonical WrappedMotionSummary and its dangling callback/state/import are removed in both trees. The column's existing spacedBy places space only between remaining children, so removal leaves no dedicated replay block. The reveal stays keyed by selectedTimeframe and stats presence and still snaps then animates 1500 ms; period dropdown, calculations, share/export, overall motion engine are unchanged. RU/EN both use the same button-free composable. No other replay references exist in production WrappedScreen or relevant tests. Existing string resources remain unused to avoid touching foreign-dirty shared catalogs.

## Preservation

Main WrappedScreen already had the button deleted before this task, alongside unrelated user changes to calendar period observation (and a pre-existing blank line). Those unrelated changes are preserved. Canonical was based on HEAD 076e53e; its diff consists only of removing TextButton, replay state/key/callback/parameter, and TextButton import. It preserves original livePlayback path. Stage only canonical blob.

## Scope and verification

Static diff and search only; no Gradle, UI/device, APK, or tests run. No code change to period/share paths, but runtime behavior is not certified. No tests asserted the removed button. The user requested no new tests for this small, reversible change.

| File | Main SHA256 | Canonical SHA256 |
|---|---|---|
| `app/src/main/java/com/lirix/app/ui/WrappedScreen.kt` | `484b4dfb8f9b36c9d547e057c4d3c512471d4220ac28ce0ac5066db96dffe5d4` | `5d04005a898eeacfa761fac51e45d2604143434bfb66de844d1db81e20acbab3` |


Root verification: isolated :app:compileDebugKotlin BUILD SUCCESSFUL (2026-10-27), diff reviewed by executor. No device rendering, period/export runtime test or APK produced for this small change.
