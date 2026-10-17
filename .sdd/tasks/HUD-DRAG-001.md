# HUD-DRAG-001: correct `DesktopHudWindow` header dragging

Status: IN_REVIEW (native acceptance pending). Owner: hud_drag_coder. Implementation, compilation, 42 desktop tests and independent static review passed on 2026-10-17; native acceptance NOT RUN.

File: `desktop/src/main/kotlin/com/lirix/app/desktop/hud/DesktopHudWindow.kt` only.
Function: `DesktopHudWindow`, preserve exact existing signature.
Spec: `.sdd/specs/media-ui/hud_drag_fix.md`, v1, frozen contract.

1. Import `androidx.compose.foundation.window.WindowDraggableArea` (available in cached foundation-desktop 1.7.3).
2. In unlocked header Row replace the Text's manual pointerInput branch with `WindowDraggableArea(modifier = Modifier.weight(1f)) { Text(...) }`.
3. Preserve Text appearance, one-line ellipsis and 8.dp vertical padding; inner Text fills available width.
4. Keep the four header action controls outside this area, as existing sibling controls. Keep resize gesture and all settings, persistence, recovery and Window flags untouched.

Forbidden: other product functions, dependencies, custom screen-pointer engines, adding clamps, new preferences, broad formatting.

Boundary behavior: toolkit uses global pointer/window anchors; monitor negative coordinates remain valid; drag area exists only unlocked. No new errors are introduced. On integration/compiler failure return BLOCKED with exact diagnostic rather than substitute another drag engine.

Acceptance: `:desktop:compileKotlin` and `:desktop:test` succeed; diff shows only stated function and import; native cases 1–3 and 6–8 in spec pass before runtime-verified status. Cases 4–5 require appropriate display hardware; mark unavailable cases NOT RUN. Existing persistence tests are necessary but insufficient to prove dragging.
