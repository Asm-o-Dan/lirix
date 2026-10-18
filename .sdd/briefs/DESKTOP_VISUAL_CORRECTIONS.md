# Desktop visual corrections brief — 2026-10-18

Status: confirmed artifact findings; implementation specification and executor assignment pending. Do not mark READY from this brief alone.

Evidence: `.sdd/reports/DESKTOP_STATIC_VISUAL_REVIEW.md`; real Compose PNGs under desktop/build/compose-preview-matrix.

Required outcomes:

1. At 980×680 logical pixels, the long active karaoke paragraph remains readable rather than cut by the panel bottom. Preserve actual timing, first-line behavior, offset-aware seeking and text selection modes. Recheck short and long lines in dark and light themes at minimum and default sizes; native DPI is separate.
2. Library favorite/edit/poster actions remain legible and reachable at minimum width. No single-word label split such as «Посте» / «р». Do not shrink all text or silently hide actions to satisfy an image boundary.
3. Desktop Material roles used by controls derive coherently from the selected theme, retaining all six palettes. Resolve source-audited foreground contrast for meaningful text. Verify actual button/chip/field states in dark and light previews; no blanket replacement of palette identity.
4. Preview generator must navigate to actual Wrapped and Settings pages, not name library images after those pages. Establish destination before capture, wait for bounded ready state and preserve artifacts on failure. No claims about native navigation from scene-local tests.

Current coordinator role prohibits root product/test implementation. Executors reached usage limits; root requested an explicit temporary role change while continuing tests, packaging and artifact inspection. No role change is inferred from elapsed time.
