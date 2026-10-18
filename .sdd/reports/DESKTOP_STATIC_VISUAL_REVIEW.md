# Static Compose visual review — 2026-10-18

These images render the actual desktop composables with production fonts and theme mapping through CPU ImageComposeScene. They are not Windows screenshots. Fixtures use temporary Room/cache and inert callbacks; no native application startup is involved. Native focus, installation, window geometry, audio, hotkeys, dialog wrappers and restart are outside this evidence.

## First proof

Artifact: `desktop/build/compose-preview-qa/obsidian_pulse-1280x820-idle.png`.

Root opened and inspected the PNG, rather than accepting its existence as design approval. The empty player renders its sidebar, transport, header and four separate tabs including Karaoke. At this logical size their visible bounds fit inside the image. No playback is represented honestly with empty-state copy. Disabled top actions are visually muted.

This single empty dark-theme frame does not validate long titles, populated lists, light-theme contrast, minimum width, notes, Wrapped loading, interactions or any native integration. No complete design PASS is claimed.

## Matrix execution and inspected states

The opt-in renderer completed successfully in 2m 17s and wrote 61 PNGs to `desktop/build/compose-preview-matrix`: one idle proof plus six themes × two logical sizes × five named states. This is an artifact count, not 61 accepted screens.

Root inspected these actual images:
- spotify_mood-980x680-library.png
- spotify_mood-1280x820-settings.png
- spotify_mood-1280x820-karaoke.png
- paper_ink-980x680-karaoke.png
- pastel_serenity-980x680-library.png
- yandex_music-980x680-no-timing.png

Confirmed visual findings:

1. **Observed clipping, settled reproduction PENDING.** In paper_ink-980x680-karaoke.png the active fixture paragraph continues below the lower edge. The previous line consumes space above; the current line is not entirely readable in this captured frame. The original harness rendered only three frames, so completion of animated auto-scroll was not established. At 1280×820 the inspected Spotify paragraph fits. Repeat with bounded animation settling before calling this a permanent product defect or changing the teleprompter.
2. **P2: library action row breaks a short label in the middle of a word.** In Spotify and Pastel 980×680 library images, «Постер» becomes «Посте» / «р». The row must adapt or move actions before labels break into unusable fragments. This confirms the earlier source-based narrow-layout concern.
3. **P2: partial Material theme mapping is visible.** Selected «Треки» uses the default lavender container in Spotify and Pastel library frames while surrounding actions use the chosen accent. Small muted explanatory labels are faint. Contrast numbers remain the separate source/token audit; these inspected pixels establish the mixed treatment, not a new computed ratio.
4. **Valid no-timing state observed.** Yandex 980×680 shows the synchronization explanation, Tap-to-Sync and Open Text actions fully in the panel. This is static content evidence, not confirmation that native clicks open the correct dialog.
5. **Renderer navigation coverage defect.** spotify_mood-1280x820-settings.png visibly has «Медиатека» selected and library content. The similarly sized settings/wrapped outputs cannot establish those pages. Correct the detached-scene navigation and verify the actual destination before accepting Wrapped/Settings visuals. This is a preview-harness defect until native evidence shows otherwise, not a demonstrated application navigation bug.

These observations are sufficient to keep design acceptance open. Native dialogs/HUD and density/monitor transitions remain outside this matrix. No edits to product code were made by the coordinator during inspection.

## Execution constraints

The QA, coder and preview executor hit account usage limits after writing and compiling the bounded UI correction and optional renderer. Root continues available artifact inspection, tests and packaging under the coordinator role. Further product changes require an available executor or an explicit role change from the user; do not silently bypass supplied AGENTS.md.
## Final correction matrix — 2026-10-18

Latest complete run: desktop 81 tests PASS; shared 173 PASS (UP-TO-DATE). Opt-in renderer produced 61 frames in desktop/build/compose-preview-final, with destination semantics assertions and bounded settling. Inspected final representatives: Spotify narrow Library, Pastel wide Wrapped, Yandex narrow Settings, Paper narrow Karaoke, Graphite narrow Karaoke, Obsidian wide untimed Karaoke. Actual Wrapped/Settings navigation is now verified in these frames. Whole Poster label wraps as an action; selected containers use the chosen palette. The complete active fixture paragraph fits the inspected Paper/Graphite narrow panels. This is detached static evidence, not native monitor/DPI/HUD acceptance.

The auto-scroll effect now includes the lines key to handle same-index track rebinding. Broader editor draft protection, database merge UI and native acceptance remain open. The newly reported live seek-clock and stale artwork defects are a separate blocking batch; no release readiness is declared.
