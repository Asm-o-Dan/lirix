# Android player UI correction from annotated screenshots

Status: IMPLEMENTED / AUTOMATED PASS; physical UI acceptance pending. Android only; published release unchanged.

User evidence: attachments 3830.jpg/3831.jpg, 2026-10-21. White annotation: remove the explanatory original/translation karaoke disclaimer from the reading surface. Green: compact source/translation attribution, avoid repeated track headings and preserve source access. Red: move editing, sync and translation/language/display controls into contextual additional actions. Move device connection to the lower transport row for symmetry.

Design read: targeted preservation of native Compose Material styling. Keep current themes, typography, playback behavior and lower tabs. Reduce competing controls above lyrics. DESIGN_VARIANCE 3 / MOTION_INTENSITY 2 / VISUAL_DENSITY 4 for this bounded correction; no new animation or UI dependency.

Required outcomes:
- Portrait transport order: Connect, Previous, Play/Pause, Next, Share; one discoverable Connect action, no topbar/overflow duplicate. Landscape retains a single reachable Connect action.
- The top-right overflow opens anchored to its trigger, stays inside viewport and dismisses on context changes. Back/outside dismiss correctly; accessible tap targets and font scaling retained.
- Actions depend on current content/screen: lyrics, karaoke, chords, notes, library. Do not expose track editing without a track or unrelated lyric actions in a library screen. Keep existing operations reachable through context-appropriate entry points.
- Translation controls moved away from the main reading surface remain usable, including language, URL import and original/translation/together. A bounded sheet is acceptable for these choices; avoid one enormous overflow list.
- Compact attribution remains readable, opens the real source, handles missing/long author/source and supports themes and large fonts. Original/translation text remains intact.

Delivery gates: audit and explicit action matrix; bounded implementation; independent review; regression verification and Android APK build. Phone visual acceptance if available, otherwise explicitly pending. Preserve all concurrent working edits; commit only this task's patches, never whole dirty files. No release/tag edits.

## Implementation checkpoint
Approved spec and independent review completed. White explanation removed from Android reading surface; compact attribution exposes complete source/author details; red controls move to contextual overflow/translation sheet. Connect/Previous/Play/Next/Share use five symmetric portrait slots, with single Connect and Share reachable in landscape. Menu anchors to its trigger and dismisses on context changes. Desktop default translation presentation preserved.

Clean-base verification: Android 231 + shared 189 unit tests pass; Android debug APK, desktop compilation and instrumentation APK compile pass. Final release lint passed. Native UI tests/visual acceptance await a connected Android device. Details: reports/ANDROID_PLAYER_UI_VERIFICATION_20261021.md.
