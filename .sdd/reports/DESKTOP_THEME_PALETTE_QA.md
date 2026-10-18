# Desktop visual palette: QA — 2026-10-18

Approved contract: `desktop_visual_corrections_v1.md`, including Gate2 opaque-alpha policy and the pinned 36-role Material3 API. QA owns `DesktopThemePaletteTest.kt`; no product edits or new dependencies.

Six tests exercise the production foreground helper and palette builder. Assertions use the existing shared WCAG contrast calculation against actual returned foreground/background pairs, rather than reproducing the lightness search. They cover unchanged passing solid colors, alpha normalization before calculation, true translucent-pixel contrast as a separate composite, invalid/unsatisfiable inputs, a small step back toward the original color failing the threshold, all six shared palettes remaining immutable, retained branded backgrounds/glow/shapes, adjusted accent hue/saturation, actual opaque 18% composited containers, Material text/inverse/error pairs and essential outline contrast. All 36 supported role getters are listed; their opaque returned values and branded mappings are checked. Explicit assignment of every role additionally requires source review.

Enabled text and karaoke targets are 4.5; essential outlines target 3. Tests do not relax these by subtracting an epsilon. Disabled state-layer alpha and translucent HUD over arbitrary external content are not claimed compliant. No Room, user preferences, cache files, network or native integration is needed.

Initial command: `:desktop:test --tests '*DesktopThemePaletteTest' --offline --no-daemon --no-configuration-cache --max-workers=2`.

RED result: expected compile failure in 27 seconds on absent production helper/builder APIs and cascading inferred types. Existing product compilation was up to date. No behavioral test body executed; this is an absent-contract compile gate, not measured contrast failure evidence. `git diff --check` passed before the build freeze. Implementation was released only after Gradle exited; six tests were frozen at that handoff. Actual frame inspection and native acceptance remain separate gates.

## Independent implementation source review

Reviewed the implementation before its build: no source blockers found against the bounded contract. Helper validates finite specified input, opaque backgrounds and threshold; normalizes alpha before measuring; searches both passing endpoints with 16 iterations, chooses the smaller lightness adjustment and checks the final real contrast. Builder copies shared tokens, derives against actual backgrounds, composites real 18% containers, and explicitly assigns all 36 pinned Material roles. Surface elevation mapping is documented and uses only original branded surfaces. Main reuses one remembered palette for main/compact/HUD; FlowRow keeps original callbacks and complete single-line button labels. The preview executor owns its builder integration and settled-frame run.

This review proves source conformance, not rendered button bounds, native-window/dialog inherited locals, arbitrary color-space behavior, user-opacity HUD contrast or accessibility of disabled controls. The tested/shared palettes use sRGB; no wider-gamut color guarantee is made.
