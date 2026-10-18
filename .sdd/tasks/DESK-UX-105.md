# DESK-UX-105 — DesktopTopBar

Status: READY FOR COORDINATOR SELECTION; not implemented.
Spec: .sdd/specs/media-ui/desktop_library_theme_corrections.md v1.
Owner: desktop coder; one function card.
Function: `DesktopTopBar`.
File: `desktop/ui/components/DesktopTopBar.kt` (relative to desktop/src/main/kotlin/com/lirix/app unless New path).
Dependencies: 104.

## Deterministic scope

Theme action becomes PaletteIcon with selectedtheme contextual accessiblelabel and hovertooltip specified; invokes supplied callback only. Retain sameiconbuttonsize/position. No binary light/dark contentDescription; no other toolbar changes.

## DoR

Spec accepted; dependencies integrated; current working copy checked; no unrelated changes. Supporting imports/data type declarations required by named function allowed; no second behavioural function in this card.

## DoD

Tooltip+accessibility conveys selector/currenttheme, activation opensselector, sixchoicesavailable.

Run relevant existing checks once after integrated batch; record results and exactmanuallimitations. No broad layoutredesign, schema migration, added audio features, release publication or visualPASS without actual evidence. Commit coordination belongs to root; do not commit another worker's edits.
