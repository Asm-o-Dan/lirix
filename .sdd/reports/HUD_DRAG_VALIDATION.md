# HUD drag validation — 2026-10-17

Implementation: hud_drag_coder, approved HUD-DRAG-001 v1. Independent review: hud_drag_reviewer, static PASS in `HUD_DRAG_REVIEW.md`.

`DesktopHudWindow` header now delegates dragging to Compose WindowDraggableArea. Product scope is one function/import; controls, resize and persistence are unchanged. Architecture evidence identifies integration of window-relative pointer deltas into the moving window location as the defect addressed; native reproduction was not observed.

## Checks

- `:desktop:compileKotlin :desktop:test --offline --no-daemon --no-configuration-cache --max-workers=2`: BUILD SUCCESSFUL in 1m 59s.
- Desktop XML: 42 tests, 0 failures, 0 errors, 0 skipped. These tests do not drive native drag events.
- Two root window-capture attempts failed: `FrameArrived timed out: timed out waiting on channel`, followed by `window capture timed out: timed out waiting on channel`. No coordinate-based input followed these failures.
- Native dragging, release/cancel, position persistence, monitor transitions and DPI cases remain NOT RUN. Do not report native PASS.

## Local artifact

`desktop/build/hud-drag-fixed-20261017/Lirix/Lirix.exe`

This is a local validation image, not a published release or installer. Copied the existing jpackage image into a new directory, then replaced both project jars with current Gradle outputs while preserving the exact filenames referenced by Lirix.cfg. SHA256 equality with each source jar was verified. The implementation executor checked unchanged dependency versions and their presence in the packaged image. The validation image has not been launched. No original running image was modified and no process was terminated.

Desktop jar: `desktop/build/libs/desktop.jar` → `app/desktop-b5b7ce2ae1d5f64bb4e9231442bbe3.jar`.
Shared jar: `shared/build/libs/shared-desktop.jar` → `app/shared-desktop-886786cacbdda6f836cdb1c8b883ef.jar`.

Before native acceptance use the fixed image; an already running image retains the old handler. Avoid overlapping copies when assessing hotkeys. User cannot currently perform a manual check; this remains pending without requesting the same check again now.
