# DESK-UX-103 — DesktopLibraryPanel

Status: READY FOR COORDINATOR SELECTION; not implemented.
Spec: .sdd/specs/media-ui/desktop_library_theme_corrections.md v1.
Owner: desktop coder; one function card.
Function: `DesktopLibraryPanel`.
File: `desktop/ui/DesktopWorkspace.kt` (relative to desktop/src/main/kotlin/com/lirix/app unless New path).
Dependencies: 101 +102; must integrate with102 before shipping.

## Deterministic scope

Render passed viewstate instead of independently selecting unfiltered picked. Four honest filters and exact emptystates/copy/reset fromspec; search labelincludeslyrics. No stale details/actions with noresults. Consume hoisted draftstate; no local remember(picked.userNotes) that discardsinput. Keep existing layout.

## DoR

Spec accepted; dependencies integrated; current working copy checked; no unrelated changes. Supporting imports/data type declarations required by named function allowed; no second behavioural function in this card.

## DoD

NoHistory vsNoMatches; resetallrestrictions; chord/offlinefalsepromisesremoved; details matchresult; playback untouched.

Run relevant existing checks once after integrated batch; record results and exactmanuallimitations. No broad layoutredesign, schema migration, added audio features, release publication or visualPASS without actual evidence. Commit coordination belongs to root; do not commit another worker's edits.
