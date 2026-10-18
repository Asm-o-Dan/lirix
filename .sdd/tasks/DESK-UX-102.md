# DESK-UX-102 — DesktopWorkspace

Status: READY FOR COORDINATOR SELECTION; not implemented.
Spec: .sdd/specs/media-ui/desktop_library_theme_corrections.md v1.
Owner: desktop coder; one function card.
Function: `DesktopWorkspace`.
File: `desktop/ui/DesktopWorkspace.kt` (relative to desktop/src/main/kotlin/com/lirix/app unless New path).
Dependencies: 101; architect approval for draft lifecycle.

## Deterministic scope

Hoist ephemeral notesdrafts map keyed by trackKey above page switch. Preserve drafts through selection/pagechanges; pass draft and edits to panel, clear only when repository tracksflow confirms savedvalue; keep on failure. Switch existing panel invocation to viewstate contract and explicit resetcallbacks. Do not query cache or modify savebackend.

## DoR

Spec accepted; dependencies integrated; current working copy checked; no unrelated changes. Supporting imports/data type declarations required by named function allowed; no second behavioural function in this card.

## DoD

Draft retained selectingB/filteringAout/leaving/reentering; failedsave retains input; no navigation autosave.

Run relevant existing checks once after integrated batch; record results and exactmanuallimitations. No broad layoutredesign, schema migration, added audio features, release publication or visualPASS without actual evidence. Commit coordination belongs to root; do not commit another worker's edits.
