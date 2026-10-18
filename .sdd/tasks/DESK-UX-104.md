# DESK-UX-104 — DesktopDeckScreen

Status: READY FOR COORDINATOR SELECTION; not implemented.
Spec: .sdd/specs/media-ui/desktop_library_theme_corrections.md v1.
Owner: desktop coder; one function card.
Function: `DesktopDeckScreen`.
File: `desktop/ui/DesktopDeckScreen.kt` (relative to desktop/src/main/kotlin/com/lirix/app unless New path).
Dependencies: None.

## Deterministic scope

Replace dark/light theme swap with local selectorboolean and existing ThemeSelectionDialog(current,onThemeSelected,dismiss). TopBar callback only opensdialog. Keep existing callbacks and themedata persistence. Remove dead swapenum logic, no palette changes.

## DoR

Spec accepted; dependencies integrated; current working copy checked; no unrelated changes. Supporting imports/data type declarations required by named function allowed; no second behavioural function in this card.

## DoD

Each6theme open/dismiss nochange; selectexactenum; restartpersists.

Run relevant existing checks once after integrated batch; record results and exactmanuallimitations. No broad layoutredesign, schema migration, added audio features, release publication or visualPASS without actual evidence. Commit coordination belongs to root; do not commit another worker's edits.
