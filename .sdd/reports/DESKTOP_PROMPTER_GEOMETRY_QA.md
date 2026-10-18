# Desktop karaoke adaptive anchor: independent review — 2026-10-18

Reviewed the root-authored geometry/helper/tests and production teleprompter diff read-only while the implementation executor owns Gradle. QA changed no product/tests and ran no build. Rendered acceptance remains pending.

Formula review: the preferred anchor remains 40% of viewport; after measurement it is capped by available space minus the actual row height and bottom margin. Pixel/dp conversion uses current density. Negative/oversized geometry is bounded; rows taller than the viewport use a zero anchor and the existing lazy-list manual scrolling remains available. The actual active row supplies onSizeChanged measurement, reset per lyrics/index, and changed top padding restarts positioning. No font-size reduction is introduced. Four supplied tests cover the pure geometry, not Compose measurement/autoscroll.

Color review: empty, active, nearby and distant text now consume the desktop-derived palette instead of hardcoded dark/light constants. Accent normalization measures actual desktop backgrounds, not the old Graphite/Parchment reference helper. Transitional hover alpha is outside opaque-text contrast claims.

One lifecycle issue reported to root: measurement state is keyed by `lines` and active index, but the positioning effect initially keys only active index and top padding. Replacing lyrics/track with the same active index and same resulting padding can preserve a prior manual scroll offset without repositioning the active line. Add `lines` to the effect keys if this rebind scenario is included in the bounded correction; do not claim it solved from the pure geometry tests.

No further geometry blocker identified in source. Actual settled long-paragraph frames, index transitions, resize, oversized manual scrolling and native behavior must still be inspected/tested separately.
