# HUD drag correction — v1, 2026-10-17

Owner: hud_drag_architect. Status: APPROVED v1 by coordinator, 2026-10-17. Native runtime acceptance remains pending.

## Evidence and diagnosis

User reports that dragging the overlay sends it offscreen. Runtime reproduction has not been observed by this architect; do not report a runtime confirmation.

`desktop/src/main/kotlin/com/lirix/app/desktop/hud/DesktopHudWindow.kt`, inside `DesktopHudWindow`, header text currently uses `detectDragGestures` and executes `window.setLocation(window.x + drag.x.toInt(), window.y + drag.y.toInt())`. Confirmed implementation defect: the callback integrates pointer displacement measured relative to a window that it itself moves. It also adds Compose pixel deltas directly to AWT window coordinates. The latter is an unverified contributor under non-default DPI, rather than a reproduced device-specific cause. Existing settings-position effects and snapshot persistence can amplify visible oscillation; they are not established as an independent cause.

The cached runtime is Compose foundation-desktop 1.7.3. `jar tf` confirms `androidx.compose.foundation.window.WindowDraggableArea_desktopKt`; `javap` confirms its WindowScope receiver, Modifier parameter and composable content. `javap -c -p` of its `DragHandler` confirms that it captures AWT window location W0 and global MouseInfo pointer P0 on start, then sets location to W0 + (Pcurrent - P0), never integrating local deltas. No dependency change is necessary.

## Architecture and frozen contract

Use the existing toolkit drag implementation through `androidx.compose.foundation.window.WindowDraggableArea`. Do not implement a second drag engine, coordinate conversion, event accumulator or screen-edge clamp. Dragging deliberately to another monitor, including negative virtual-desktop coordinates, remains supported. Existing offscreen recovery and Ctrl+Alt+R reset remain unchanged.

Only the header label is a drag target. Place `WindowDraggableArea(modifier = Modifier.weight(1f))` inside the existing Row, wrapping the existing Text with its vertical padding. Text uses `Modifier.fillMaxWidth().padding(vertical = 8.dp)`. Keep font settings, maxLines and ellipsis. Do not wrap the entire Row: A−, A+, lock and close controls must remain siblings outside the drag area. The locked header remains absent exactly as before. Resize gesture handling is untouched.

The sole product function changed is the existing `@Composable fun DesktopHudWindow(visible: Boolean, onCloseRequest: () -> Unit, playback: LivePlaybackSnapshot?, lyricsLines: List<LrcLine> = emptyList(), settings: HudSettings = HudSettings(), onSettingsChange: (HudSettings) -> Unit = {}, syncOffsetMs: Long = 0, onHelp: () -> Unit = {})`. No public signature changes. Remove only the header's custom pointerInput/detectDragGestures branch; retain those imports because resizing still uses them.

Persistence remains the current WindowState snapshotFlow → settings callback → store. A movement regression is not accepted if the overlay jumps after release, reverts when hidden/shown, or fails to restore after relaunch. If those checks fail with toolkit dragging, stop and report a separate persistence CR with evidence; do not add speculative suppression flags during this change.

## Regression cases / acceptance oracle

Native integration observations use AWT virtual-desktop user coordinates, with a tolerance of 2 units after settling; device-pixel screenshots must account for scale before comparison. These are acceptance cases, not claims that a synthetic unit test has driven an actual window.

1. W0=(200,180), P0=(250,200), P1=(350,240) gives W1=(300,220). Header remains under the grabbed pointer offset.
2. Continue through pointer positions (360,245), (360,245), (350,240): expected window positions (310,225), (310,225), (300,220). Repeated stationary events must not drift.
3. Twenty alternating ±10 horizontal displacements and return to P0 finish at W0. No accumulated error or acceleration.
4. Start W0=(-1200,100), P0=(-1150,120), move to (-1050,170): W1=(-1100,150). Negative monitor origins are valid and never clamped to zero.
5. Repeat cases 1–3 with 100%, 150%, 200% display scaling. Where a second display exists, cross its boundary and return. Same pointer-to-window offset must be preserved in native coordinates. If hardware is unavailable, mark this case NOT RUN, not PASS.
6. Release left button; subsequent unpressed pointer motion does not move HUD. Hide/lock during a drag, reopen/unlock and begin a new drag: old anchor is not reused. Closing/hiding leaves no continued movement.
7. After a completed drag wait for settings propagation, hide/show then relaunch using the same test Preferences node: location equals final location within tolerance. Existing width, height, text scale, opacity and locked state remain unchanged.
8. Click A−, A+, lock and close separately. Each action fires once and does not move the window. Resize handle still resizes; locked mode remains click-through.

Existing HudSettingsTest tests persisted coordinates but does not reproduce the pointer bug. Do not add source-text assertions or a helper that merely repeats third-party arithmetic to manufacture coverage. QA may write a real native integration test if the installed harness can observe and inject native pointer events safely; otherwise the native acceptance matrix is the required runtime gate, with unavailable cases explicit.

## Gates

DoR: causal code evidence and dependency API are available; one-function card exists. Approve minimal correction before assigning coder. Compile `:desktop:compileKotlin`, run `:desktop:test`, inspect diff. Native drag verification is a separate gate; screenshots unavailable does not equal PASS. A distributable can be produced for user validation with native gate explicitly pending, but do not call bug verified fixed until a real drag succeeds.

## Out of scope

New hotkeys, changing persisted schema, geometry migration, screen-edge snapping/clamping, resizing redesign, library merge, themes, release versioning and terminating installed application processes.
