# HUD drag independent review — 2026-10-17

Verdict: **PASS for static scope and contract review**. Native acceptance remains **NOT RUN / PENDING**; this report does not establish that the user's runtime symptom is fixed.

Reviewed approved `.sdd/specs/media-ui/hud_drag_fix.md` v1, `.sdd/tasks/HUD-DRAG-001.md`, the working product diff and the complete HUD/settings implementation. No product files were edited and no Gradle task was launched by this reviewer (coder owns build session 7300).

## Scope and implementation

- Product diff contains only the `WindowDraggableArea` import and replacement of the header label's custom drag handler within `DesktopHudWindow`. Signature, persistence, geometry recovery, settings, resize handling and dependency declarations are unchanged.
- Toolkit extension receiver is valid: the call remains lexically within `Window { ... }`, which provides `WindowScope`; nested Row/Column lambdas do not remove that receiver. Independent `javap` inspection of cached foundation-desktop 1.7.3 confirms the extension accepts `WindowScope`, `Modifier` and composable content. Compilation remains the authoritative compiler gate, owned by coder.
- `Modifier.weight(1f)` remains on the direct Row child. Inner Text fills that area and preserves vertical padding, colors, font, single-line ellipsis and label. A−, A+, lock and close remain sibling controls outside the drag area. Locked mode excludes the drag header. The resize pointer handler and its imports remain intact.
- Independent dependency bytecode inspection confirms `DragHandler.onDragStarted` captures native AWT window position and global `MouseInfo` pointer position; subsequent `onDrag` sets `W0 + (Pcurrent - P0)`. It does not accumulate window-relative deltas or mix Compose pixel deltas with AWT position units. No zero clamp is introduced, so negative monitor origins remain representable.

## Persistence assessment

No new persistence defect is evident from static review. Native moves still feed the existing Compose WindowState bridge, whose snapshotFlow persists absolute position. Settings changes can still feed position back through LaunchedEffect; this feedback existed before the correction and is unchanged. It cannot be certified free of event-order races by source inspection. Verify final settled position, hide/show, lock/unlock and process relaunch against the same preferences node before calling the runtime gate PASS. A toolkit move bypassing WindowState synchronization, stale callback ordering or jump-back would require a separate evidence-backed persistence CR, as the approved spec requires.

## Remaining gates

Coder must provide compile/test results. Native matrix cases 1–3 and 6–8 remain pending: actual drag, stationary/repeated movement, release lifecycle, controls/resize and saved-position restoration. Negative-origin and 100%/150%/200% DPI / mixed-monitor cases remain NOT RUN unless matching hardware is exercised. Coordinator reports two native window capture timeouts; those are an automation limitation, never a passing native drag result. No simulated arithmetic or existing settings unit test substitutes for these observations.

No actionable static findings within the approved one-function scope.
